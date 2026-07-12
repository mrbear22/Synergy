package me.synergy.handlers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import me.synergy.brains.Synergy;
import me.synergy.discord.Discord;
import me.synergy.modules.Config;
import me.synergy.modules.Locales;
import me.synergy.objects.BreadMaker;
import me.synergy.objects.Chat;
import me.synergy.objects.LocaleBuilder;
import me.synergy.text.Translation;
import me.synergy.twitch.Twitch;
import me.synergy.web.MonobankHandler;
import net.dv8tion.jda.api.entities.User;

public class PlayerSpigotHandler implements Listener {

    private static Set<UUID> kickedPlayers = new HashSet<>();
    
    public void initialize() {
        Bukkit.getPluginManager().registerEvents(this, Synergy.getSpigot());
        Locales.addDefault("player-join-message", "en", "<secondary>[<success>+<secondary>] <primary>%player% <secondary>joined the server");
        Locales.addDefault("player-quit-message", "en", "<secondary>[<danger>-<secondary>] <primary>%player% <secondary>left the server");
        Locales.addDefault("player-first-time-join-message", "en", "<primary>%player% <secondary>joined for the first time!");
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        String username = event.getName();

        try {
        	UUID oldUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8)); // Synergy.getDataManager().findUuidByName(username);
            if (oldUuid != null && !oldUuid.equals(uuid)) {
                Synergy.getDataManager().migrateUuid(oldUuid, uuid);
                Synergy.getLogger().info("Migrated UUID for " + username + ": " + oldUuid + " -> " + uuid);

                for (org.bukkit.World world : Bukkit.getWorlds()) {
                    Path base = world.getWorldFolder().toPath();
                    migrateFile(base.resolve("playerdata").resolve(oldUuid + ".dat"),
                                base.resolve("playerdata").resolve(uuid + ".dat"));
                    migrateFile(base.resolve("playerdata").resolve(oldUuid + ".dat_old"),
                                base.resolve("playerdata").resolve(uuid + ".dat_old"));
                    migrateFile(base.resolve("stats").resolve(oldUuid + ".json"),
                                base.resolve("stats").resolve(uuid + ".json"));
                    migrateFile(base.resolve("advancements").resolve(oldUuid + ".json"),
                                base.resolve("advancements").resolve(uuid + ".json"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        BreadMaker bread = Synergy.getBread(uuid);
        try {
            bread.getData("twitch-username");
            bread.getData("twitch-token");
            bread.getData("monobank");
            bread.getData("discord");
            bread.getData("confirm-discord");
            bread.getData("last-voted");
            bread.getData("gender");
            bread.getData("chat");
            bread.getData("theme");
            bread.getData("name");
            bread.getData("votes");
            bread.getData("mojangUUID");
            bread.getData("language");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void migrateFile(Path source, Path target) {
        try {
            if (Files.exists(source) && !Files.exists(target)) {
                Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
    	Player player = event.getPlayer();
        UUID uuid = event.getPlayer().getUniqueId();
        BreadMaker bread = Synergy.getBread(uuid);
        bread.setData("name", event.getPlayer().getName());
    	bread.getCache().clear();
    	
        if (kickedPlayers.remove(event.getPlayer().getUniqueId())) {
        	event.quitMessage(null);
        	return;
        }
        
    	event.quitMessage(LocaleBuilder.of("player-quit-message")
    			.placeholder("player", event.getPlayer().getName())
    			.placeholder("gender", bread.getGender().name().toLowerCase())
    			.component()
    		);

    	Synergy.getLogger().discord("```Player "+event.getPlayer().getName()+" has left ```");

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
        
        String channel = new Chat("global").getDiscord().getChannel();
    	if (Config.getBoolean("discord.enabled") && Config.getBoolean("discord.player-join-leave-messages") && channel.length() == 19) {
    		Synergy.event("discord-embed")
				   .setPlayerUniqueId(player.getUniqueId())
				   .setOption("chat", "global")
				   .setOption("color", "#fab1a0")
				   .setOption("author", LocaleBuilder.of("player-quit-message").placeholder("player", player.getName()).placeholder("gender", bread.getGender().name()).build())
				   .fireEvent();
    	}
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
    	Player player = event.getPlayer();
        UUID uuid = event.getPlayer().getUniqueId();
        BreadMaker bread = Synergy.getBread(uuid);
        bread.getCache().clear();
        bread.setData("name", event.getPlayer().getName());
        
	    event.joinMessage(LocaleBuilder.of("player-join-message")
	    		.placeholder("player", event.getPlayer().getName())
	    		.placeholder("gender", bread.getGender().name().toLowerCase())
	    		.component()
	    	);
        
    	Synergy.getLogger().discord("```Player "+event.getPlayer().getName()+" has joined with IP "+event.getPlayer().getAddress()+" ```");
        
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
        
        if (Config.getBoolean("discord.kick-player.if-has-no-link.enabled")) {
        	if (!bread.getData("discord", false).isSet()) {
                kickedPlayers.add(player.getUniqueId());
        		event.joinMessage(null);
        		bread.kick(Config.getString("discord.kick-player.if-has-no-link.message"));
    	        return;
        	}
        }
        
        if (Config.getBoolean("discord.enabled") && Config.getBoolean("discord.kick-player.if-banned.enabled")) {
        	if (Discord.isBanned(bread)) {
                kickedPlayers.add(player.getUniqueId());
        		bread.kick(Config.getString("discord.kick-player.if-banned.message"));
        		event.joinMessage(null);
    	        return;
        	}
        }

        if (Config.getBoolean("discord.enabled") && Config.getBoolean("discord.kick-player.if-missing.enabled")) {
            if (Discord.isMissing(bread) && (bread.getData("discord").isSet() && !bread.getData("discord").getAsString().equals("00000"))) {
                kickedPlayers.add(player.getUniqueId());
        		bread.kick(Config.getString("discord.kick-player.if-missing.message"));
        		event.joinMessage(null);
    	        return;
        	}
        }

        if (Config.getBoolean("discord.enabled") && Config.getBoolean("discord.kick-player.if-muted.enabled")) {
        	if (Discord.isMuted(bread)) {
                kickedPlayers.add(player.getUniqueId());
        		bread.kick(Config.getString("discord.kick-player.if-muted.message"));
        		event.joinMessage(null);
    	        return;
        	}
        }

        String channel = new Chat("global").getDiscord().getChannel();
		if (Config.getBoolean("discord.enabled") && Config.getBoolean("discord.player-join-leave-messages") && channel.length() == 19) {
        	Synergy.event("discord-embed").setPlayerUniqueId(player.getUniqueId())
		           .setOption("channel", channel)
		           .setOption("color", "#81ecec")
			       .setOption("author", LocaleBuilder.of("player-join-message").placeholder("player", player.getName()).placeholder("gender", bread.getGender().name()).build())
			       .fireEvent();
		}
		
		if (Config.getBoolean("discord.enabled") && bread.getData("confirm-discord").isSet()) {
			User user = Synergy.getDiscord().getUserById(bread.getData("confirm-discord").getAsString());
		    if (user != null) {
		        Bukkit.getScheduler().runTaskLater(Synergy.getSpigot(), () -> {
		            bread.sendMessage(Translation.translate("<lang>link-discord-confirmation</lang>", bread.getLanguage()).replace("%ACCOUNT%", user.getEffectiveName()));
		        }, 40L);
		    }
		}
		

    }
    
	@EventHandler
	public void onPlayerKick(PlayerKickEvent event) {
	 	Synergy.getLogger().discord("```Player "+event.getPlayer().getName()+" has been kicked with the reason: "+event.reason()+"```");
        BreadMaker bread = Synergy.getBread(event.getPlayer().getUniqueId());
		event.reason(Synergy.translate(event.reason(), bread.getLanguage()).getColoredComponent(bread.getTheme()));
    }

}