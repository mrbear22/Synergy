package me.synergy.objects;

import java.sql.SQLException;
import java.util.UUID;

import me.synergy.brains.Synergy;
import me.synergy.integrations.AuthmeAPI;
import me.synergy.integrations.EssentialsAPI;
import me.synergy.utils.Endings.Pronoun;

public class BreadMaker {

	private UUID uuid;

	public BreadMaker(UUID uuid) {
		this.uuid = uuid;
	}

	public String getLanguage() {
		if (getData("language").isSet()) {
			return getData("language").getAsString();
		}
		if (Synergy.isRunningSpigot() && isOnline()) {
			return Synergy.getSpigot().getPlayerLanguage(getUniqueId());
		}
		return Synergy.getConfig().getString("localizations.default-language", "en");
	}

	public void sendMessage(String message) {
		Synergy.createSynergyEvent("system-chat").setPlayerUniqueId(uuid).setOption("message", message).send();
	}

	public UUID getUniqueId() {
		return uuid;
	}

	public boolean isMuted() {
		return Synergy.isRunningSpigot() ? EssentialsAPI.essentialsIsPlayerMuted(getName()) : false;
	}

	public String getName() {
		return getData("name").isSet() ? getData("name").getAsString() : null;
	}

	public DataObject getData(String option) {
		try {
			return Synergy.getDataManager().getData(getUniqueId(), option);
		} catch (SQLException e) {
			return new DataObject(null);
		}
	}

	public void setData(String option, String value) {
		 try {
			Synergy.getDataManager().setData(getUniqueId(), option, value);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public boolean isOnline() {
		if (Synergy.isRunningSpigot()) {
			return getUniqueId() == null ? false : Synergy.getSpigot().getPlayerByUniqueId(getUniqueId()) != null ? Synergy.getSpigot().getPlayerByUniqueId(getUniqueId()).isOnline() : false;
		}
		return false;
	}

    public boolean isAuthenticated() {
		if (Synergy.isRunningSpigot() && Synergy.isDependencyAvailable("Authme")) {
			return AuthmeAPI.isAuthenticated(Synergy.getSpigot().getPlayerByUniqueId(uuid));
		}
		return true;
    }

	public boolean hasPermission(String node) {
		if (Synergy.isRunningSpigot()) {
			return Synergy.getSpigot().playerHasPermission(getUniqueId(), node);
		}
		return false;
	}

	public String getTheme() {
		if (getData("theme").isSet()) {
			return getData("theme").getAsString();
		}
		return "default";
	}

	public Pronoun getPronoun() {
		if (getData("pronoun").isSet()) {
			return getData("pronoun").getAsPronoun();
		}
		return Pronoun.HE;
	}

	public Cache getCache() {
		return new Cache(getUniqueId());
	}
	
}
