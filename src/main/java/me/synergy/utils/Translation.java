package me.synergy.utils;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

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
        		if (JsonUtils.isValidJson(string)) {
        			string = JsonUtils.jsonToCustomString(string);
        		}
        		string = Endings.processEndings(string, null);
        		string = Translation.processLangTags(string, language);
        	}
		} catch (Exception c) { Synergy.getLogger().error("Error while processing <lang> tags: " + c.getLocalizedMessage()); }

		// Process placeholders
		try {
			if (Synergy.isDependencyAvailable("PlaceholderAPI") && bread != null) {
				string = PlaceholdersAPI.processPlaceholders(Synergy.getSpigot().getPlayerByUniqueId(bread.getUniqueId()), string);
			}
		} catch (Exception c) { Synergy.getLogger().error("Error while processing placeholders: " + c.getLocalizedMessage()); }


		// Process <translation> tags
		try {
        	if (string.contains("<translation>")) {
        		if (JsonUtils.isValidJson(string)) {
        			string = JsonUtils.jsonToCustomString(string);
        		}
        		string = Endings.processEndings(string, null);
        		string = Translation.processTranslationTags(string, language);
        	}
		} catch (Exception c) { Synergy.getLogger().error("Error while processing <translation> tags: " + c.getLocalizedMessage()); }


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


	/*
	 * <translation> tags processor
	 */

    public static String processTranslationTags(String input, String languageCode) {
        Document doc = Jsoup.parse(input);
        StringBuilder result = new StringBuilder();
        processNode(doc.body(), languageCode, result);
        return result.toString().trim().replace("\\", "");
    }

    private static void processNode(Node node, String languageCode, StringBuilder result) {
        if (node instanceof TextNode) {
            result.append(((TextNode) node).text());
        } else if (node instanceof Element) {
            Element element = (Element) node;
            String tagName = element.tagName();
            if (tagName.equals("translation")) {
                Elements translations = element.children();
                for (Element translation : translations) {
                    if (translation.tagName().equals(languageCode)) {
                        processNode(translation, languageCode, result);
                        break;
                    }
                }
            } else {
            	if (!tagName.equals("body") && !tagName.equals(languageCode)) {
            		result.append("<").append(tagName).append(">");
            	}
                for (Node child : element.childNodes()) {
                    processNode(child, languageCode, result);
                }
            }
        }
    }

    public static String removeTranslationTags(String string) {
    	return string.replaceAll("<translation>(.*?)</translation>", "");
    }

    public static String removeAllTags(String input) {
        return input.replaceAll("<[^>]*>", "");
    }

}

