package me.synergy.handlers;

import me.synergy.brains.Bungee;
import me.synergy.modules.Module;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

public class PlayerBungeeHandler implements Listener, Module {

    @Override
    public void start() {
        Bungee.getInstance().getProxy().getPluginManager().registerListener(Bungee.getInstance(), this);
    }

    @EventHandler
    public void onPlayerLogin(PostLoginEvent event) {
        ProxiedPlayer player = event.getPlayer();
        PlayerSessions.joinAsync(player.getUniqueId(), player.getName(), player.getSocketAddress().toString());
    }

    @EventHandler
    public void onPlayerQuit(PlayerDisconnectEvent event) {
        PlayerSessions.quitAsync(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }
}
