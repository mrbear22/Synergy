package me.synergy.commands;

import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import me.synergy.anotations.SynergyHandler;
import me.synergy.anotations.SynergyListener;
import me.synergy.brains.Synergy;
import me.synergy.discord.Discord;
import me.synergy.events.SynergyEvent;
import me.synergy.objects.BreadMaker;
import me.synergy.utils.BookMessage;
import me.synergy.utils.RepeatingTask;
import me.synergy.utils.Translation;

public class DiscordCommand implements CommandExecutor, TabCompleter, Listener, SynergyListener {

	public void initialize() {
        if (!Synergy.getConfig().getBoolean("discord.enabled")) {
       //     return;
        }

        Bukkit.getPluginManager().registerEvents(this, Synergy.getSpigot());
        Synergy.getSpigot().getCommand("discord").setExecutor(this);
        Synergy.getSpigot().getCommand("discord").setTabCompleter(this);
        Synergy.getEventManager().registerEvents(this);
	}

	@SynergyHandler
	public void onSynergyEvent(SynergyEvent event) {
        if (event.getOption("tags").isSet()) {
            Discord.getUsersTagsCache().clear();
        	for (String tag : event.getOption("tags").getAsString().split(",")) {
        		Discord.getUsersTagsCache().add(tag);
        	}
        }
	}
	
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("link", "unlink", "confirm");
        } else if (args.length == 2 && args[0].equals("link")) {
            return Discord.getUsersTagsCache().stream()
                    .filter(u -> u.startsWith(args[1]))
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    	Player player = (Player) sender;
    	BreadMaker bread = Synergy.getBread(player.getUniqueId());
    	if (!player.hasPermission("synergy.discord")) {
    		sender.sendMessage("<lang>synergy-no-permission</lang>");
    		return true;
    	}
        if (args.length == 0) {
        	
            StringBuilder build = new StringBuilder(Translation.translate("<lang>synergy-discord-invite</lang>", bread.getLanguage()).replace("%INVITE%", Synergy.getConfig().getString("discord.invite-link")));
            BookMessage.sendFakeBook((Player) sender, "Discord", build.toString());
        	
        	//bread.sendMessage(Translation.processLangTags("<lang>synergy-discord-invite</lang>", bread.getLanguage()).replace("%INVITE%", Synergy.getConfig().getString("discord.invite-link")));
            return true;
        }
        switch (args[0]) {
            case "link":
    	    	if (args.length < 2) {
    	    		sender.sendMessage("<lang>synergy-discord-link-cmd-usage</lang>");
    	    		return true;
    	    	}
            	Synergy.createSynergyEvent("make-discord-link").setPlayerUniqueId(player.getUniqueId()).setOption("tag", args[1]).send();
                break;
            case "confirm":
            	Synergy.createSynergyEvent("confirm-discord-link").setPlayerUniqueId(player.getUniqueId()).send();
                break;
            case "unlink":
            	Synergy.createSynergyEvent("remove-discord-link").setPlayerUniqueId(player.getUniqueId()).send();
                if (Synergy.getConfig().getBoolean("discord.kick-player.if-has-no-link.enabled")) {
                	bread.kick(Synergy.getConfig().getString("discord.kick-player.if-has-no-link.message"));
                }
                break;
        }
        return true;
    }

}
