package me.synergy.objects;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.clip.placeholderapi.PlaceholderAPI;
import me.synergy.brains.Synergy;
import me.synergy.text.Color;
import me.synergy.text.Interactive;
import me.synergy.text.Translation;
import me.synergy.text.Gendered;
import me.synergy.text.Gendered.Gender;
import me.synergy.utils.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class Locale {
	
	String string;
	String language;
	Gender gender;
	private boolean cancelled = false;
	private int delay = 0;

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
		string = Interactive.process(string, bread);
		return this;
	}

	public Locale setPlaceholders(BreadMaker bread) {
		if (Synergy.isDependencyAvailable("PlaceholderAPI")) {
			string = Utils.replacePlaceholderOutputs(Synergy.getSpigot().getOfflinePlayerByUniqueId(bread.getUniqueId()), string);
			string = PlaceholderAPI.setPlaceholders(Synergy.getSpigot().getOfflinePlayerByUniqueId(bread.getUniqueId()), string);
		}
		return this;
	}

	public Component getColoredComponent(String theme) {
		string = Translation.translate(string, language);
		string = Gendered.process(string, gender);
		string = Color.process(string, theme);
		string = Interactive.removeTags(string);
		return MiniMessage.miniMessage().deserialize(string);
	}

	public String getColoredLegacy(String theme) {
		string = Translation.translate(string, language);
		string = Gendered.process(string, gender);
		string = Color.process(string, theme);
		string = Interactive.removeTags(string);
		Component component = MiniMessage.miniMessage().deserialize(string);
		return LegacyComponentSerializer.legacySection().serialize(component).replace('&', '§');
	}

	public String getStripped() {
		string = Translation.translate(string, language);
		string = Gendered.process(string, gender);
		string = Color.removeColor(string);
		string = Interactive.removeTags(string);
		string = Translation.removeAllTags(string);
		return string;
	}
	
	private void processSpecialTags() {
		cancelled = string.contains("<cancel_message>");
		string = string.replace("<cancel_message>", "");
		string = string.replace("<clear_chat>", System.lineSeparator().repeat(30));
		Matcher matcher = Pattern.compile("<delay:(\\d+)>").matcher(string);
		if (matcher.find()) {
			delay = Integer.parseInt(matcher.group(1));
			string = matcher.replaceAll("");
		}
	}

}