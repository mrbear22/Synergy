package me.synergy.objects;

import me.synergy.modules.Config;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.synergy.brains.Synergy;
import me.synergy.text.Color;
import me.synergy.text.Interactive;
import me.synergy.text.Translation;
import me.synergy.text.Gendered;
import me.synergy.text.Gendered.Gender;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class Locale {
	
	String string;
	String language;
	Gender gender;
	private boolean cancelled = false;
	private int delay = 0;
	private BreadMaker bread;

	public Locale(String string, String language) {
		this.language = language;
		this.string = string;
		processSpecialTags();
	}
	
	public Locale(Component component, String language) {
	    this.language = language;
	    this.string = Color.componentToMiniMessage(component);
	    processSpecialTags();
	}

	public boolean isCancelled() {
		return cancelled;
	}

	public int getDelay() {
		return delay;
	}

	public boolean hasDelay() {
		return delay > 0;
	}

	public String getString() {
		return string;
	}
	
	public Locale setGendered(Gender gender) {
		this.gender = gender;
		return this;
	}

	public Locale setExecuteInteractive(BreadMaker bread) {
		this.bread = bread;
		return this;
	}

	public Locale setPlaceholders(BreadMaker bread) {
		string = replacePlaceholderOutputs(bread.getUniqueId(), string);
		string = Synergy.platform().placeholders(bread.getUniqueId(), string);
		return this;
	}

	public Component getColoredComponent(String theme) {
		string = Translation.translate(string, language);
		string = string.replace("<clear_chat>", System.lineSeparator().repeat(30));
		string = bread != null ? Interactive.process(string, bread) : string;
		string = Gendered.process(string, gender);
		string = Color.process(string, theme);
		return MiniMessage.miniMessage().deserialize(string);
	}

	public String getColoredLegacy(String theme) {
		string = Translation.translate(string, language);
		string = string.replace("<clear_chat>", System.lineSeparator().repeat(30));
		string = bread != null ? Interactive.process(string, bread) : string;
		string = Gendered.process(string, gender);
		string = Color.process(string, theme);
		Component component = MiniMessage.miniMessage().deserialize(string);
		return LegacyComponentSerializer.legacySection().serialize(component).replace('&', '§');
	}

	public String getStripped() {
		string = Translation.translate(string, language);
		string = string.replace("<clear_chat>", System.lineSeparator().repeat(30));
		string = bread != null ? Interactive.process(string, bread) : string;
		string = Gendered.process(string, gender);
		string = Color.removeColor(string);
		string = Translation.removeAllTags(string);
		return string;
	}
	
	private void processSpecialTags() {
		cancelled = string.contains("<cancel_message>");
		string = string.replace("<cancel_message>", "");
		Matcher matcher = Pattern.compile("<delay:(\\d+)>").matcher(string);
		if (matcher.find()) {
			delay = Integer.parseInt(matcher.group(1));
			string = matcher.replaceAll("");
		}
	}

    public static String replacePlaceholderOutputs(UUID player, String format) {
        StringBuilder result = new StringBuilder(format);
        Set<Entry<String, Object>> placeholdersKeys = Config.getConfigurationSection("placeholder-output-replacements").entrySet();

        for (Entry<String, Object> entry : placeholdersKeys) {
            String placeholderKey = entry.getKey();
            String replacementKey = Synergy.platform().placeholders(player, placeholderKey);
            Set<Entry<String, Object>> placeholders = Config.getConfigurationSection("placeholder-output-replacements." + placeholderKey).entrySet();
            for (Entry<String, Object> p : placeholders) {
                String configKey = p.getKey();
                String configValue = (String) p.getValue();

                if (replacementKey.isEmpty() && configKey.equals("%none%")) {
                    replaceInResult(result, placeholderKey, configValue);
                } else if (!replacementKey.isEmpty() && configKey.equals(placeholderKey)) {
                    String formattedValue = configValue.replace(placeholderKey, replacementKey);
                    replaceInResult(result, placeholderKey, formattedValue);
                }
            }
        }

        return result.toString();
    }

    private static void replaceInResult(StringBuilder result, String placeholderKey, String replacementValue) {
        int index = result.indexOf(placeholderKey);
        while (index != -1) {
            result.replace(index, index + placeholderKey.length(), replacementValue);
            index = result.indexOf(placeholderKey, index + replacementValue.length());
        }
    }
}
