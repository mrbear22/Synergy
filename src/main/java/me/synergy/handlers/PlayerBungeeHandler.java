package me.synergy.handlers;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import me.synergy.brains.Bungee;
import me.synergy.brains.Synergy;
import me.synergy.discord.Discord;
import me.synergy.modules.Locales;
import me.synergy.objects.BreadMaker;
import me.synergy.text.Translation;
import me.synergy.twitch.Twitch;
import me.synergy.web.MonobankHandler;
import net.dv8tion.jda.api.entities.User;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

public class PlayerBungeeHandler implements Listener {

    private static Set<UUID> kickedPlayers = new HashSet<>();

    public void initialize() {
        Bungee.getInstance().getProxy().getPluginManager().registerListener(Bungee.getInstance(), this);
        Locales.addDefault("player-join-message", "en", "<secondary>[<success>+<secondary>] <primary>%player% <secondary>joined the server");
        Locales.addDefault("player-quit-message", "en", "<secondary>[<danger>-<secondary>] <primary>%player% <secondary>left the server");
        Locales.addDefault("player-first-time-join-message", "en", "<primary>%player% <secondary>joined for the first time!");
        Synergy.getLogger().info(getClass().getSimpleName() + " module has been initialized!");
    }

    @EventHandler
    public void onServerConect(ServerConnectEvent event) {
        BreadMaker bread = Synergy.getBread(event.getPlayer().getUniqueId());
        bread.getCache().clear();
        bread.setData("name", event.getPlayer().getName());
        ProxiedPlayer player = event.getPlayer();
      
        if (!Synergy.getConfig().getBoolean("discord.enabled")) {
            return;
        }

        if (Synergy.getConfig().getBoolean("discord.kick-player.if-banned.enabled")) {
            if (Discord.isBanned(bread)) {
                kickedPlayers.add(player.getUniqueId());
                bread.kick(Synergy.getConfig().getString("discord.kick-player.if-banned.message"));
                return;
            }
        }
    }

    @EventHandler
    public void onPlayerLogin(PostLoginEvent event) {
        BreadMaker bread = Synergy.getBread(event.getPlayer().getUniqueId());
        bread.getCache().clear();
        bread.setData("name", event.getPlayer().getName());
        ProxiedPlayer player = event.getPlayer();

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

        if (Synergy.getConfig().getBoolean("discord.enabled") && Synergy.getConfig().getBoolean("discord.kick-player.if-missing.enabled")) {
            if (Discord.isMissing(bread)) {
                kickedPlayers.add(player.getUniqueId());
                bread.kick(Synergy.getConfig().getString("discord.kick-player.if-missing.message"));
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
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.schedule(() -> {
                User user = Synergy.getDiscord().getUserById(bread.getData("confirm-discord").getAsString());
                if (user != null) {
                    bread.sendMessage(Translation.translate("<lang>link-discord-confirmation</lang>", bread.getLanguage())
                            .replace("%ACCOUNT%", user.getEffectiveName()));
                }
                scheduler.shutdown();
            }, 2, TimeUnit.SECONDS);
        }

        if (Synergy.getConfig().getBoolean("discord.enabled") && Synergy.getConfig().getBoolean("discord.player-join-leave-messages")) {
        	kickedPlayers.add(player.getUniqueId());
            Bungee.getInstance().getProxy().getScheduler().schedule(
                Bungee.getInstance(),
                () -> {
                    if (Bungee.getInstance().getProxy().getPlayer(player.getUniqueId()) != null) {
                    	kickedPlayers.remove(event.getPlayer().getUniqueId());
                        Synergy.event("discord-embed")
                            .setPlayerUniqueId(player.getUniqueId())
                            .setOption("chat", "global")
                            .setOption("color", "#81ecec")
                            .setOption("author", Synergy.translate("<lang>player-join-message<arg>" + player.getName() + "</arg></lang>", Translation.getDefaultLanguage())
                                .setGendered(bread.getGender())
                                .getStripped())
                            .fireEvent();
                    }
                },
                2, TimeUnit.SECONDS
            );
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerDisconnectEvent event) {
        ProxiedPlayer player = event.getPlayer();
        BreadMaker bread = Synergy.getBread(event.getPlayer().getUniqueId());
        bread.getCache().clear();

        if (kickedPlayers.remove(event.getPlayer().getUniqueId())) {
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
	        Synergy.event("discord-embed")
	            .setPlayerUniqueId(player.getUniqueId())
	            .setOption("chat", "global")
	            .setOption("color", "#fab1a0")
	            .setOption("author", Synergy.translate("<lang>player-quit-message<arg>" + player.getName() + "</arg></lang>", Translation.getDefaultLanguage())
	                .setGendered(bread.getGender())
	                .getStripped())
	            .fireEvent();
        }
    }
}
