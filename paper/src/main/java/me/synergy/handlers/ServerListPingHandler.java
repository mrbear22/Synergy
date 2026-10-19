package me.synergy.handlers;

import me.synergy.modules.Module;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

import me.synergy.brains.Spigot;
import me.synergy.modules.Config;
import me.synergy.objects.Locale;
import me.synergy.text.Color;
import me.synergy.text.Translation;

public class ServerListPingHandler implements Listener, Module {

    @Override
    public void defaults() {
        Config.addDefault("motd.enabled", true);
        Config.addDefault("motd.message", "Message of The Day Example");
        Config.addDefault("motd.max-players", 100);
    }

    @Override
    public void start() {
        Spigot.getInstance().getServer().getPluginManager().registerEvents(this, Spigot.getInstance());
    }

	@EventHandler
    public void onPing(ServerListPingEvent event) {
		if (Config.getBoolean("motd.enabled")) {
			event.motd(new Locale(Config.getString("motd.message"), Translation.getDefaultLanguage()).getColoredComponent(Color.getDefaultTheme()));
			event.setMaxPlayers(Config.getInt("motd.max-players"));
		}
    }
}
