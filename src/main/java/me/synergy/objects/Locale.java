package me.synergy.objects;

import me.clip.placeholderapi.PlaceholderAPI;
import me.synergy.brains.Synergy;
import me.synergy.utils.Color;
import me.synergy.utils.Endings;
import me.synergy.utils.Endings.Pronoun;
import me.synergy.utils.Interactive;
import me.synergy.utils.JsonUtils;
import me.synergy.utils.Translation;
import me.synergy.utils.Utils;

public class Locale {

	String string;
	String language;

	public Locale(String string, String language) {
		this.language = language;
		this.string = Translation.translate(string, language);
		if (JsonUtils.isValidJson(this.string) && Interactive.containsTags(this.string) || Color.containsTags(this.string)) {
			this.string = JsonUtils.jsonToCustomString(this.string);
			//new Logger().info("json as string: " +this.string);
		}
	}

	public String getString() {
		return string;
	}
	
	public Locale setEndings(Pronoun pronoun) {
		string = Endings.processEndings(string, pronoun);
		return this;
	}
	
	public Locale setExecuteInteractive(BreadMaker bread) {
		string = Interactive.processInteractive(string);
		if (Synergy.isRunningSpigot()) {
			Synergy.getSpigot().executeInteractive(string, bread);
		}
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
		if (!JsonUtils.isValidJson(string)) {
			string = JsonUtils.convertToJson(string);
		}
		string = Interactive.removeInteractiveTags(string);
		string = Endings.removeEndingTags(string);
		string = Color.processColors(string, theme);
		return string;
	}

	public String getLegacyColored(String theme) {
		string = Interactive.removeInteractiveTags(string);
		string = Endings.removeEndingTags(string);
		string = Color.processLegacyColors(string, theme);
		return string;
	}

	public String getStripped() {
		string = Translation.removeAllTags(string);
		string = Color.removeColor(string);
		string = Interactive.removeInteractiveTags(string);
		string = Endings.removeEndingTags(string);
		return string;
	}

}
