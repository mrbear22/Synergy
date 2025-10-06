package me.synergy.text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.synergy.brains.Synergy;
import net.md_5.bungee.api.ChatColor;

public class Color {

    private static final String HEX_COLOR_PATTERN = "#[0-9a-fA-F]{6}";
	
    public static String processThemeTags(String string, String theme) {
        for (String t : new String[]{theme, "default"}) {
            try {
                var section = Synergy.getConfig().getConfigurationSection("localizations.color-themes." + t);
                if (section != null) {
                    for (var entry : section.entrySet()) {
                        String hexCode = Synergy.getConfig().getString("localizations.color-themes." + t + "." + entry.getKey()).substring(1, 8);
                        string = string.replace("<" + entry.getKey() +">", "<"+hexCode+">");
                        string = string.replace("</" + entry.getKey() + ">","</"+hexCode+">");
                    }
                }
            } catch (Exception e) {
                Synergy.getLogger().error("Error processing theme tags: " + e.getLocalizedMessage());
            }
        }
        return string;
    }
	 
    public static String processColorReplace(String string, String theme) {
        try {
            var section = Synergy.getConfig().getConfigurationSection("localizations.color-replace");
            if (section != null) {
                for (var entry : section.entrySet()) {
                    String hexCode = processThemeTags(Synergy.getConfig().getString("localizations.color-replace." + entry.getKey()), theme).substring(1, 8);
                    string = string.replace("<" + entry.getKey() +">", "<"+hexCode+">");
                    string = string.replace("</" + entry.getKey() + ">","</"+hexCode+">");
                }
            }
        } catch (Exception e) {
            Synergy.getLogger().error("Error processing color replace: " + e.getLocalizedMessage());
        }
        return string;
    }
    
    public static String processCustomColorCodes(String string) {
        try {
            var section = Synergy.getConfig().getConfigurationSection("chat-manager.custom-color-tags");
            if (section != null) {
                for (var entry : section.entrySet()) {
                	string = string.replace(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
        } catch (Exception e) {
            Synergy.getLogger().error("Error processing custom color codes: " + e.getLocalizedMessage());
        }
        return string;
    }
    
    public static String processLegacyColors(String text, String theme) {
        text = processCustomColorCodes(text);
        text = processLegacyColorCodes(text);
        text = processColorReplace(text, theme);
        text = processThemeTags(text, theme);
        text = processHexColors(text);
        text = processFormattingTags(text);
        
        text = text
            .replace("<black>", "&0")
            .replace("<dark_blue>", "&1")
            .replace("<dark_green>", "&2")
            .replace("<dark_aqua>", "&3")
            .replace("<dark_red>", "&4")
            .replace("<dark_purple>", "&5")
            .replace("<gold>", "&6")
            .replace("<gray>", "&7")
            .replace("<dark_gray>", "&8")
            .replace("<blue>", "&9")
            .replace("<green>", "&a")
            .replace("<aqua>", "&b")
            .replace("<red>", "&c")
            .replace("<light_purple>", "&d")
            .replace("<yellow>", "&e")
            .replace("<white>", "&f")
            .replace("<obfuscated>", "&k")
            .replace("<bold>", "&l")
            .replace("<strikethrough>", "&m")
            .replace("<underlined>", "&n")
            .replace("<italic>", "&o")
            .replace("<reset>", "&r");
        
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private static String processFormattingTags(String text) {
        return text
            .replace("<bold>", ChatColor.BOLD.toString())
            .replace("</bold>", ChatColor.RESET.toString())
            .replace("<italic>", ChatColor.ITALIC.toString())
            .replace("</italic>", ChatColor.RESET.toString())
            .replace("<underlined>", ChatColor.UNDERLINE.toString())
            .replace("</underlined>", ChatColor.RESET.toString())
            .replace("<strikethrough>", ChatColor.STRIKETHROUGH.toString())
            .replace("</strikethrough>", ChatColor.RESET.toString())
            .replace("<obfuscated>", ChatColor.MAGIC.toString())
            .replace("</obfuscated>", ChatColor.RESET.toString())
            .replace("<reset>", ChatColor.RESET.toString());
    }

    public static String removeColor(String json) {
        json = processThemeTags(json, getDefaultTheme());
        json = processColorReplace(json, getDefaultTheme());
        json = processLegacyColorCodes(json);
        json = removeTags(json);
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', json));
    }

    private static String processHexColors(String text) {
        Pattern pattern = Pattern.compile("<(" + HEX_COLOR_PATTERN + ")>");
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            text = text.replace(matcher.group(), ChatColor.of(matcher.group(1)).toString());
        }
        return text;
    }

    public static String processLegacyColorCodes(String input) {
        return input
            // Colors
            .replaceAll("[&§]0", "<black>")
            .replaceAll("[&§]1", "<dark_blue>")
            .replaceAll("[&§]2", "<dark_green>")
            .replaceAll("[&§]3", "<dark_aqua>")
            .replaceAll("[&§]4", "<dark_red>")
            .replaceAll("[&§]5", "<dark_purple>")
            .replaceAll("[&§]6", "<gold>")
            .replaceAll("[&§]7", "<gray>")
            .replaceAll("[&§]8", "<dark_gray>")
            .replaceAll("[&§]9", "<blue>")
            .replaceAll("[&§]a", "<green>")
            .replaceAll("[&§]b", "<aqua>")
            .replaceAll("[&§]c", "<red>")
            .replaceAll("[&§]d", "<light_purple>")
            .replaceAll("[&§]e", "<yellow>")
            .replaceAll("[&§]f", "<white>")
            // Formatting
            .replaceAll("[&§]k", "<obfuscated>")
            .replaceAll("[&§]l", "<bold>")
            .replaceAll("[&§]m", "<strikethrough>")
            .replaceAll("[&§]n", "<underlined>")
            .replaceAll("[&§]o", "<italic>")
            .replaceAll("[&§]r", "<reset>");
    }
    
    public static String removeTags(String text) {
        text = text.replaceAll("<(?:#[0-9a-fA-F]{6}|[a-zA-Z_]+)>", "");
        text = text.replaceAll("</(?:#[0-9a-fA-F]{6}|[a-zA-Z_]+)>", "");
        return text;
    }

    public static String getDefaultTheme() {
        return "default";
    }

}
