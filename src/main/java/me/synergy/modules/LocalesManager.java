package me.synergy.modules;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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

public class LocalesManager {

	private static final Map<String, HashMap<String, String>> LOCALES = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> ARRAY_KEYS = new HashMap<>();
    private final String localesFilePath = "plugins/Synergy/locales.yml";
    public static Map<String, Object> localesValues;

    public void initialize() {
        try {
            loadLocales();
            if (!Synergy.getConfig().getBoolean("localizations.enabled")) return;
            Synergy.getLogger().info(getClass().getSimpleName() + " module initialized!");
        } catch (Exception e) {
            Synergy.getLogger().error(getClass().getSimpleName() + " module failed to initialize:");
            e.printStackTrace();
        }
    }

    public void loadLocales() {
        try {
            createLocalesFileIfNotExists();
            Map<String, Object> yamlData = loadYamlDataFromFile();
            if (yamlData == null) {
                Synergy.getLogger().warning("Failed to load locales file.");
                return;
            }
            initializeTranslations(yamlData);
            checkMissingTranslations();
            new Locales().initialize();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createLocalesFileIfNotExists() throws IOException {
        File file = new File(localesFilePath);
        if (!file.exists()) {
            Synergy.getLogger().info("Creating locales file...");
            file.getParentFile().mkdirs();
            file.createNewFile();
            try (InputStream stream = getClass().getClassLoader().getResourceAsStream("locales.yml")) {
                if (stream != null) {
                    Files.copy(stream, Path.of(localesFilePath), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private Map<String, Object> loadYamlDataFromFile() {
        try (FileInputStream fis = new FileInputStream(localesFilePath)) {
            return new Yaml().load(fis);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private void initializeTranslations(Map<String, Object> yamlData) {
        int count = 0;
        for (Map.Entry<String, Object> entry : yamlData.entrySet()) {
            if (!(entry.getValue() instanceof Map)) continue;
            Map<String, Object> subSection = (Map<String, Object>) entry.getValue();
            for (Map.Entry<String, Object> langEntry : subSection.entrySet()) {
                if (langEntry.getValue() instanceof String) {
                    count += addTranslationInternal(entry.getKey(), langEntry.getKey(), (String) langEntry.getValue());
                } else if (langEntry.getValue() instanceof List) {
                    count += addTranslationsFromList(entry.getKey(), langEntry.getKey(), (List<String>) langEntry.getValue());
                    ARRAY_KEYS.computeIfAbsent(langEntry.getKey(), k -> new java.util.HashSet<>()).add(entry.getKey());
                }
            }
        }
        Synergy.getLogger().info("Initialized " + count + " translations!");
    }

    private void checkMissingTranslations() {
        Set<String> allKeys = new HashSet<>();
        LOCALES.values().forEach(map -> allKeys.addAll(map.keySet()));
        
        if (allKeys.isEmpty()) return;
        
        boolean hasMissing = false;
        for (Map.Entry<String, HashMap<String, String>> entry : LOCALES.entrySet()) {
            String language = entry.getKey();
            Set<String> langKeys = entry.getValue().keySet();
            
            Set<String> missingKeys = new HashSet<>(allKeys);
            missingKeys.removeAll(langKeys);
            
            if (!missingKeys.isEmpty()) {
                hasMissing = true;
                Synergy.getLogger().warning("Language '" + language + "' is missing " + missingKeys.size() + " translation(s):");
                for (String key : missingKeys) {
                    Synergy.getLogger().warning("  - " + key);
                }
            }
        }
        
        if (!hasMissing) {
            Synergy.getLogger().info("All translations are complete for all languages!");
        }
    }

    private int addTranslationInternal(String key, String language, String translation) {
        LOCALES.computeIfAbsent(language, k -> new HashMap<>())
               .put(key, translation.replace("%nl%", System.lineSeparator()));
        return 1;
    }

    private int addTranslationsFromList(String key, String language, List<String> translations) {
        String combined = String.join(System.lineSeparator(), translations)
                                .replace("%nl%", System.lineSeparator());
        LOCALES.computeIfAbsent(language, k -> new HashMap<>()).put(key, combined);
        return 1;
    }

    public boolean addOrUpdate(String key, String language, String translation) {
        try {
            LOCALES.computeIfAbsent(language, k -> new HashMap<>()).put(key, translation);
            if (ARRAY_KEYS.containsKey(language)) {
                ARRAY_KEYS.get(language).remove(key);
            }
            saveToFile();
            return true;
        } catch (Exception e) {
            Synergy.getLogger().error("Failed to update translation: " + key);
            return false;
        }
    }

    public boolean addDefault(String key, String language, String translation) {
        if (hasTranslation(key, language)) return false;
        return addOrUpdate(key, language, translation);
    }

    public boolean addOrUpdate(String key, String language, String[] translations) {
        try {
            String combined = String.join(System.lineSeparator(), translations);
            LOCALES.computeIfAbsent(language, k -> new HashMap<>()).put(key, combined);
            ARRAY_KEYS.computeIfAbsent(language, k -> new java.util.HashSet<>()).add(key);
            saveToFile();
            return true;
        } catch (Exception e) {
            Synergy.getLogger().error("Failed to update translation: " + key);
            return false;
        }
    }

    public boolean addDefault(String key, String language, String[] translations) {
        if (hasTranslation(key, language)) return false;
        return addOrUpdate(key, language, translations);
    }

    public boolean remove(String key, String language) {
        HashMap<String, String> map = LOCALES.get(language);
        if (map == null || !map.containsKey(key)) return false;
        try {
            map.remove(key);
            if (ARRAY_KEYS.containsKey(language)) {
                ARRAY_KEYS.get(language).remove(key);
            }
            saveToFile();
            return true;
        } catch (Exception e) {
            Synergy.getLogger().error("Failed to remove translation: " + key);
            return false;
        }
    }

    public boolean hasTranslation(String key, String language) {
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

    private void saveToFile() throws IOException {
        // Use TreeMap for alphabetical order
        Map<String, Object> yamlData = new TreeMap<>();
        
        Set<String> allKeys = LOCALES.values().stream()
                .flatMap(map -> map.keySet().stream())
                .collect(java.util.stream.Collectors.toSet());

        allKeys.forEach(key -> {
            Map<String, Object> langMap = new LinkedHashMap<>();
            LOCALES.forEach((lang, map) -> {
                String translation = map.get(key);
                if (translation != null) {
                    if (ARRAY_KEYS.containsKey(lang) && ARRAY_KEYS.get(lang).contains(key)) {
                        String[] lines = translation.split(System.lineSeparator());
                        langMap.put(lang, List.of(lines));
                    } else {
                        langMap.put(lang, translation);
                    }
                }
            });
            if (!langMap.isEmpty()) yamlData.put(key, langMap);
        });

        DumperOptions options = new DumperOptions();
        options.setIndent(2);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

        try (FileWriter writer = new FileWriter(localesFilePath)) {
            new Yaml(options).dump(yamlData, writer);
        }
    }

    public void reload() {
        LOCALES.clear();
        ARRAY_KEYS.clear();
        loadLocales();
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

}