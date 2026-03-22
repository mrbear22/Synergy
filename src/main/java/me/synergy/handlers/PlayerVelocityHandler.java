package me.synergy.handlers;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;

import me.synergy.brains.Synergy;
import me.synergy.brains.Velocity;
import me.synergy.discord.Discord;
import me.synergy.modules.Config;
import me.synergy.modules.Locales;
import me.synergy.objects.BreadMaker;
import me.synergy.objects.LocaleBuilder;
import me.synergy.text.Translation;
import me.synergy.twitch.Twitch;
import me.synergy.web.MonobankHandler;
import net.dv8tion.jda.api.entities.User;

public class PlayerVelocityHandler {

    private static Set<UUID> kickedPlayers = new HashSet<>();

    public void initialize() {
        Velocity.getProxy().getEventManager().register(Velocity.getInstance(), this);
        Locales.addDefault("player-join-message", "en", "<secondary>[<success>+<secondary>] <primary>%player% <secondary>joined the server");
        Locales.addDefault("player-quit-message", "en", "<secondary>[<danger>-<secondary>] <primary>%player% <secondary>left the server");
        Locales.addDefault("player-first-time-join-message", "en", "<primary>%player% <secondary>joined for the first time!");
        Synergy.getLogger().info(getClass().getSimpleName() + " module has been initialized!");
    }

    @Subscribe
    public void onServerConnect(ServerPreConnectEvent event) {
        BreadMaker bread = Synergy.getBread(event.getPlayer().getUniqueId());
        bread.getCache().clear();
        bread.setData("name", event.getPlayer().getUsername());
        Player player = event.getPlayer();
      
        if (!Config.getBoolean("discord.enabled")) {
            return;
        }

        if (Config.getBoolean("discord.kick-player.if-banned.enabled")) {
            if (Discord.isBanned(bread)) {
                kickedPlayers.add(player.getUniqueId());
                bread.kick(Config.getString("discord.kick-player.if-banned.message"));
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

        if (Config.getBoolean("twitch.enabled") && bread.getData("twitch-username").isSet()) {
            try {
                Twitch.getConnectionManager().connect(
                    bread.getData("twitch-username").getAsString(), 
                    bread.getData("twitch-token").getAsString()
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (Config.getBoolean("monobank.enabled") && bread.getData("monobank").isSet()) {
            try {
                MonobankHandler.connect(
                    bread.getName(), 
                    bread.getData("monobank").getAsString()
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        if (Config.getBoolean("discord.enabled") && Config.getBoolean("discord.kick-player.if-banned.enabled")) {
            if (Discord.isBanned(bread)) {
                kickedPlayers.add(player.getUniqueId());
                bread.kick(Config.getString("discord.kick-player.if-banned.message"));
                return;
            }
        }

        if (Config.getBoolean("discord.enabled") && Config.getBoolean("discord.kick-player.if-muted.enabled")) {
            if (Discord.isMuted(bread)) {
                kickedPlayers.add(player.getUniqueId());
                bread.kick(Config.getString("discord.kick-player.if-muted.message"));
                return;
            }
        }

        if (Config.getBoolean("discord.enabled") && bread.getData("confirm-discord").isSet()) {
            Velocity.getProxy().getScheduler().buildTask(Velocity.getInstance(), () -> {
                User user = Synergy.getDiscord().getUserById(bread.getData("confirm-discord").getAsString());
                if (user != null) {
                    bread.sendMessage(Translation.translate("<lang>link-discord-confirmation</lang>", bread.getLanguage())
                            .replace("%ACCOUNT%", user.getEffectiveName()));
                }
            }).delay(2, TimeUnit.SECONDS).schedule();
        } else if (Config.getBoolean("discord.enabled") && Config.getBoolean("discord.kick-player.if-missing.enabled")) {
            if (Discord.isMissing(bread) && (bread.getData("discord").isSet() && !bread.getData("discord").getAsString().equals("00000"))) {
                kickedPlayers.add(player.getUniqueId());
                bread.kick(Config.getString("discord.kick-player.if-missing.message"));
                return;
            }
        }
        
        if (Config.getBoolean("discord.enabled") && Config.getBoolean("discord.player-join-leave-messages")) {
            kickedPlayers.add(player.getUniqueId());
            Velocity.getProxy().getScheduler().buildTask(Velocity.getInstance(), () -> {
                if (Velocity.getProxy().getPlayer(player.getUniqueId()).isPresent()) {
                    kickedPlayers.remove(player.getUniqueId());
                    Synergy.event("discord-embed")
                        .setPlayerUniqueId(player.getUniqueId())
                        .setOption("chat", "global")
                        .setOption("color", "#81ecec")
                        .setOption("author", Synergy.translate(LocaleBuilder.of("player-join-message").placeholder("player", player.getUsername()).build(), Translation.getDefaultLanguage())
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
        
        if (Config.getBoolean("twitch.enabled") && bread.getData("twitch-username").isSet()) {
            try {
                Twitch.getConnectionManager().disconnect(
                    bread.getData("twitch-username").getAsString()
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        if (Config.getBoolean("monobank.enabled") && bread.getData("monobank").isSet()) {
            try {
                MonobankHandler.disconnect(
                    bread.getData("monobank").getAsString()
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        if (Config.getBoolean("discord.enabled") && Config.getBoolean("discord.player-join-leave-messages")) {
            Synergy.event("discord-embed")
                .setPlayerUniqueId(player.getUniqueId())
                .setOption("chat", "global")
                .setOption("color", "#fab1a0")
                .setOption("author", Synergy.translate(LocaleBuilder.of("player-quit-message").placeholder("player", player.getUsername()).build(), Translation.getDefaultLanguage())
                    .setGendered(bread.getGender())
                    .getStripped())
                .fireEvent();
        }
    }
    
    @Subscribe
	public void onPlayerKick(KickedFromServerEvent event) {
	 	Synergy.getLogger().discord("```Player "+event.getPlayer().getUsername()+" has been kicked with the reason: "+event.getServerKickReason().get().toString()+"```");
        BreadMaker bread = Synergy.getBread(event.getPlayer().getUniqueId());
		//event.reason(Synergy.translate(event.getServerKickReason().get(), bread.getLanguage()).getColoredComponent(bread.getTheme()));
    }
}