package me.synergy.handlers;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.proxy.Player;

import me.synergy.brains.Synergy;
import me.synergy.brains.Velocity;
import me.synergy.modules.Module;
import me.synergy.objects.BreadMaker;

public class PlayerVelocityHandler implements Module {

    @Override
    public void start() {
        Velocity.getProxy().getEventManager().register(Velocity.getInstance(), this);
    }

    @Subscribe
    public void onPlayerLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        PlayerSessions.joinAsync(player.getUniqueId(), player.getUsername(), player.getRemoteAddress().getAddress().getHostAddress());
    }

    @Subscribe
    public void onPlayerQuit(DisconnectEvent event) {
        PlayerSessions.quitAsync(event.getPlayer().getUniqueId(), event.getPlayer().getUsername());
    }

    @Subscribe
    public void onPlayerKick(KickedFromServerEvent event) {
        BreadMaker bread = Synergy.getBread(event.getPlayer().getUniqueId());
        var reason = event.getServerKickReason().orElse(null);
        Synergy.getLogger().discord("```Player " + event.getPlayer().getUsername() + " has been kicked with the reason: "
            + (reason != null ? Synergy.translate(reason, bread.getLanguage()).getStripped() : "no reason") + "```");
        if (reason != null) event.setResult(KickedFromServerEvent.DisconnectPlayer.create(Synergy.translate(reason, bread.getLanguage()).getColoredComponent(bread.getTheme())));
    }
}
