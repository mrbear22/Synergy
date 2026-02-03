package me.synergy.handlers;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

import me.synergy.brains.Synergy;
import me.synergy.objects.Locale;
import me.synergy.text.Color;
import me.synergy.text.Translation;

public class ServerListPingHandler implements Listener {

    public void initialize() {
        Synergy.getSpigot().getServer().getPluginManager().registerEvents(this, Synergy.getSpigot());
    }

	@EventHandler
    public void onPing(ServerListPingEvent event) {
		if (Synergy.getConfig().getBoolean("motd.enabled")) {
			event.motd(new Locale(Synergy.getConfig().getString("motd.message"), Translation.getDefaultLanguage()).getColoredComponent(Color.getDefaultTheme()));
			event.setMaxPlayers(Synergy.getConfig().getInt("motd.max-players"));
		}
    }
}
