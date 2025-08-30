package me.synergy.handlers;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import me.synergy.brains.Synergy;
import me.synergy.discord.Discord;
import me.synergy.objects.BreadMaker;
import me.synergy.objects.Chat;
import me.synergy.twitch.Twitch;
import me.synergy.utils.Translation;
import me.synergy.web.MonobankHandler;
import net.dv8tion.jda.api.entities.User;

public class PlayerSpigotHandler implements Listener {

    private static Set<UUID> kickedPlayers = new HashSet<>();
    
    public void initialize() {
        Bukkit.getPluginManager().registerEvents(this, Synergy.getSpigot());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
    	Player player = event.getPlayer();
        UUID uuid = event.getPlayer().getUniqueId();
        BreadMaker bread = Synergy.getBread(uuid);
        bread.setData("name", event.getPlayer().getName());
    	bread.getCache().clear();
    	
        if (kickedPlayers.remove(event.getPlayer().getUniqueId())) {
        	event.setQuitMessage(null);
        	return;
        }
        
    	event.setQuitMessage("<lang>player-quit-message<arg>"+event.getPlayer().getName()+"</arg></lang><pronoun>"+bread.getPronoun().name()+"</pronoun>");

    	Synergy.getLogger().discord("```Player "+event.getPlayer().getName()+" has left ```");

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
        
        String channel = new Chat("global").getDiscord().getChannel();
    	if (Synergy.getConfig().getBoolean("discord.enabled") && Synergy.getConfig().getBoolean("discord.player-join-leave-messages") && channel.length() == 19) {
    		Synergy.createSynergyEvent("discord-embed")
				   .setPlayerUniqueId(player.getUniqueId())
				   .setOption("chat", "global")
				   .setOption("color", "#fab1a0")
				   .setOption("author", Synergy.translate("<lang>player-quit-message<arg>"+player.getName()+"</arg></lang>", Translation.getDefaultLanguage())
												.setEndings(bread.getPronoun())
												.getStripped()).fireEvent();
    	}
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
    	Player player = event.getPlayer();
        UUID uuid = event.getPlayer().getUniqueId();
        BreadMaker bread = Synergy.getBread(uuid);
        bread.getCache().clear();
        bread.setData("name", event.getPlayer().getName());
        
	    event.setJoinMessage("<lang>player-join-message<arg>"+event.getPlayer().getName()+"</arg></lang><pronoun>"+bread.getPronoun().name()+"</pronoun>");
        
    	Synergy.getLogger().discord("```Player "+event.getPlayer().getName()+" has joined with IP "+event.getPlayer().getAddress()+" ```");
        
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
        
        if (Synergy.getConfig().getBoolean("discord.kick-player.if-has-no-link.enabled")) {
        	if (!bread.getData("discord", false).isSet()) {
                kickedPlayers.add(player.getUniqueId());
        		event.setJoinMessage(null);
        		bread.kick(Synergy.getConfig().getString("discord.kick-player.if-has-no-link.message"));
    	        return;
        	}
        }
        
        if (Synergy.getConfig().getBoolean("discord.enabled") && Synergy.getConfig().getBoolean("discord.kick-player.if-banned.enabled")) {
        	if (Discord.isBanned(bread)) {
                kickedPlayers.add(player.getUniqueId());
        		bread.kick(Synergy.getConfig().getString("discord.kick-player.if-banned.message"));
        		event.setJoinMessage(null);
    	        return;
        	}
        }

        if (Synergy.getConfig().getBoolean("discord.enabled") && Synergy.getConfig().getBoolean("discord.kick-player.if-missing.enabled")) {
        	if (Discord.isMissing(bread)) {
                kickedPlayers.add(player.getUniqueId());
        		bread.kick(Synergy.getConfig().getString("discord.kick-player.if-missing.message"));
        		event.setJoinMessage(null);
    	        return;
        	}
        }

        if (Synergy.getConfig().getBoolean("discord.enabled") && Synergy.getConfig().getBoolean("discord.kick-player.if-muted.enabled")) {
        	if (Discord.isMuted(bread)) {
                kickedPlayers.add(player.getUniqueId());
        		bread.kick(Synergy.getConfig().getString("discord.kick-player.if-muted.message"));
        		event.setJoinMessage(null);
    	        return;
        	}
        }

        String channel = new Chat("global").getDiscord().getChannel();
		if (Synergy.getConfig().getBoolean("discord.enabled") && Synergy.getConfig().getBoolean("discord.player-join-leave-messages") && channel.length() == 19) {
        	Synergy.createSynergyEvent("discord-embed").setPlayerUniqueId(player.getUniqueId())
		           .setOption("channel", channel)
		           .setOption("color", "#81ecec")
		           .setOption("author", Synergy.translate("<lang>player-join-message<arg>"+player.getName()+"</arg></lang>", Translation.getDefaultLanguage())
					        			.setEndings(bread.getPronoun())
					        			.getStripped()).fireEvent();
		}
		
		if (Synergy.getConfig().getBoolean("discord.enabled") && bread.getData("confirm-discord").isSet()) {
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
	 	Synergy.getLogger().discord("```Player "+event.getPlayer().getName()+" has been kicked with the reason: "+event.getReason()+"```");
        BreadMaker bread = Synergy.getBread(event.getPlayer().getUniqueId());
		event.setReason(Synergy.translate(event.getReason(), bread.getLanguage()).getLegacyColored(bread.getTheme()));
    }

}