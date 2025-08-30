package me.synergy.utils;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.synergy.brains.Synergy;
import me.synergy.integrations.PlaceholdersAPI;
import me.synergy.modules.LocalesManager;
import me.synergy.objects.BreadMaker;

public class Translation {

    public static String getDefaultLanguage() {
    	try {
    		return Synergy.getConfig().getString("localizations.default-language");
    	} catch (Exception c) {
    		return "en";
    	}
    }

    public static String translate(String string, String language) {
    	return translate(string, language, null);
    }

	public static String translate(String string, String language, BreadMaker bread) {

		// Process <lang> tags
		try {
        	if (string.contains("<lang>")) {
        		string = Translation.processLangTags(string, language);
        	}
		} catch (Exception c) { Synergy.getLogger().error("Error while processing <lang> tags: " + c.getLocalizedMessage()); }

		// Process placeholders
		try {
			if (Synergy.isDependencyAvailable("PlaceholderAPI") && bread != null) {
				string = PlaceholdersAPI.processPlaceholders(Synergy.getSpigot().getPlayerByUniqueId(bread.getUniqueId()), string);
			}
		} catch (Exception c) { Synergy.getLogger().error("Error while processing placeholders: " + c.getLocalizedMessage()); }

		return string;
	}


	/*
	 * <lang> tags processor
	 */

    public static String processLangTags(String input, String language) {
    	try {
	        String keyPattern = "<lang>(.*?)</lang>";
	        Pattern pattern = Pattern.compile(keyPattern);
	        Matcher matcher = pattern.matcher(input);

	        StringBuffer outputBuffer = new StringBuffer();
	        boolean found = false;
	        while (matcher.find()) {
	            found = true;
	            String translationKeyWithArgs = matcher.group(1);
	            String translationKey = translationKeyWithArgs.replaceAll("<arg>(.*?)</arg>", "");
	            HashMap<String, String> locales = LocalesManager.getLocales().getOrDefault(language, new HashMap<>());
	            String defaultTranslation = LocalesManager.getLocales().getOrDefault(getDefaultLanguage(), new HashMap<>()).getOrDefault(translationKey, translationKey);
	            String translatedText = locales.getOrDefault(translationKey, defaultTranslation);
	            if (translatedText != null) {
	                String argsPattern = "<arg>(.*?)</arg>";
	                Pattern argsPatternPattern = Pattern.compile(argsPattern);
	                Matcher argsMatcher = argsPatternPattern.matcher(translationKeyWithArgs);
	                while (argsMatcher.find()) {
	                    String arg = argsMatcher.group(1);
	                    translatedText = translatedText.replaceFirst("%ARGUMENT%", arg);
	                }
	                matcher.appendReplacement(outputBuffer, translatedText);
	            }
	        }
	        matcher.appendTail(outputBuffer);

	        String result = outputBuffer.toString();
	        result = result.replace("%CLEAR%", System.lineSeparator().repeat(30));
	        
	        if (found) {
	            return processLangTags(result, language);
	        } else {
	            return result;
	        }
        } catch (Exception c) {
    		Synergy.getLogger().error(c.getLocalizedMessage());
    	}

    	return removeLangTags(input);
    }

    public static String removeLangTags(String string) {
    	return string.replaceAll("<lang>(.*?)</lang>", "");
    }

    public static String removeAllTags(String input) {
        return input.replaceAll("<[^>]*>", "");
    }

}

