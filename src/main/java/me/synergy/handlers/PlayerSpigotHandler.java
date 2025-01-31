package me.synergy.handlers;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import me.synergy.brains.Synergy;
import me.synergy.objects.BreadMaker;
import me.synergy.objects.Chat;
import me.synergy.utils.Translation;

public class PlayerSpigotHandler implements Listener {

    public void initialize() {
        Bukkit.getPluginManager().registerEvents(this, Synergy.getSpigot());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
    	Player player = event.getPlayer();
        UUID uuid = event.getPlayer().getUniqueId();
        BreadMaker bread = Synergy.getBread(uuid);
        String channel = new Chat("global").getDiscord().getChannel();
        
    	event.setQuitMessage("<lang>synergy-player-quit-message<arg>"+event.getPlayer().getName()+"</arg></lang><pronoun>"+bread.getPronoun().name()+"</pronoun>");
    	
    	bread.getCache().clear();
    	
    	Synergy.getLogger().discord("```Player "+event.getPlayer().getName()+" has left ```");
    	
    	if (Synergy.getConfig().getBoolean("discord.enabled") && Synergy.getConfig().getBoolean("discord.player-join-leave-messages") && channel.length( )== 19) {
    		Synergy.createSynergyEvent("discord-embed")
				   .setPlayerUniqueId(player.getUniqueId())
				   .setOption("chat", "global")
				   .setOption("color", "#fab1a0")
				   .setOption("author", Synergy.translate("<lang>synergy-player-quit-message<arg>"+player.getName()+"</arg></lang>", Translation.getDefaultLanguage())
												.setEndings(bread.getPronoun())
												.getStripped()).fireEvent();
    	}
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
    	Player player = event.getPlayer();
        UUID uuid = event.getPlayer().getUniqueId();
        BreadMaker bread = Synergy.getBread(uuid);
        String channel = new Chat("global").getDiscord().getChannel();
        bread.getCache().clear();
        bread.setData("name", event.getPlayer().getName());
    	event.setJoinMessage("<lang>synergy-player-join-message<arg>"+event.getPlayer().getName()+"</arg></lang><pronoun>"+bread.getPronoun().name()+"</pronoun>");
    	
    	Synergy.getLogger().discord("```Player "+event.getPlayer().getName()+" has joined with IP "+event.getPlayer().getAddress()+" ```");
    	
    	if (Synergy.getConfig().getBoolean("discord.enabled") && Synergy.getConfig().getBoolean("discord.player-join-leave-messages") && channel.length( )== 19) {
        	Synergy.createSynergyEvent("discord-embed").setPlayerUniqueId(player.getUniqueId())
		           .setOption("channel", channel)
		           .setOption("color", "#81ecec")
		           .setOption("author", Synergy.translate("<lang>synergy-player-join-message<arg>"+player.getName()+"</arg></lang>", Translation.getDefaultLanguage())
					        			.setEndings(bread.getPronoun())
					        			.getStripped()).fireEvent();
    	}

    }
    
	@EventHandler
	public void onPlayerKick(PlayerKickEvent event) {
	 	Synergy.getLogger().discord("```Player "+event.getPlayer().getName()+" has been kicked with the reason: "+event.getReason()+"```");
        BreadMaker bread = Synergy.getBread(event.getPlayer().getUniqueId());
		event.setReason(Synergy.translate(event.getReason(), bread.getLanguage()).getLegacyColored(bread.getTheme()));
    }

}