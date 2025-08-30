package me.synergy.objects;

import me.clip.placeholderapi.PlaceholderAPI;
import me.synergy.brains.Synergy;
import me.synergy.text.Color;
import me.synergy.utils.Endings;
import me.synergy.utils.Endings.Pronoun;
import me.synergy.text.Interactive;
import me.synergy.utils.Translation;
import me.synergy.utils.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

public class Locale {

	String string;
	String language;

	public Locale(String string, String language) {
		this.language = language;
		this.string = Translation.translate(string, language);
	}

	public String getString() {
		return string;
	}
	
	public Locale setEndings(Pronoun pronoun) {
		string = Endings.processEndings(string, pronoun);
		string = Endings.removeEndingTags(string);
		return this;
	}
	
	public Locale setExecuteInteractive(BreadMaker bread) {
		string = Interactive.process(string, bread);
		string = Interactive.removeTags(string);
		return this;
	}

	public Locale setPlaceholders(BreadMaker bread) {
		if (Synergy.isDependencyAvailable("PlaceholderAPI")) {
			string = Utils.replacePlaceholderOutputs(Synergy.getSpigot().getOfflinePlayerByUniqueId(bread.getUniqueId()), string);
			string = PlaceholderAPI.setPlaceholders(Synergy.getSpigot().getOfflinePlayerByUniqueId(bread.getUniqueId()), string);
		}
		return this;
	}
	
	public String getColored(String theme) {
        Component adventureComponent = GsonComponentSerializer.gson().deserialize(this.string);
        this.string = MiniMessage.miniMessage().serialize(adventureComponent);

		string = Interactive.removeTags(string);
		string = Endings.removeEndingTags(string);
        string = Color.processThemeTags(string, theme);
        string = Color.processColorReplace(string, theme);
        string = Color.processLegacyColorCodes(string);
        
        string = string.replaceAll("\\\\<", "<").replaceAll("\\\\>", ">");
        
        Component component = MiniMessage.miniMessage().deserialize(string);
        string = GsonComponentSerializer.gson().serialize(component);

        string = Color.removeTags(string);
		return string;
	}

	public String getLegacyColored(String theme) {
		string = Interactive.removeTags(string);
		string = Endings.removeEndingTags(string);
		string = Color.processLegacyColors(string, theme);
		return string;
	}

	public String getStripped() {
		string = Translation.removeAllTags(string);
		string = Color.removeColor(string);
		string = Interactive.removeTags(string);
		string = Endings.removeEndingTags(string);
		return string;
	}

}
