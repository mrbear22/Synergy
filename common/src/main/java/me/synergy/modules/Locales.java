package me.synergy.modules;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import me.synergy.brains.Synergy;
import me.synergy.text.Translation;

public class Locales implements Module {

    private static final File DIR = new File(Synergy.getDataFolder(), "locales");
    private static final String MAIN = "synergy.yml";
    private static final Map<String, Map<String, Map<String, Object>>> files = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, String>> texts = new ConcurrentHashMap<>();
    private static final Map<String, Object> defaults = new ConcurrentHashMap<>();

    @Override
    public void defaults() {
        Locales.addDefault("command.usage", "<primary>/%command% <secondary>— %description%");
        Locales.addDefault("player.permission.missing", "<danger>You don't have permission to do this.");
        Locales.addDefault("command.console.denied", "<danger>Only players can do this.");
        Locales.addDefault("player.name.unknown", "<danger>Player <primary>%name% <danger>not found.");
        Locales.addDefault("input.number.invalid", "<danger>Invalid number: <primary>%input%");
        Locales.addDefault("hover.click.open", "Click to open");
        Locales.addDefault("hover.click.select", "Click to select");
        Locales.addDefault("account.link.done", "<success>%service% account linked!");
        Locales.addDefault("account.unlink.done", "<success>%service% account unlinked.");
        Locales.addDefault("account.link.missing", "<danger>No linked %service% account. Link it: <secondary><u>%command%</u>");
        Locales.addDefault("account.link.exists", "<danger>%service% account is already linked. Unlink it: <secondary><u>%command%</u>");
        Locales.addDefault("player.server.join", "<secondary>[<success>+<secondary>] <primary>%player% <secondary>joined the server");
        Locales.addDefault("player.server.quit", "<secondary>[<danger>-<secondary>] <primary>%player% <secondary>left the server");
        Config.addDefault("localizations.enabled", true);
        Config.addDefault("localizations.default-language", "en");
        Config.addDefault("localizations.color-themes.default.primary", "<#1abc9c>");
        Config.addDefault("localizations.color-themes.default.secondary", "<#3498db>");
        Config.addDefault("localizations.color-themes.default.success", "<#2ecc71>");
        Config.addDefault("localizations.color-themes.default.danger", "<#e74c3c>");
        Config.addDefault("localizations.genders", true);
        Config.addDefault("localizations.minimessage", true);
    }

    @Override
    public void start() throws IOException {
        files.clear();
        texts.clear();
        DIR.mkdirs();
        File[] found = DIR.listFiles((dir, name) -> name.endsWith(".yml"));
        for (File file : found == null ? new File[0] : found) {
            try (InputStream in = new FileInputStream(file)) {
                files.put(file.getName(), read(in));
            }
        }
        files.values().forEach(file -> file.forEach((key, langs) -> langs.forEach((lang, value) -> index(key, lang, value))));
    }

    @Override
    public void refresh() throws IOException {
        start();
    }

    public static void addDefault(String key, String... lines) {
        defaults.put(key, lines.length == 1 ? lines[0] : List.of(lines));
    }

    public static void save() {
        Map<String, Map<String, Object>> main = files.computeIfAbsent(MAIN, f -> new TreeMap<>());
        boolean changed = false;
        for (var entry : defaults.entrySet()) {
            if (raw(entry.getKey(), "en") != null) continue;
            main.computeIfAbsent(entry.getKey(), k -> new TreeMap<>()).put("en", entry.getValue());
            index(entry.getKey(), "en", entry.getValue());
            changed = true;
        }
        try {
            if (changed) save(MAIN);
        } catch (IOException e) {
            Synergy.getLogger().warning("Locales: failed to save " + MAIN + ": " + e.getMessage());
        }
        Set<String> keys = new TreeSet<>();
        texts.values().forEach(map -> keys.addAll(map.keySet()));
        texts.forEach((lang, map) -> {
            List<String> missing = keys.stream().filter(key -> !map.containsKey(key)).toList();
            if (!missing.isEmpty()) Synergy.getLogger().warning("Language '" + lang + "' misses " + missing.size() + " key(s): " + String.join(", ", missing));
        });
    }

    public static String get(String key, String language) {
        for (String lang : new String[] {language, Translation.getDefaultLanguage(), "en"}) {
            String text = texts.getOrDefault(lang, Map.of()).get(key);
            if (text != null) return text;
        }
        return defaults.containsKey(key) ? text(defaults.get(key)) : null;
    }

    public static boolean has(String key, String language) {
        return texts.getOrDefault(language, Map.of()).containsKey(key);
    }

    public static Set<String> languages() {
        return texts.keySet();
    }

    public static Object raw(String key, String language) {
        return files.values().stream().map(file -> file.getOrDefault(key, Map.of()).get(language)).filter(v -> v != null).findFirst().orElse(null);
    }

    public static Set<String> keys(String language) {
        return texts.getOrDefault(language, Map.of()).keySet();
    }

    public static void put(String key, String language, Object value) throws IOException {
        String name = files.entrySet().stream().filter(e -> e.getValue().containsKey(key)).map(Map.Entry::getKey).findFirst().orElse(MAIN);
        files.computeIfAbsent(name, f -> new TreeMap<>()).computeIfAbsent(key, k -> new TreeMap<>()).put(language, value);
        index(key, language, value);
        save(name);
    }

    private static void index(String key, String language, Object value) {
        texts.computeIfAbsent(language, l -> new ConcurrentHashMap<>()).put(key, text(value));
    }

    private static String text(Object value) {
        return (value instanceof List<?> list ? String.join("\n", list.stream().map(String::valueOf).toList()) : String.valueOf(value)).replace("%nl%", "\n");
    }

    private static synchronized void save(String name) throws IOException {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setWidth(Integer.MAX_VALUE);
        try (FileWriter writer = new FileWriter(new File(DIR, name))) {
            new Yaml(options).dump(new TreeMap<>(files.get(name)), writer);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> read(InputStream in) {
        Map<String, Map<String, Object>> result = new TreeMap<>();
        Object data = in == null ? null : new Yaml().load(in);
        if (data instanceof Map<?, ?> map) map.forEach((key, langs) -> {
            if (langs instanceof Map<?, ?> values) result.put(String.valueOf(key), new TreeMap<>((Map<String, Object>) values));
        });
        return result;
    }
}
