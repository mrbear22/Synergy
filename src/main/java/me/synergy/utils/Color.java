package me.synergy.utils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.*;

import me.synergy.brains.Synergy;
import net.md_5.bungee.api.ChatColor;

public class Color {

    private static final String HEX_COLOR_PATTERN = "#[0-9a-fA-F]{6}";
    private static final String TAG_PATTERN = "<(?:" + HEX_COLOR_PATTERN + "|[a-zA-Z_]+)>";
    
    private static final ThreadLocal<FormattingState> currentState = ThreadLocal.withInitial(FormattingState::new);
    
    private static final Map<String, String> FORMATTING_TAGS = Map.of(
        "bold", "§l",
        "italic", "§o", 
        "underlined", "§n",
        "strikethrough", "§m",
        "obfuscated", "§k",
        "reset", "§r"
    );

    private static final Set<String> STANDARD_COLORS = Set.of(
        "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", 
        "dark_purple", "gold", "gray", "dark_gray", "blue", "green", 
        "aqua", "red", "light_purple", "yellow", "white"
    );
    
    private static final Map<String, String> LEGACY_COLOR_CODES = Map.ofEntries(
        Map.entry("black", "§0"),
        Map.entry("dark_blue", "§1"),
        Map.entry("dark_green", "§2"),
        Map.entry("dark_aqua", "§3"),
        Map.entry("dark_red", "§4"),
        Map.entry("dark_purple", "§5"),
        Map.entry("gold", "§6"),
        Map.entry("gray", "§7"),
        Map.entry("dark_gray", "§8"),
        Map.entry("blue", "§9"),
        Map.entry("green", "§a"),
        Map.entry("aqua", "§b"),
        Map.entry("red", "§c"),
        Map.entry("light_purple", "§d"),
        Map.entry("yellow", "§e"),
        Map.entry("white", "§f")
    );

    public static String processColors(String json, String theme) {
        try {
            json = ThemeProcessor.processThemeTags(json, theme);
            json = CustomTagProcessor.processCustomColorCodes(json);
            json = ColorReplacer.processColorReplace(json, theme);
            json = processColorTags(JsonUtils.isValidJson(json) ? json : JsonUtils.convertToJson(json), theme); // Pass theme here
            json = processLegacyFormattingTags(json);
            json = ChatColor.translateAlternateColorCodes('&', json);
            
        } catch (Exception e) {
            Synergy.getLogger().error("Error processing colors: " + e.getLocalizedMessage());
        }
        return json;
    }

    public static String processLegacyColors(String text, String theme) {
        text = ThemeProcessor.processThemeTags(text, theme);
        text = CustomTagProcessor.processCustomColorCodes(text);
        text = ColorReplacer.processColorCodesReplace(text, theme);
        text = processHexColors(text);
        text = processLegacyFormattingTags(text);
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static String removeColor(String json) {
        json = ThemeProcessor.processThemeTags(json, "default");
        json = ColorReplacer.processColorReplace(json, "default");
        json = removeTags(json, FORMATTING_TAGS.keySet());
        json = removeTags(json, Collections.singleton(HEX_COLOR_PATTERN));
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', json));
    }

    private static String processColorTags(String json, String theme) {
        try {
            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();

            boolean needsProcessing = false;

            if (jsonObject.has("text") && !jsonObject.get("text").getAsString().isEmpty()) {
                if (containsCustomTags(jsonObject.get("text").getAsString())) {
                    needsProcessing = true;
                }
            }
            
            if (jsonObject.has("extra") && jsonObject.get("extra").isJsonArray()) {
                JsonArray extraArray = jsonObject.getAsJsonArray("extra");
                for (JsonElement element : extraArray) {
                    if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                        if (containsCustomTags(element.getAsString())) {
                            needsProcessing = true;
                            break;
                        }
                    } else if (element.isJsonObject() && element.getAsJsonObject().has("text")) {
                        if (containsCustomTags(element.getAsJsonObject().get("text").getAsString())) {
                            needsProcessing = true;
                            break;
                        }
                    }
                }
            }
            
            if (!needsProcessing) {
                return json;
            }
            
            JsonArray processedExtra = new JsonArray();

            FormattingState state = new FormattingState(theme);
            currentState.set(state);
            
            if (jsonObject.has("text")) {
                String rootText = jsonObject.get("text").getAsString();
                if (!rootText.isEmpty()) {
                    JsonObject textObj = new JsonObject();
                    textObj.addProperty("text", rootText);
                    PropertyCopier.copyNonTextProperties(jsonObject, textObj);
                    processTextElement(textObj, processedExtra);
                }
            }
            
            if (jsonObject.has("extra") && jsonObject.get("extra").isJsonArray()) {
                JsonArray extraArray = jsonObject.getAsJsonArray("extra");
                for (JsonElement element : extraArray) {
                    processExtraElement(element, processedExtra);
                }
            }
            
            JsonObject result = new JsonObject();
            result.addProperty("text", "");
            result.add("extra", processedExtra);
            PropertyCopier.copyNonTextProperties(jsonObject, result);
            
            return result.toString();
            
        } catch (Exception e) {
            Synergy.getLogger().error("Error processing color tags: " + e.getLocalizedMessage());
            return removeTagsSimple(json);
        }
    }

