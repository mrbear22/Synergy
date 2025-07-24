package me.synergy.utils;

import java.util.*;

import com.google.gson.*;

import me.synergy.brains.Synergy;

public class JsonUtils {
    
    private static final Set<String> STANDARD_COLORS = Set.of(
        "black", "dark_blue", "dark_green", "dark_aqua", "dark_red",
        "dark_purple", "gold", "gray", "dark_gray", "blue", "green",
        "aqua", "red", "light_purple", "yellow", "white"
    );

    public static String jsonToCustomString(String json) {
        try {
            if (!isValidJson(json)) {
                return json;
            }
            
            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
            FormattingContext context = new FormattingContext();
            StringBuilder result = new StringBuilder();

            processElement(jsonObject, context, result);
            
            return result.toString();
            
        } catch (Exception e) {
            Synergy.getLogger().error("Error converting JSON to custom string: " + e.getLocalizedMessage());
            return json;
        }
    }

    private static void processElement(JsonObject element, FormattingContext context, StringBuilder result) {
        FormattingContext elementContext = new FormattingContext(context);
        
        // Check if this element has interactive components
        boolean hasInteractive = element.has("clickEvent") || element.has("hoverEvent");
        
        if (hasInteractive) {
            result.append("<interactive>");
        }
        
        processColorChange(element, elementContext, result);
        processFormattingChanges(element, elementContext, result);
        
        if (element.has("text")) {
            String text = element.get("text").getAsString();
            result.append(preserveWhitespace(text));
        }
        
        if (element.has("extra") && element.get("extra").isJsonArray()) {
            JsonArray extraArray = element.getAsJsonArray("extra");
            for (JsonElement extraElement : extraArray) {
                processExtraElement(extraElement, elementContext, result);
            }
        }
        
        if (hasInteractive) {
            processInteractiveElements(element, result);
            result.append("</interactive>");
        }
        
        context.updateFrom(elementContext);
    }

