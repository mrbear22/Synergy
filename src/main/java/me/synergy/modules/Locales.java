package me.synergy.modules;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.DumperOptions;

import me.synergy.brains.Synergy;

public class Locales {

    private static final Map<String, HashMap<String, String>> LOCALES = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> ARRAY_KEYS = new HashMap<>();

    private static final Map<String, String> KEY_SOURCE_FILE = new ConcurrentHashMap<>();

    private static final String LOCALES_DIR = "plugins/Synergy/locales";

    public void initialize() {
        try {
            loadLocales();
            if (!Config.getBoolean("localizations.enabled")) return;
            Synergy.getLogger().info(getClass().getSimpleName() + " module initialized!");
        } catch (Exception e) {
            Synergy.getLogger().error(getClass().getSimpleName() + " module failed to initialize:");
            e.printStackTrace();
        }
    }

    public void loadLocales() {
        try {
            File localesDir = new File(LOCALES_DIR);

            if (localesDir.mkdirs()) {
            	copyResourceIfAbsent("locales/example.yml", new File(localesDir, "example.yml"));
            }

            File[] ymlFiles = localesDir.listFiles(
                    (dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));

            if (ymlFiles == null || ymlFiles.length == 0) {
                Synergy.getLogger().warning("No locale files found in " + LOCALES_DIR);
                return;
            }

            int totalCount = 0;
            for (File file : ymlFiles) {
                Map<String, Object> yamlData = loadYamlDataFromFile(file);
                if (yamlData == null) {
                    Synergy.getLogger().warning("Failed to load locale file: " + file.getName());
                    continue;
                }
                int count = initializeTranslations(yamlData, file.getName());
                Synergy.getLogger().info("  Loaded " + count + " translations from " + file.getName());
                totalCount += count;
            }

            Synergy.getLogger().info("Initialized " + totalCount + " translations total!");
            checkMissingTranslations();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void copyResourceIfAbsent(String resourcePath, File target) throws IOException {
        if (target.exists()) return;
        Synergy.getLogger().info("Creating " + target.getName() + " from resources...");
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream != null) {
                Files.copy(stream, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                Synergy.getLogger().warning("Resource not found: " + resourcePath);
            }
        }
    }

    private Map<String, Object> loadYamlDataFromFile(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            return new Yaml().load(fis);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private int initializeTranslations(Map<String, Object> yamlData, String sourceFile) {
        int count = 0;
        for (Map.Entry<String, Object> entry : yamlData.entrySet()) {
            if (!(entry.getValue() instanceof Map)) continue;
            Map<String, Object> subSection = (Map<String, Object>) entry.getValue();
            for (Map.Entry<String, Object> langEntry : subSection.entrySet()) {
                if (langEntry.getValue() instanceof String) {
                    count += addTranslationInternal(entry.getKey(), langEntry.getKey(),
                            (String) langEntry.getValue(), sourceFile);
                } else if (langEntry.getValue() instanceof List) {
                    count += addTranslationsFromList(entry.getKey(), langEntry.getKey(),
                            (List<String>) langEntry.getValue(), sourceFile);
                    ARRAY_KEYS.computeIfAbsent(langEntry.getKey(), k -> new HashSet<>())
                              .add(entry.getKey());
                }
            }
        }
        return count;
    }

    private int addTranslationInternal(String key, String language, String translation,
                                       String sourceFile) {
        LOCALES.computeIfAbsent(language, k -> new HashMap<>())
               .put(key, translation.replace("%nl%", System.lineSeparator()));
        KEY_SOURCE_FILE.putIfAbsent(key, sourceFile);
        return 1;
    }

    private int addTranslationsFromList(String key, String language, List<String> translations,
                                        String sourceFile) {
        String combined = String.join(System.lineSeparator(), translations)
                                .replace("%nl%", System.lineSeparator());
        LOCALES.computeIfAbsent(language, k -> new HashMap<>()).put(key, combined);
        KEY_SOURCE_FILE.putIfAbsent(key, sourceFile);
        return 1;
    }

    public static boolean addOrUpdate(String key, String language, String translation,
                                      String targetFile) {
        try {
            LOCALES.computeIfAbsent(language, k -> new HashMap<>()).put(key, translation);
            if (ARRAY_KEYS.containsKey(language)) ARRAY_KEYS.get(language).remove(key);
            String file = resolveTargetFile(key, targetFile);
            KEY_SOURCE_FILE.put(key, file);
            saveFile(file);
            return true;
        } catch (Exception e) {
            Synergy.getLogger().error("Failed to update translation: " + key);
            return false;
        }
    }

    public static boolean addOrUpdate(String key, String language, String translation) {
        return addOrUpdate(key, language, translation, null);
    }

    public static boolean addDefault(String key, String language, String translation) {
        if (hasTranslation(key, language)) return false;
        return addOrUpdate(key, language, translation);
    }

    public static boolean addDefault(String key, String language, String translation,
                                     String targetFile) {
        if (hasTranslation(key, language)) return false;
        return addOrUpdate(key, language, translation, targetFile);
    }

    public static boolean addOrUpdate(String key, String language, String[] translations,
                                      String targetFile) {
        try {
            String combined = String.join(System.lineSeparator(), translations);
            LOCALES.computeIfAbsent(language, k -> new HashMap<>()).put(key, combined);
            ARRAY_KEYS.computeIfAbsent(language, k -> new HashSet<>()).add(key);
            String file = resolveTargetFile(key, targetFile);
            KEY_SOURCE_FILE.put(key, file);
            saveFile(file);
            return true;
        } catch (Exception e) {
            Synergy.getLogger().error("Failed to update translation: " + key);
            return false;
        }
    }

    public static boolean addOrUpdate(String key, String language, String[] translations) {
        return addOrUpdate(key, language, translations, null);
    }

    public static boolean addDefault(String key, String language, String[] translations) {
        if (hasTranslation(key, language)) return false;
        return addOrUpdate(key, language, translations);
    }

    public static boolean addDefault(String key, String language, String[] translations,
                                     String targetFile) {
        if (hasTranslation(key, language)) return false;
        return addOrUpdate(key, language, translations, targetFile);
    }

    public boolean remove(String key, String language) {
        HashMap<String, String> map = LOCALES.get(language);
        if (map == null || !map.containsKey(key)) return false;
        try {
            map.remove(key);
            if (ARRAY_KEYS.containsKey(language)) ARRAY_KEYS.get(language).remove(key);

            String sourceFile = KEY_SOURCE_FILE.get(key);

            boolean anyLeft = LOCALES.values().stream().anyMatch(m -> m.containsKey(key));
            if (!anyLeft) KEY_SOURCE_FILE.remove(key);

            if (sourceFile != null) saveFile(sourceFile);
            return true;
        } catch (Exception e) {
            Synergy.getLogger().error("Failed to remove translation: " + key);
            return false;
        }
    }

    public static boolean hasTranslation(String key, String language) {
        return LOCALES.getOrDefault(language, new HashMap<>()).containsKey(key);
    }

    public boolean hasTranslation(String key) {
        return LOCALES.values().stream().anyMatch(map -> map.containsKey(key));
    }

    public String get(String key, String language) {
        return LOCALES.getOrDefault(language, new HashMap<>()).get(key);
    }

    public Map<String, String> getAllForKey(String key) {
        Map<String, String> result = new HashMap<>();
        LOCALES.forEach((lang, map) -> {
            String translation = map.get(key);
            if (translation != null) result.put(lang, translation);
        });
        return result;
    }

    public Set<String> getLanguages() {
        return LOCALES.keySet();
    }

    public static Map<String, HashMap<String, String>> getLocales() {
        return LOCALES;
    }

    public Map<String, Set<String>> getArrayKeys() {
        return ARRAY_KEYS;
    }

    public String getSourceFile(String key) {
        return KEY_SOURCE_FILE.get(key);
    }

    private static void saveFile(String fileName) throws IOException {
        Map<String, Object> yamlData = new TreeMap<>();

        KEY_SOURCE_FILE.forEach((key, file) -> {
            if (!file.equals(fileName)) return;

            Map<String, Object> langMap = new LinkedHashMap<>();
            LOCALES.forEach((lang, map) -> {
                String translation = map.get(key);
                if (translation == null) return;
                if (ARRAY_KEYS.containsKey(lang) && ARRAY_KEYS.get(lang).contains(key)) {
                    langMap.put(lang, List.of(translation.split(System.lineSeparator())));
                } else {
                    langMap.put(lang, translation);
                }
            });
            if (!langMap.isEmpty()) yamlData.put(key, langMap);
        });

        DumperOptions options = new DumperOptions();
        options.setIndent(2);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

        File outFile = new File(LOCALES_DIR, fileName);
        outFile.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(outFile)) {
            new Yaml(options).dump(yamlData, writer);
        }
    }

    private static String resolveTargetFile(String key, String targetFile) {
        if (targetFile != null && !targetFile.isBlank()) return targetFile;
        return KEY_SOURCE_FILE.getOrDefault(key, "synergy.yml");
    }

    private void checkMissingTranslations() {
        Set<String> allKeys = new HashSet<>();
        LOCALES.values().forEach(map -> allKeys.addAll(map.keySet()));
        if (allKeys.isEmpty()) return;

        boolean hasMissing = false;
        for (Map.Entry<String, HashMap<String, String>> entry : LOCALES.entrySet()) {
            String language = entry.getKey();
            Set<String> missingKeys = new HashSet<>(allKeys);
            missingKeys.removeAll(entry.getValue().keySet());
            if (!missingKeys.isEmpty()) {
                hasMissing = true;
                Synergy.getLogger().warning("Language '" + language + "' is missing "
                        + missingKeys.size() + " translation(s):");
                missingKeys.forEach(k -> Synergy.getLogger().warning("  - " + k));
            }
        }
        if (!hasMissing) {
            Synergy.getLogger().info("All translations are complete for all languages!");
        }
    }

    public void reload() {
        LOCALES.clear();
        ARRAY_KEYS.clear();
        KEY_SOURCE_FILE.clear();
        loadLocales();
    }
}