    private static void processTextElement(JsonObject object, JsonArray output) {
        String text = object.get("text").getAsString();
        
        if (!containsCustomTags(text)) {
            JsonObject newObj = new JsonObject();
            newObj.addProperty("text", text);
            PropertyCopier.copyNonTextProperties(object, newObj);
            output.add(newObj);
            return;
        }
        
        List<TextSegment> segments = parseTextSegments(text);
        
        for (TextSegment segment : segments) {
            if (segment.isTag()) {
                currentState.get().processTag(segment.getContent());
            } else if (!segment.getContent().isEmpty()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("text", segment.getContent());
                currentState.get().applyTo(obj);
                PropertyCopier.copyNonTextProperties(object, obj);
                output.add(obj);
            }
        }
    }

    private static List<TextSegment> parseTextSegments(String text) {
        List<TextSegment> segments = new ArrayList<>();
        Pattern tagPattern = Pattern.compile(TAG_PATTERN);
        Matcher matcher = tagPattern.matcher(text);
        
        int lastEnd = 0;
        
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                String textBefore = text.substring(lastEnd, matcher.start());
                segments.add(new TextSegment(textBefore, false));
            }

            String tag = matcher.group();
            segments.add(new TextSegment(tag, true));
            
            lastEnd = matcher.end();
        }
        
        if (lastEnd < text.length()) {
            String remainingText = text.substring(lastEnd);
            segments.add(new TextSegment(remainingText, false));
        }
        
        return segments;
    }

    private static void processExtraElement(JsonElement element, JsonArray output) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("text", element.getAsString());
            processTextElement(obj, output);
        } else if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("text") && obj.get("text").isJsonPrimitive()) {
                processTextElement(obj, output);
            } else {
                output.add(element);
            }
        } else {
            output.add(element);
        }
    }

    private static boolean containsCustomTags(String text) {
        return text.contains("<#") || text.contains("<reset>") || 
               FORMATTING_TAGS.keySet().stream().anyMatch(tag -> text.contains("<" + tag + ">")) ||
               STANDARD_COLORS.stream().anyMatch(color -> text.contains("<" + color + ">"));
    }

    private static String processHexColors(String text) {
        Pattern pattern = Pattern.compile("<(" + HEX_COLOR_PATTERN + ")>");
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            text = text.replace(matcher.group(), ChatColor.of(matcher.group(1)).toString());
        }
        return text;
    }

    private static String processLegacyFormattingTags(String input) {
        for (Map.Entry<String, String> tag : FORMATTING_TAGS.entrySet()) {
            input = input.replace("<" + tag.getKey() + ">", tag.getValue());
        }
        return input;
    }

    private static String removeTags(String input, Collection<String> tags) {
        for (String tag : tags) {
            input = input.replaceAll("<" + tag + ">", "");
        }
        return input;
    }

    private static String removeTagsSimple(String text) {
        text = text.replaceAll(TAG_PATTERN, "");
        return text;
    }

    private static class TextSegment {
        private final String content;
        private final boolean isTag;

        public TextSegment(String content, boolean isTag) {
            this.content = content;
            this.isTag = isTag;
        }

        public String getContent() { return content; }
        public boolean isTag() { return isTag; }
    }

    private static class FormattingState {
        private String currentColor;
        private final Map<String, Boolean> formatting = new HashMap<>();
        private final String theme;

        public FormattingState() {
            this("default");
        }
        
        public FormattingState(String theme) {
            this.theme = theme != null ? theme : "default";
        }

        public void reset() {
            currentColor = null;
            formatting.clear();
        }

        public void processTag(String tag) {
            String tagContent = tag.substring(1, tag.length() - 1);

            if (tagContent.startsWith("#")) {
                currentColor = tagContent;
                formatting.clear();
            } else if (STANDARD_COLORS.contains(tagContent)) {
                currentColor = tagContent;
                formatting.clear();
            } else if (FORMATTING_TAGS.containsKey(tagContent)) {
                if ("reset".equals(tagContent)) {
                    reset();
                } else {
                    formatting.put(tagContent, true);
                }
            } else {
                if (isValidCustomColorTag(tagContent)) {
                    currentColor = tagContent;
                    formatting.clear();
                }
            }
        }
        
        private boolean isValidCustomColorTag(String tagContent) {
            for (String themeToCheck : new String[]{theme, "default"}) {
                try {
                    Map<String, Object> section = Synergy.getConfig().getConfigurationSection("localizations.color-themes." + themeToCheck);
                    if (section != null && !section.isEmpty() && section.containsKey(tagContent)) {
                        return true;
                    }
                } catch (Exception ignored) {}
            }
            try {
                Map<String, Object> section = Synergy.getConfig().getConfigurationSection("localizations.color-replace");
                if (section != null && !section.isEmpty() && section.containsKey(tagContent)) {
                    return true;
                }
            } catch (Exception ignored) {}
            
            return false;
        }

        public void applyTo(JsonObject obj) {
            if (currentColor != null) {
                obj.addProperty("color", currentColor);
            }
            for (Map.Entry<String, Boolean> entry : formatting.entrySet()) {
                if (entry.getValue()) {
                    obj.addProperty(entry.getKey(), true);
                }
            }
        }
    }

    private static class PropertyCopier {
        private static final Set<String> EXCLUDED_PROPERTIES = Set.of(
            "text", "color", "bold", "italic", "underlined", 
            "strikethrough", "obfuscated", "extra"
        );

        public static void copyNonTextProperties(JsonObject source, JsonObject target) {
            for (String key : source.keySet()) {
                if (!EXCLUDED_PROPERTIES.contains(key)) {
                    target.add(key, source.get(key));
                }
            }
        }
    }
    
    private static class ThemeProcessor {
        public static String processThemeTags(String input, String theme) {
            for (String t : new String[]{theme, "default"}) {
                try {
                    var section = Synergy.getConfig().getConfigurationSection("localizations.color-themes." + t);
                    if (section != null) {
                        for (var entry : section.entrySet()) {
                            String hexCode = Synergy.getConfig().getString("localizations.color-themes." + t + "." + entry.getKey());
                            input = input.replace("<" + entry.getKey() + ">", hexCode);
                        }
                    }
                } catch (Exception e) {
                    Synergy.getLogger().error("Error processing theme tags: " + e.getLocalizedMessage());
                }
            }
            return input;
        }
    }

    private static class CustomTagProcessor {
        public static String processCustomColorCodes(String sentence) {
            try {
                var section = Synergy.getConfig().getConfigurationSection("chat-manager.custom-color-tags");
                if (section != null) {
                    for (var entry : section.entrySet()) {
                        sentence = sentence.replace(entry.getKey(), String.valueOf(entry.getValue()));
                    }
                }
            } catch (Exception e) {
                Synergy.getLogger().error("Error processing custom color codes: " + e.getLocalizedMessage());
            }
            return sentence;
        }
    }

    private static class ColorReplacer {
        public static String processColorReplace(String input, String theme) {
            try {
                var section = Synergy.getConfig().getConfigurationSection("localizations.color-replace");
                if (section != null) {
                    for (var entry : section.entrySet()) {
                        String hexCode = ThemeProcessor.processThemeTags(
                            Synergy.getConfig().getString("localizations.color-replace." + entry.getKey()), theme);
                        input = input.replace("\"" + entry.getKey() + "\"", "\"" + hexCode.substring(1, 8) + "\"");
                        input = input.replace("<" + entry.getKey() + ">", hexCode);
                    }
                }
            } catch (Exception e) {
                Synergy.getLogger().error("Error processing color replace: " + e.getLocalizedMessage());
            }
            return input;
        }

        public static String processColorCodesReplace(String input, String theme) {
            try {
                var section = Synergy.getConfig().getConfigurationSection("localizations.color-replace");
                if (section != null) {
                    for (var entry : section.entrySet()) {
                        String hexCode = ThemeProcessor.processThemeTags(
                            Synergy.getConfig().getString("localizations.color-replace." + entry.getKey()), theme);
                        if (hexCode.startsWith("<#") && hexCode.endsWith(">")) {
                            String minecraftCode = LEGACY_COLOR_CODES.get(entry.getKey().toLowerCase());
                            if (minecraftCode != null) {
                                input = input.replace(minecraftCode, hexCode);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Synergy.getLogger().error("Error processing color codes replace: " + e.getLocalizedMessage());
            }
            return input;
        }
    }

    public static String getDefaultTheme() {
        return "default";
    }

}