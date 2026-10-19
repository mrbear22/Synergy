package me.synergy.objects;

import java.time.Duration;

import me.synergy.handlers.MessageHandler;
import me.synergy.modules.DataManager;
import java.util.UUID;
import me.synergy.brains.Synergy;
import me.synergy.modules.Config;
import me.synergy.modules.Locales;
import me.synergy.modules.PunishmentsManager;
import me.synergy.text.Gendered.Gender;

public class BreadMaker {

	private UUID uuid;

	public BreadMaker(UUID uuid) {
		this.uuid = uuid;
	}

	public String getLanguage() {
		if (getData("language").isSet()) {
			return getData("language").getAsString();
		}
		String locale = isOnline() ? Synergy.platform().locale(uuid) : null;
		if (locale != null) {
			return Locales.languages().contains(locale) ? locale : "en";
		}
		return Config.getString("localizations.default-language", "en");
	}

	public void sendMessage(String message) {
		if (!Synergy.isProxy() && isOnline()) MessageHandler.send(uuid, message, true, true);
		else Synergy.event("system-chat").toPlayer(uuid).setOption("message", message).send();
	}

	public UUID getUniqueId() {
		return uuid;
	}

	public boolean isMuted() {
		return PunishmentsManager.isMuted(this);
	}

	public long getMuteExpires() {
		return PunishmentsManager.getMuteExpires(this);
	}

	public String getMuteReason() {
		return PunishmentsManager.getMuteReason(this);
	}

	public String getMuteBy() {
		return PunishmentsManager.getMuteBy(this);
	}

	public long getMuteRemainingSeconds() {
		return PunishmentsManager.getMuteRemainingSeconds(this);
	}

	public void mute(long durationMillis, String reason, String staff) {
		PunishmentsManager.mute(this, durationMillis, reason, staff);
	}

	public void unmute() {
		PunishmentsManager.unmute(this);
	}

	public void kick(String reason) {
		PunishmentsManager.kick(getUniqueId(), reason);
	}

	public String getName() {
		return getData("name").isSet() ? getData("name").getAsString() : null;
	}

	public DataObject getData(String option) {
		return DataManager.getData(uuid, option);
	}

	public DataObject getDataOrDefault(String option, Object defaultValue) {
		return DataManager.getDataOrDefault(uuid, option, defaultValue);
	}

	public void setData(String option, Object value) {
		DataManager.setData(uuid, option, value);
	}

	public void setData(String option, Object value, Duration ttl) {
		DataManager.setData(uuid, option, value, ttl);
	}

	public boolean cooldown(String name, int seconds) {
		Cache cache = getCache();
		if (cache.has("cooldown:" + name)) return true;
		if (seconds > 0) cache.add("cooldown:" + name, true, seconds);
		return false;
	}

	public boolean hasCooldown(String name) {
		return getCache().has("cooldown:" + name);
	}

	public boolean isOnline() {
		return uuid != null && Synergy.platform().isOnline(uuid);
	}

	public boolean isAuthenticated() {
		return Synergy.platform().isAuthenticated(uuid);
	}

	public boolean hasPermission(String node) {
		return uuid != null && Synergy.platform().hasPermission(uuid, node);
	}

	public String getTheme() {
		if (getData("theme").isSet()) {
			return getData("theme").getAsString();
		}
		return "default";
	}

	public Gender getGender() {
		if (getData("gender").isSet()) {
			return getData("gender").getAsGender();
		}
		return Gender.MALE;
	}

	public Cache getCache() {
		return new Cache(getUniqueId());
	}
}