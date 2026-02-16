package me.synergy.text;

import java.util.Map;
import java.util.regex.Pattern;

import me.synergy.brains.Synergy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public class Color {

    private static final Pattern TAG_PATTERN = Pattern.compile("</?((?:#[0-9a-fA-F]{6}|[a-zA-Z_]+))>");
    
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();
    private static final PlainTextComponentSerializer PLAIN_SERIALIZER = PlainTextComponentSerializer.plainText();
    
    private static final Map<String, String> LEGACY_TO_TAG = Map.ofEntries(
        Map.entry("&0", "<black>"), Map.entry("&1", "<dark_blue>"), Map.entry("&2", "<dark_green>"),
        Map.entry("&3", "<dark_aqua>"), Map.entry("&4", "<dark_red>"), Map.entry("&5", "<dark_purple>"),
        Map.entry("&6", "<gold>"), Map.entry("&7", "<gray>"), Map.entry("&8", "<dark_gray>"),
        Map.entry("&9", "<blue>"), Map.entry("&a", "<green>"), Map.entry("&b", "<aqua>"),
        Map.entry("&c", "<red>"), Map.entry("&d", "<light_purple>"), Map.entry("&e", "<yellow>"),
        Map.entry("&f", "<white>"), Map.entry("&k", "<obfuscated>"), Map.entry("&l", "<bold>"),
        Map.entry("&m", "<strikethrough>"), Map.entry("&n", "<underlined>"), 
        Map.entry("&o", "<italic>"), Map.entry("&r", "<reset>")
    );
    
	public static String process(String string, String theme) {
		string = Color.processLegacyColorCodes(string);
		string = Color.processThemeTags(string, theme);
		string = Color.processColorReplace(string, theme);
		string = Color.processCustomColorCodes(string);
		return string;
	}
    
	public static String componentToMiniMessage(Component component) {
	    Component compacted = component.compact();
	    String serialized = MiniMessage.miniMessage().serialize(compacted);
	    return (serialized.contains("<locale:") 
	            ? MiniMessage.miniMessage().serialize(removeInteractive(compacted)) 
	            : serialized)
	            .replaceAll("\\\\<", "<").replaceAll("\\\\>", ">");
	}

	private static Component removeInteractive(Component c) {
	    return c.clickEvent(null).hoverEvent(null).insertion(null)
	            .children(c.children().stream().map(Color::removeInteractive).toList());
	}

    public static String processThemeTags(String string, String theme) {
        for (String t : new String[]{theme, "default"}) {
            var section = Synergy.getConfig().getConfigurationSection("localizations.color-themes." + t);
            if (section != null) {
                for (var entry : section.entrySet()) {
                    String value = Synergy.getConfig().getString("localizations.color-themes." + t + "." + entry.getKey());
                    string = string.replace("<" + entry.getKey() + ">", value)
                                   .replace("</" + entry.getKey() + ">", value.replace("<", "</"));
                }
            }
        }
        return string;
    }
    
    public static String processColorReplace(String string, String theme) {
        var section = Synergy.getConfig().getConfigurationSection("localizations.color-replace");
        if (section != null) {
            for (var entry : section.entrySet()) {
                String value = processThemeTags(
                    Synergy.getConfig().getString("localizations.color-replace." + entry.getKey()), theme
                );
                string = string.replace("<" + entry.getKey() + ">", value)
                               .replace("</" + entry.getKey() + ">", value.replace("<", "</"));
            }
        }
        return string;
    }
    
    public static String processCustomColorCodes(String string) {
        var section = Synergy.getConfig().getConfigurationSection("chat-manager.custom-color-tags");
        if (section != null) {
            for (var entry : section.entrySet()) {
                string = string.replace(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        return string;
    }
    

    public static String removeColor(String text) {
        text = processLegacyColorCodes(text);
        text = processThemeTags(text, "default");
        text = processColorReplace(text, "default");
        text = removeTags(text);
        return PLAIN_SERIALIZER.serialize(LEGACY_SERIALIZER.deserialize(text));
    }

    public static String processLegacyColorCodes(String input) {
        input = input.replace('§', '&');
        for (var entry : LEGACY_TO_TAG.entrySet()) {
            input = input.replace(entry.getKey(), entry.getValue());
        }
        return input;
    }
    
    public static String removeTags(String text) {
        return TAG_PATTERN.matcher(text).replaceAll("");
    }
    
    public static String getDefaultTheme() {
        return "default";
    }

}