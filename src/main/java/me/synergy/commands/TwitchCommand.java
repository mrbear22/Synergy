package me.synergy.commands;
import java.util.List;
import java.util.Map;
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
import me.synergy.utils.Translation;

public class TwitchCommand implements CommandExecutor, TabCompleter, Listener, SynergyListener {

	public void initialize() {
        if (!Synergy.getConfig().getBoolean("twitch.enabled")) {
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, Synergy.getSpigot());
        Synergy.getSpigot().getCommand("twitch").setExecutor(this);
        Synergy.getSpigot().getCommand("twitch").setTabCompleter(this);
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
	        return List.of("link", "unlink", "createreward", "removereward", "testreward");
	    }

	    if (args[0].equalsIgnoreCase("link")) {
	        if (args.length == 2) {
	            return List.of("<channel_name>");
	        } else if (args.length == 3) {
	            return List.of("<access_token>");
	        }
	    }

	    if (args[0].equalsIgnoreCase("createreward") || args[0].equalsIgnoreCase("removereward") || args[0].equalsIgnoreCase("testreward")) {
	        if (args.length == 2) {
	            Map<String, Object> rewardsSection = Synergy.getConfig().getConfigurationSection("twitch.rewards");
	            if (rewardsSection != null) {
	                return rewardsSection.keySet().stream()
	                        .filter(k -> k.toLowerCase().startsWith(args[1].toLowerCase()))
	                        .collect(Collectors.toList());
	            }
	        }
	    }

	    return List.of();
	}

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("<lang>command-not-player</lang>");
            return false;
        }
        
    	Player player = (Player) sender;
    	BreadMaker bread = Synergy.getBread(player.getUniqueId());
    	
    	if (!player.hasPermission("synergy.twitch")) {
    		sender.sendMessage("<lang>no-permission</lang>");
    		return false;
    	}
    	
        if (args.length == 0) {
            // Send usage directly in chat, not as a book
            String[] usageLines = Translation.translate("<lang>command_usage_twitch</lang>", bread.getLanguage()).split("\n");
            for (String line : usageLines) {
                bread.sendMessage(line);
            }
            return true;
        }
        
        switch (args[0].toLowerCase()) {
	        case "createreward":
		    	if (!bread.getData("twitch-username").isSet()) {
		    		bread.sendMessage("<lang>you-have-no-linked-twitch-accounts</lang>");
		    		return false;
		    	} 
		    	
		    	if (args.length < 2) {
		    		return false; // Show usage from plugin.yml
    	    	}
		    	
	    		Synergy.createSynergyEvent("create-twitch-reward")
	    			   .setPlayerUniqueId(player.getUniqueId())
	    			   .setOption("title", Synergy.getConfig().getString("twitch.rewards." + args[1] + ".title"))
	    			   .setOption("cost", String.valueOf(Synergy.getConfig().getInt("twitch.rewards." + args[1] + ".cost")))
	    			   .setOption("description", Synergy.getConfig().getString("twitch.rewards." + args[1] + ".description"))
	    			   .setOption("input-required", String.valueOf(Synergy.getConfig().getBoolean("twitch.rewards." + args[1] + ".input-required")))
	    			   .send();
	            break;
	            
	        case "removereward":
		    	if (!bread.getData("twitch-username").isSet()) {
		    		bread.sendMessage("<lang>you-have-no-linked-twitch-accounts</lang>");
		    		return false;
		    	} 
		    	
		    	if (args.length < 2) {
		    		return false; // Show usage from plugin.yml
    	    	}
		    	
	    		Synergy.createSynergyEvent("remove-twitch-reward")
	    			   .setPlayerUniqueId(player.getUniqueId())
	    			   .setOption("title", Synergy.getConfig().getString("twitch.rewards." + args[1] + ".title"))
	    			   .send();
	            break;
	            
	        case "testreward":
		    	if (!bread.getData("twitch-username").isSet()) {
		    		bread.sendMessage("<lang>you-have-no-linked-twitch-accounts</lang>");
		    		return false;
		    	} 
		    	
		    	if (args.length < 3) {
		    		return false; // Show usage from plugin.yml
    	    	}
		    	
	            Synergy.createSynergyEvent("twitch-reward-redeemed")
	                .setPlayerUniqueId(bread.getUniqueId())
	                .setOption("reward-title", Synergy.getConfig().getString("twitch.rewards." + args[1] + ".title"))
	                .setOption("viewer-name", "test")
	                .setOption("channel-name", bread.getData("twitch-username").getAsString())
	                .setOption("viewer-input", args[2])
	                .setOption("reward-id", "-1")
	                .send();
	            break;
	            
            case "link":
    	    	if (args.length < 3) {
    	    		return false; // Show usage from plugin.yml
    	    	}
    	    	
    	    	if (!bread.getData("twitch-username").isSet()) {
    	    		Synergy.createSynergyEvent("make-twitch-link")
    	    			   .setPlayerUniqueId(player.getUniqueId())
    	    			   .setOption("tag", args[1])
    	    			   .setOption("token", args[2])
    	    			   .send();
    	    	} else {
    	    		bread.sendMessage("<lang>link-twitch-already-linked<arg>" + bread.getData("twitch-username").getAsString() + "</arg></lang>");
    	    	}
                break;
                
            case "unlink":
            	if (bread.getData("twitch-username").isSet()) {
            		Synergy.createSynergyEvent("remove-twitch-link").setPlayerUniqueId(player.getUniqueId()).send();
            	} else {
            		bread.sendMessage("<lang>you-have-no-linked-twitch-accounts</lang>");
            	}
                break;
                
            default:
                return false;
        }
        return true;
    }
}