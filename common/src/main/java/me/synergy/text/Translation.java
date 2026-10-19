package me.synergy.text;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.synergy.brains.Synergy;
import me.synergy.modules.Config;
import me.synergy.modules.DataManager;
import me.synergy.modules.Locales;
import me.synergy.objects.BreadMaker;
import me.synergy.text.Gendered.Gender;

public class Translation {

    private static final Pattern LOCALE_TAG = Pattern.compile("<locale:([a-zA-Z0-9._-]+)((?:[^>\"]|\"(?:[^\"\\\\]|\\\\.)*\")*)>", Pattern.DOTALL);
    private static final Pattern ARG = Pattern.compile("([a-zA-Z0-9_-]+)\\s*=\\s*(?:\"((?:[^\"\\\\]|\\\\.)*)\"|([^\\s>]+))", Pattern.DOTALL);

    public static String getDefaultLanguage() {
        String language = Config.getString("localizations.default-language");
        return language == null ? "en" : language;
    }

    public static String translate(String string, String language) {
        return translate(string, language, null);
    }

    public static String translate(String string, String language, BreadMaker bread) {
        string = processLocaleTags(string, language);
        return bread == null ? string : Synergy.platform().placeholders(bread.getUniqueId(), string);
    }

    public static String processLocaleTags(String input, String language) {
        if (!input.contains("<locale:")) return input;
        Matcher matcher = LOCALE_TAG.matcher(input);
        StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            Map<String, String> args = parseArguments(matcher.group(2));
            String translation = Locales.get(matcher.group(1), language);
            if (translation == null) translation = args.getOrDefault("fallback", matcher.group(1));
            if (args.containsKey("line")) {
                String[] lines = translation.split("\n");
                int line = Integer.parseInt(args.get("line").trim());
                if (line >= 0 && line < lines.length) translation = lines[line];
            }
            for (var arg : args.entrySet())
                translation = Pattern.compile("%" + Pattern.quote(arg.getKey()) + "%", Pattern.CASE_INSENSITIVE).matcher(translation).replaceAll(Matcher.quoteReplacement(arg.getValue()));
            Gender gender = args.containsKey("gender") ? Gendered.getGender(args.get("gender")) : null;
            if (gender == null && args.containsKey("player")) {
                UUID uuid = DataManager.findUuidByName(args.get("player"));
                if (uuid != null) gender = Synergy.getBread(uuid).getGender();
            }
            if (gender != null) translation = Gendered.process(translation, gender);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(translation));
        }
        matcher.appendTail(buffer);
        return processLocaleTags(buffer.toString(), language);
    }

    private static Map<String, String> parseArguments(String input) {
        Map<String, String> args = new HashMap<>();
        Matcher matcher = ARG.matcher(input);
        while (matcher.find()) args.put(matcher.group(1), matcher.group(2) != null ? matcher.group(2).replaceAll("\\\\(.)", "$1") : matcher.group(3));
        return args;
    }

    public static String removeLocaleTags(String string) {
        return string.replaceAll("<locale:[^>]*>", "");
    }

    public static String removeAllTags(String input) {
        return input.replaceAll("(?<!\\\\)<[^>]*>", "").replace("\\<", "<");
    }
}
