package me.synergy.modules;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import me.synergy.brains.Synergy;

public final class Config {

    private static final File MAIN = new File(Synergy.getDataFolder(), "config.yml");
    private static final Set<String> OWN_FILE = Set.of("discord", "chat-manager", "localizations", "twitch", "tiktok", "monobank");
    private static final Map<String, Object> values = new LinkedHashMap<>();
    private static final Map<String, Object> defaults = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile boolean writing = true;

    private Config() {}

    @SuppressWarnings("unchecked")
    public static void start() {
        values.clear();
        Map<String, Object> main = MAIN.exists() ? read(MAIN) : null;
        if (main != null) main.forEach((key, value) -> {
            if (value instanceof Map) values.put(key, new LinkedHashMap<>((Map<String, Object>) value));
        });
        OWN_FILE.forEach(name -> load(name, file(name)));
    }

    private static void load(String name, File file) {
        if (!file.exists()) return;
        Map<String, Object> section = read(file);
        values.put(name, section != null ? section : new LinkedHashMap<>());
    }

    private static File file(String name) {
        return new File(Synergy.getDataFolder(), name + ".yml");
    }

    public static void save() {
        Map<String, Object> main = new TreeMap<>();
        values.forEach((name, section) -> {
            if (OWN_FILE.contains(name)) write(file(name), section);
            else main.put(name, section);
        });
        write(MAIN, main);
    }

    private static void write(File file, Object data) {
        file.getParentFile().mkdirs();
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        try (Writer out = new FileWriter(file, StandardCharsets.UTF_8)) {
            new Yaml(options).dump(data, out);
        } catch (IOException e) {
            Synergy.getLogger().warning("Config: failed to save " + file.getName() + ": " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> read(File file) {
        try {
            return (Map<String, Object>) new Yaml().load(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (IOException | ClassCastException e) {
            Synergy.getLogger().warning("Config: failed to read " + file.getName() + ": " + e.getMessage());
            return null;
        }
    }

    public static Object get(String key) {
        Object value = value(key);
        return value != null ? value : defaults.get(key) instanceof String[] list ? Arrays.asList(list) : defaults.get(key);
    }

    @SuppressWarnings("unchecked")
    private static Object value(String key) {
        Object current = values;
        for (String k : key.split("\\.")) {
            if (!(current instanceof Map)) return null;
            current = ((Map<String, Object>) current).get(k);
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    public static void set(String key, Object value) {
        String[] keys = key.split("\\.");
        Map<String, Object> current = values;
        for (int i = 0; i < keys.length - 1; i++) {
            if (!(current.get(keys[i]) instanceof Map)) current.put(keys[i], new LinkedHashMap<>());
            current = (Map<String, Object>) current.get(keys[i]);
        }
        current.put(keys[keys.length - 1], value instanceof Map ? new LinkedHashMap<>((Map<String, Object>) value) : value);
    }

    @SuppressWarnings("unchecked")
    public static void remove(String key) {
        int dot = key.lastIndexOf('.');
        Object parent = dot < 0 ? values : get(key.substring(0, dot));
        if (parent instanceof Map<?, ?> map) ((Map<String, Object>) map).remove(key.substring(dot + 1));
    }

    public static boolean isFilled(String key) {
        String value = getString(key);
        return value != null && !value.isBlank() && !value.equals(String.valueOf(defaults.get(key))) && !value.matches("0+");
    }

    public static void defaults(boolean write, Runnable defaults) {
        writing = write;
        try {
            defaults.run();
        } finally {
            writing = true;
        }
    }

    private static void define(String key, Object value) {
        defaults.put(key, value);
        if (writing && value(key) == null) set(key, value instanceof String[] list ? Arrays.asList(list) : value);
    }

    public static List<String> addDefault(String key, String[] value) {
        define(key, value);
        return getStringList(key);
    }

    public static String addDefault(String key, String value) {
        define(key, value);
        return getString(key);
    }

    public static double addDefault(String key, double value) {
        define(key, value);
        return getDouble(key);
    }

    public static boolean addDefault(String key, boolean value) {
        define(key, value);
        return getBoolean(key);
    }

    public static int addDefault(String key, int value) {
        define(key, value);
        return getInt(key);
    }

    public static Boolean getBoolean(String key, boolean defaultIfNull) {
        return get(key) instanceof Boolean b ? b : defaultIfNull;
    }

    public static Boolean getBoolean(String key) {
        return getBoolean(key, false);
    }

    public static Integer getInt(String key, int defaultIfNull) {
        Integer value = getInt(key);
        return value != null ? value : defaultIfNull;
    }

    public static Integer getInt(String key) {
        return get(key) instanceof Number n ? n.intValue() : null;
    }

    public static String getString(String key) {
        Object value = get(key);
        return value != null ? value.toString() : null;
    }

    public static String getString(String key, String defaultIfNull) {
        String value = getString(key);
        return value != null ? value : defaultIfNull;
    }

    public static Double getDouble(String key, Double defaultIfNull) {
        Double value = getDouble(key);
        return value != null ? value : defaultIfNull;
    }

    public static Double getDouble(String key) {
        return get(key) instanceof Number n ? n.doubleValue() : null;
    }

    @SuppressWarnings("unchecked")
    public static List<String> getStringList(String key) {
        return get(key) instanceof List<?> list ? (List<String>) list : null;
    }

    public static boolean isSet(String key) {
        return get(key) != null;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> getConfigurationSection(String path) {
        Object section = path == null || path.isEmpty() ? values : get(path);
        return section instanceof Map ? (Map<String, Object>) section : new LinkedHashMap<>();
    }

    public static String secret(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append(chars.charAt(random.nextInt(chars.length())));
        return sb.toString();
    }
}
