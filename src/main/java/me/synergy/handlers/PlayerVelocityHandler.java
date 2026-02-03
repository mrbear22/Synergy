package me.synergy.handlers;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;

import me.synergy.brains.Synergy;
import me.synergy.brains.Velocity;
import me.synergy.discord.Discord;
import me.synergy.objects.BreadMaker;
import me.synergy.text.Translation;
import me.synergy.twitch.Twitch;
import me.synergy.web.MonobankHandler;
import net.dv8tion.jda.api.entities.User;

public class PlayerVelocityHandler {

    private static Set<UUID> kickedPlayers = new HashSet<>();

    public void initialize() {
        Velocity.getProxy().getEventManager().register(Velocity.getInstance(), this);
        Synergy.getLogger().info(getClass().getSimpleName() + " module has been initialized!");
    }

    @Subscribe
    public void onServerConnect(ServerPreConnectEvent event) {
        BreadMaker bread = Synergy.getBread(event.getPlayer().getUniqueId());
        bread.getCache().clear();
        bread.setData("name", event.getPlayer().getUsername());
        Player player = event.getPlayer();
      
        if (!Synergy.getConfig().getBoolean("discord.enabled")) {
            return;
        }

        if (Synergy.getConfig().getBoolean("discord.kick-player.if-banned.enabled")) {
            if (Discord.isBanned(bread)) {
                kickedPlayers.add(player.getUniqueId());
                bread.kick(Synergy.getConfig().getString("discord.kick-player.if-banned.message"));
                event.setResult(ServerPreConnectEvent.ServerResult.denied());
                return;
            }
        }
    }

    @Subscribe
    public void onPlayerLogin(PostLoginEvent event) {
        BreadMaker bread = Synergy.getBread(event.getPlayer().getUniqueId());
        bread.getCache().clear();
        bread.setData("name", event.getPlayer().getUsername());
        Player player = event.getPlayer();

        if (Synergy.getConfig().getBoolean("twitch.enabled") && bread.getData("twitch-username").isSet()) {
            try {
                Twitch.getConnectionManager().connect(
                    bread.getData("twitch-username").getAsString(), 
                    bread.getData("twitch-token").getAsString()
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (Synergy.getConfig().getBoolean("monobank.enabled") && bread.getData("monobank").isSet()) {
            try {
                MonobankHandler.connect(
                    bread.getName(), 
                    bread.getData("monobank").getAsString()
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        if (Synergy.getConfig().getBoolean("discord.enabled") && Synergy.getConfig().getBoolean("discord.kick-player.if-banned.enabled")) {
            if (Discord.isBanned(bread)) {
                kickedPlayers.add(player.getUniqueId());
                bread.kick(Synergy.getConfig().getString("discord.kick-player.if-banned.message"));
                return;
            }
        }

        if (Synergy.getConfig().getBoolean("discord.enabled") && Synergy.getConfig().getBoolean("discord.kick-player.if-muted.enabled")) {
            if (Discord.isMuted(bread)) {
                kickedPlayers.add(player.getUniqueId());
                bread.kick(Synergy.getConfig().getString("discord.kick-player.if-muted.message"));
                return;
            }
        }

        if (Synergy.getConfig().getBoolean("discord.enabled") && bread.getData("confirm-discord").isSet()) {
            Velocity.getProxy().getScheduler().buildTask(Velocity.getInstance(), () -> {
                User user = Synergy.getDiscord().getUserById(bread.getData("confirm-discord").getAsString());
                if (user != null) {
                    bread.sendMessage(Translation.translate("<lang>link-discord-confirmation</lang>", bread.getLanguage())
                            .replace("%ACCOUNT%", user.getEffectiveName()));
                }
            }).delay(2, TimeUnit.SECONDS).schedule();
        } else if (Synergy.getConfig().getBoolean("discord.enabled") && Synergy.getConfig().getBoolean("discord.kick-player.if-missing.enabled")) {
            if (Discord.isMissing(bread)) {
                kickedPlayers.add(player.getUniqueId());
                bread.kick(Synergy.getConfig().getString("discord.kick-player.if-missing.message"));
                return;
            }
        }
        
        if (Synergy.getConfig().getBoolean("discord.enabled") && Synergy.getConfig().getBoolean("discord.player-join-leave-messages")) {
            kickedPlayers.add(player.getUniqueId());
            Velocity.getProxy().getScheduler().buildTask(Velocity.getInstance(), () -> {
                if (Velocity.getProxy().getPlayer(player.getUniqueId()).isPresent()) {
                    kickedPlayers.remove(player.getUniqueId());
                    Synergy.createSynergyEvent("discord-embed")
                        .setPlayerUniqueId(player.getUniqueId())
                        .setOption("chat", "global")
                        .setOption("color", "#81ecec")
                        .setOption("author", Synergy.translate("<lang>player-join-message<arg>" + player.getUsername() + "</arg></lang>", Translation.getDefaultLanguage())
                            .setGendered(bread.getGender())
                            .getStripped())
                        .fireEvent();
                }
            }).delay(2, TimeUnit.SECONDS).schedule();
        }
    }

    @Subscribe
    public void onPlayerQuit(DisconnectEvent event) {
        Player player = event.getPlayer();
        BreadMaker bread = Synergy.getBread(player.getUniqueId());
        bread.getCache().clear();

        if (kickedPlayers.remove(player.getUniqueId())) {
            return;
        }
        
        if (Synergy.getConfig().getBoolean("twitch.enabled") && bread.getData("twitch-username").isSet()) {
            try {
                Twitch.getConnectionManager().disconnect(
                    bread.getData("twitch-username").getAsString()
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        if (Synergy.getConfig().getBoolean("monobank.enabled") && bread.getData("monobank").isSet()) {
            try {
                MonobankHandler.disconnect(
                    bread.getData("monobank").getAsString()
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        if (Synergy.getConfig().getBoolean("discord.enabled") && Synergy.getConfig().getBoolean("discord.player-join-leave-messages")) {
            Synergy.createSynergyEvent("discord-embed")
                .setPlayerUniqueId(player.getUniqueId())
                .setOption("chat", "global")
                .setOption("color", "#fab1a0")
                .setOption("author", Synergy.translate("<lang>player-quit-message<arg>" + player.getUsername() + "</arg></lang>", Translation.getDefaultLanguage())
                    .setGendered(bread.getGender())
                    .getStripped())
                .fireEvent();
        }
    }
}