    private static void processExtraElement(JsonElement element, FormattingContext context, StringBuilder result) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String text = element.getAsString();
            result.append(preserveWhitespace(text));
        } else if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            FormattingContext elementContext = new FormattingContext(context);
            
            // Check if this extra element has interactive components
            boolean hasInteractive = obj.has("clickEvent") || obj.has("hoverEvent");
            
            if (hasInteractive) {
                result.append("<interactive>");
            }
            
            processColorChange(obj, elementContext, result);
            processFormattingChanges(obj, elementContext, result);
            
            if (obj.has("text")) {
                String text = obj.get("text").getAsString();
                result.append(preserveWhitespace(text));
            }
            
            if (obj.has("extra") && obj.get("extra").isJsonArray()) {
                JsonArray extraArray = obj.getAsJsonArray("extra");
                for (JsonElement extraElement : extraArray) {
                    processExtraElement(extraElement, elementContext, result);
                }
            }
            
            if (hasInteractive) {
                processInteractiveElements(obj, result);
                result.append("</interactive>");
            }
            
            context.updateFrom(elementContext);
        }
    }

    private static String preserveWhitespace(String text) {
        if (text == null) {
            return "";
        }
        
        text = text.replace("\\n", "\n");
        text = text.replace("\\t", "\t");
        text = text.replace("\\r", "\r");

        return text;
    }

    private static void processColorChange(JsonObject element, FormattingContext context, StringBuilder content) {
        if (element.has("color")) {
            String newColor = element.get("color").getAsString();
            if (!newColor.equals(context.getCurrentColor())) {
                content.append("<reset>");
                
                if (newColor.startsWith("#")) {
                    content.append("<").append(newColor).append(">");
                } else if (STANDARD_COLORS.contains(newColor)) {
                    content.append("<").append(newColor).append(">");
                }
                
                context.setColor(newColor);
                context.clearFormatting();
            }
        }
    }

    private static void processFormattingChanges(JsonObject element, FormattingContext context, StringBuilder content) {
        String[] formattingOptions = {"bold", "italic", "underlined", "strikethrough", "obfuscated"};
        
        for (String formatting : formattingOptions) {
            if (element.has(formatting) && element.get(formatting).getAsBoolean()) {
                if (!context.hasFormatting(formatting)) {
                    content.append("<").append(formatting).append(">");
                    context.setFormatting(formatting, true);
                }
            }
        }
    }

    private static void processInteractiveElements(JsonObject element, StringBuilder content) {
        if (element.has("hoverEvent")) {
            JsonObject hoverEvent = element.getAsJsonObject("hoverEvent");
            if (hoverEvent.has("contents")) {
                content.append("<hover>");
                JsonElement hoverValue = hoverEvent.get("contents");
                if (hoverValue.isJsonPrimitive()) {
                    content.append(preserveWhitespace(hoverValue.getAsString()));
                } else if (hoverValue.isJsonObject()) {
                    content.append(jsonToCustomString(hoverValue.toString()));
                }
                content.append("</hover>");
            } else if (hoverEvent.has("value")) {
                content.append("<hover>");
                JsonElement hoverValue = hoverEvent.get("value");
                if (hoverValue.isJsonPrimitive()) {
                    content.append(preserveWhitespace(hoverValue.getAsString()));
                } else if (hoverValue.isJsonObject()) {
                    content.append(jsonToCustomString(hoverValue.toString()));
                }
                content.append("</hover>");
            }
        }
        
        if (element.has("clickEvent")) {
            JsonObject clickEvent = element.getAsJsonObject("clickEvent");
            String action = clickEvent.get("action").getAsString();
            String value = clickEvent.get("value").getAsString();
            
            switch (action) {
                case "run_command":
                    content.append("<command>").append(preserveWhitespace(value)).append("</command>");
                    break;
                case "suggest_command":
                    content.append("<suggest>").append(preserveWhitespace(value)).append("</suggest>");
                    break;
                case "open_url":
                    content.append("<url>").append(preserveWhitespace(value)).append("</url>");
                    break;
                case "copy_to_clipboard":
                    content.append("<copy>").append(preserveWhitespace(value)).append("</copy>");
                    break;
            }
        }
    }

    public static String extractPlainText(String json) {
        try {
            if (!isValidJson(json)) {
                return json;
            }
            
            JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
            StringBuilder result = new StringBuilder();
            
            extractTextFromElement(jsonObject, result);
            
            return result.toString();
            
        } catch (Exception e) {
            Synergy.getLogger().error("Error extracting plain text: " + e.getLocalizedMessage());
            return json;
        }
    }

    private static void extractTextFromElement(JsonObject element, StringBuilder result) {
        if (element.has("text")) {
            result.append(preserveWhitespace(element.get("text").getAsString()));
        }
        if (element.has("extra") && element.get("extra").isJsonArray()) {
            JsonArray extraArray = element.getAsJsonArray("extra");
            for (JsonElement extraElement : extraArray) {
                if (extraElement.isJsonObject()) {
                    extractTextFromElement(extraElement.getAsJsonObject(), result);
                } else if (extraElement.isJsonPrimitive()) {
                    result.append(preserveWhitespace(extraElement.getAsString()));
                }
            }
        }
    }

    public static boolean isValidJson(String input) {
        try {
            JsonElement jsonElement = JsonParser.parseString(input);
            return jsonElement.isJsonObject() || jsonElement.isJsonArray();
        } catch (JsonSyntaxException e) {
            return false;
        }
    }

    public static String convertToJson(String input) {
        input = replaceFirstAndLastQuotes(input);
        
        input = input.replace("\\n", "\n")
                     .replace("\\t", "\t")
                     .replace("\\r", "\r");
        
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("text", input);
        
        return jsonObject.toString();
    }
    
    public static String replaceFirstAndLastQuotes(String input) {
        if (input == null || input.isEmpty() || input.length() < 2) {
            return input;
        }
        if (input.charAt(0) == '"') {
            input = input.substring(1);
        }
        int lastIndex = input.length() - 1;
        if (input.charAt(lastIndex) == '"') {
            input = input.substring(0, lastIndex);
        }
        return input;
    }
    
    private static class FormattingContext {
        private String currentColor;
        private final Map<String, Boolean> formatting = new HashMap<>();

        public FormattingContext() {}

        public FormattingContext(FormattingContext parent) {
            this.currentColor = parent.currentColor;
            this.formatting.putAll(parent.formatting);
        }

        public String getCurrentColor() {
            return currentColor;
        }

        public void setColor(String color) {
            this.currentColor = color;
        }

        public boolean hasFormatting(String type) {
            return formatting.getOrDefault(type, false);
        }

        public void setFormatting(String type, boolean value) {
            formatting.put(type, value);
        }

        public void clearFormatting() {
            formatting.clear();
        }

        public void updateFrom(FormattingContext other) {
            this.currentColor = other.currentColor;
            this.formatting.clear();
            this.formatting.putAll(other.formatting);
        }
    }
}