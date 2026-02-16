package me.synergy.text;

import java.util.Map;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.synergy.brains.Synergy;
import me.synergy.integrations.PlaceholdersAPI;
import me.synergy.modules.Locales;
import me.synergy.objects.BreadMaker;
import me.synergy.text.Gendered.Gender;

public class Translation {
    
    private static final Pattern LOCALE_TAG = Pattern.compile("<locale:([a-zA-Z0-9._-]+)((?:[^>\"]|\"[^\"]*\")*)>", Pattern.DOTALL);
    private static final Pattern ARG = Pattern.compile("([a-zA-Z0-9_-]+)\\s*=\\s*(?:\"([^\"]*)\"|([^\\s>]+))", Pattern.DOTALL);
    private static final Pattern LANG_TAG = Pattern.compile("<lang>(.*?)</lang>");
    private static final Pattern LANG_ARG = Pattern.compile("<arg>(.*?)</arg>");
    
    public static String getDefaultLanguage() {
        try { return Synergy.getConfig().getString("localizations.default-language"); } 
        catch (Exception e) { return "en"; }
    }
    
    public static String translate(String string, String language) {
        return translate(string, language, null);
    }
    
    public static String translate(String string, String language, BreadMaker bread) {
        if (string.contains("<locale:")) {
            string = processLocaleTags(string, language);
        }
        if (string.contains("<lang>")) {
            Synergy.getLogger().warning("Warning: <lang> tags are deprecated. Use <locale:key> instead. ("+string+")");
            string = processLangTags(string, language);
        }
        
        if (Synergy.isDependencyAvailable("PlaceholderAPI") && bread != null)
            string = PlaceholdersAPI.processPlaceholders(
                Synergy.getSpigot().getPlayerByUniqueId(bread.getUniqueId()), string);
        
        return string;
    }
    
    public static String processLocaleTags(String input, String language) {
        Matcher matcher = LOCALE_TAG.matcher(input);
        if (!matcher.find()) return input;
        
        matcher.reset();
        StringBuffer buffer = new StringBuffer();
        
        while (matcher.find()) {
            String key = matcher.group(1);
            Map<String, String> args = parseArguments(matcher.group(2));
            
            String translation = getTranslation(key, language, args.get("fallback"));
            
            for (Map.Entry<String, String> e : args.entrySet()) {
                if (!e.getKey().equals("fallback")) {
                    translation = translation.replace("%" + e.getKey() + "%", e.getValue());
                }
            }
            
            Gender gender = args.containsKey("gender") ? Gendered.getGender(args.get("gender")) : null;
            if (gender == null && args.containsKey("player")) {
                try {
                    BreadMaker bread = Synergy.getBread(Synergy.getOfflineUniqueId(args.get("player")));
                    if (bread != null) gender = bread.getGender();
                } catch (Exception e) {}
            }
            
            if (gender != null) translation = Gendered.process(translation, gender);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(translation));
        }
        
        matcher.appendTail(buffer);
        return processLocaleTags(buffer.toString(), language);
    }
    
    @Deprecated
    public static String processLangTags(String input, String language) {
        Matcher matcher = LANG_TAG.matcher(input);
        if (!matcher.find()) return input;
        
        matcher.reset();
        StringBuffer buffer = new StringBuffer();
        
        while (matcher.find()) {
            String content = matcher.group(1);
            String key = content.replaceAll("<arg>(.*?)</arg>", "");
            String translation = getTranslation(key, language, null);
            
            Matcher argsMatcher = LANG_ARG.matcher(content);
            while (argsMatcher.find())
                translation = translation.replaceFirst("%ARGUMENT%", argsMatcher.group(1));
            
            matcher.appendReplacement(buffer, translation);
        }
        
        matcher.appendTail(buffer);
        return processLangTags(buffer.toString(), language);
    }
    
    private static String getTranslation(String key, String language, String fallback) {
        Map<String, String> locales = Locales.getLocales().getOrDefault(language, new HashMap<>());
        
        if (locales.containsKey(key)) {
            return locales.get(key);
        }
        
        Map<String, String> defaultLocales = Locales.getLocales()
            .getOrDefault(getDefaultLanguage(), new HashMap<>());
        if (defaultLocales.containsKey(key)) {
            return defaultLocales.get(key);
        }
        
        if (fallback != null && !fallback.isEmpty()) {
            return fallback;
        }
        
        return key;
    }

    private static Map<String, String> parseArguments(String argsString) {
        Map<String, String> args = new HashMap<>();
        if (argsString == null || argsString.trim().isEmpty()) return args;
        
        Matcher matcher = ARG.matcher(argsString);
        while (matcher.find()) {
            String value = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
            if (value != null) {
                args.put(matcher.group(1), value);
            }
        }
        return args;
    }
    
    public static String removeLocaleTags(String string) {
        return string.replaceAll("<locale:[^>]*>", "");
    }
    
    @Deprecated
    public static String removeLangTags(String string) {
        return string.replaceAll("<lang>(.*?)</lang>", "");
    }
    
    public static String removeAllTags(String input) {
        return input.replaceAll("<[^>]*>", "");
    }
}