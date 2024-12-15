package me.synergy.modules;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.yaml.snakeyaml.Yaml;

import me.synergy.brains.Synergy;


public class LocalesManager {

	private static Map<String, HashMap<String, String>> LOCALES = new HashMap<>();
    private String localesFilePath = "plugins/Synergy/locales.yml";
    public static Map<String, Object> localesValues;

	public LocalesManager() {}

	public void initialize() {
		try {
			loadLocales();

			if (!Synergy.getConfig().getBoolean("localizations.enabled")) {
				return;
			}

			Synergy.getLogger().info(this.getClass().getSimpleName()+" module has been initialized!");
		} catch (Exception c) {
			Synergy.getLogger().error(this.getClass().getSimpleName()+" module failed to initialize:");
			c.printStackTrace();
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
	        try (InputStream defaultConfigStream = getClass().getClassLoader().getResourceAsStream("locales.yml")) {
	            if (defaultConfigStream != null) {
	                Files.copy(defaultConfigStream, Path.of(localesFilePath), StandardCopyOption.REPLACE_EXISTING);
	            }
	        }
	    }
	}
	
	private Map<String, Object> loadYamlDataFromFile() {
	    File localesFile = new File(localesFilePath);
	    if (localesFile.exists()) {
	        try (FileInputStream fis = new FileInputStream(localesFile)) {
	            Yaml yaml = new Yaml();
	            return yaml.load(fis);
	        } catch (IOException e) {
	            e.printStackTrace();
	        }
	    }
	    return null;
	}
	
	@SuppressWarnings("unchecked")
	private void initializeTranslations(Map<String, Object> yamlData) {
	    int count = 0;
	    for (String key : yamlData.keySet()) {
	        Object section = yamlData.get(key);
	        if (section instanceof Map) {
	            Map<String, Object> subSection = (Map<String, Object>) section;
	            for (String language : subSection.keySet()) {
	                Object value = subSection.get(language);
	                if (value instanceof String) {
	                    count += addTranslation(key, language, (String) value);
	                } else if (value instanceof List) {
	                    count += addTranslationsFromList(key, language, (List<String>) value);
	                }
	            }
	        }
	    }
	    Synergy.getLogger().info("There were " + count + " translations initialized!");
	}

	private int addTranslation(String key, String language, String translation) {
	    translation = translation.replace("%nl%", System.lineSeparator());
	    HashMap<String, String> translationMap = getLocales().getOrDefault(language, new HashMap<>());
	    translationMap.put(key, translation);
	    getLocales().put(language, translationMap);
	    return 1;
	}

	private int addTranslationsFromList(String key, String language, List<String> translations) {
	    StringBuilder sb = new StringBuilder();
	    for (String translation : translations) {
	        sb.append(translation).append(System.lineSeparator());
	    }
	    if (sb.length() > 0) {
	        sb.setLength(sb.length() - 1);
	    }
	    String combinedTranslations = sb.toString().replace("%nl%", System.lineSeparator());
	    HashMap<String, String> translationMap = getLocales().getOrDefault(language, new HashMap<>());
	    translationMap.put(key, combinedTranslations);
	    getLocales().put(language, translationMap);
	    return 1;
	}
	
	public Set<String> getLanguages() {
		return getLocales().keySet();
	}

	public static Map<String, HashMap<String, String>> getLocales() {
		return LOCALES;
	}
}