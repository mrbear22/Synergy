package me.synergy.discord;

import java.util.UUID;

import org.jetbrains.annotations.NotNull;

import me.synergy.anotations.SynergyListener;
import me.synergy.brains.Synergy;
import me.synergy.objects.BreadMaker;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateTimeOutEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class MembersHandler extends ListenerAdapter implements SynergyListener {

	public MembersHandler() {
        try {
	        if (!Synergy.getConfig().getBoolean("discord.enabled")) {
	            return;
	        }
	        
	        Synergy.getEventManager().registerEvents(this);

	        Synergy.getLogger().info(String.valueOf(getClass().getSimpleName()) + " module has been initialized!");
	    } catch (Exception exception) {
	        Synergy.getLogger().error(String.valueOf(getClass().getSimpleName()) + " module failed to initialize: " + exception.getMessage());
	    	exception.printStackTrace();
	    }
	}
	
    @Override
    public void onGuildBan(@NotNull GuildBanEvent event) {
        if (Synergy.getConfig().getBoolean("discord.kick-player.if-banned.enabled")) {
	        String userId = event.getUser().getId();
	        UUID uniqueId = Discord.getUniqueIdByDiscordId(userId);
	        BreadMaker bread = Synergy.getBread(uniqueId);
        	bread.kick(Synergy.getConfig().getString("discord.kick-player.if-banned.message"));
        }
    }

    @Override
    public void onGuildUnban(@NotNull GuildUnbanEvent event) {
       // String userId = event.getUser().getId();
    }

    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        if (Synergy.getConfig().getBoolean("discord.kick-player.if-missing.enabled")) {
	        String userId = event.getUser().getId();
	        UUID uniqueId = Discord.getUniqueIdByDiscordId(userId);
	        BreadMaker bread = Synergy.getBread(uniqueId);
        	bread.kick(Synergy.getConfig().getString("discord.kick-player.if-missing.message"));
        }
    }

    @Override
    public void onGuildMemberUpdateTimeOut(@NotNull GuildMemberUpdateTimeOutEvent event) {
        if (Synergy.getConfig().getBoolean("discord.kick-player.if-muted.enabled")) {
	        String userId = event.getUser().getId();
	        if (event.getNewTimeOutEnd() != null) {
		        UUID uniqueId = Discord.getUniqueIdByDiscordId(userId);
		        BreadMaker bread = Synergy.getBread(uniqueId);
	        	bread.kick(Synergy.getConfig().getString("discord.kick-player.if-muted.message"));
	        } else {
	            // 
	        }
        }
        	
    }
}
