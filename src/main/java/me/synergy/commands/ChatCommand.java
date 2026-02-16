package me.synergy.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import me.synergy.brains.Synergy;
import me.synergy.modules.Locales;
import me.synergy.objects.Chat;
import me.synergy.utils.BookMessage;
import me.synergy.utils.Utils;

public class ChatCommand implements CommandExecutor, TabCompleter {

    public void initialize() {
        if (Synergy.getConfig().getBoolean("chat-manager.enabled")) {
            Synergy.getSpigot().getCommand("chat").setExecutor(this);
            Synergy.getSpigot().getCommand("colors").setExecutor(this);
            Synergy.getSpigot().getCommand("emojis").setExecutor(this);
            Synergy.getSpigot().getCommand("chat").setExecutor(this);
            Synergy.getSpigot().getCommand("chat").setTabCompleter(this);
            Synergy.getSpigot().getCommand("chatfilter").setExecutor(this);
            Synergy.getSpigot().getCommand("chatfilter").setTabCompleter(this);
            
            Locales.addDefault("command_description_colors", "en", "View available colors");
            Locales.addDefault("command_usage_colors", "en", new String[] {
    		    "<danger>Usage: /colors",
    		    "",
    		    "<secondary>This command displays:",
    		    "<primary>• <secondary>Available custom color codes",
    		    "<primary>• <secondary>Color previews with examples",
    		    "<primary>• <secondary>How to use colors in chat",
    		    "<primary>• <secondary>Your color permissions"
    		});

            Locales.addDefault("command_description_emojis", "en", "View available emojis");
            Locales.addDefault("command_usage_emojis", "en", new String[] {
    		    "<danger>Usage: /emojis",
    		    "",
    		    "<secondary>This command shows:",
    		    "<primary>• <secondary>Complete emoji list",
    		    "<primary>• <secondary>Emoji codes and previews",
    		    "<primary>• <secondary>How to use emojis in chat",
    		    "<primary>• <secondary>Your emoji permissions"
    		});

            Locales.addDefault("command_description_chatfilter", "en", "Chat filter management");
            Locales.addDefault("command_usage_chatfilter", "en", new String[] {
    		    "<danger>Usage: /chatfilter <action> <word>",
    		    "",
    		    "<secondary>Actions:",
    		    "<primary>  block  <secondary>- Add word to blocked list",
    		    "<primary>  remove <secondary>- Remove word from filter",
    		    "<primary>  ignore <secondary>- Add to personal ignore list",
    		    "",
    		    "<secondary>Examples:",
    		    "<primary>  /chatfilter block spam",
    		    "<primary>  /chatfilter remove test",
    		    "<primary>  /chatfilter ignore annoy"
    		});
            
            Locales.addDefault("command_description_chat", "en", "Chat management");
            Locales.addDefault("command_usage_chat", "en", new String[] {
    		    "<danger>Usage: /chat <chat_name> [player]",
    		    "",
    		    "<secondary>Arguments:",
    		    "<primary>  chat_name <secondary>- Chat channel to join",
    		    "<primary>  player    <secondary>- (Optional) Target player",
    		    "",
    		    "<secondary>Examples:",
    		    "<primary>  /chat global <secondary>- Join global chat",
    		    "<primary>  /chat local  <secondary>- Switch to local chat",
    		    "<primary>  /chat staff  <secondary>- Access staff chat"
    		});
            
            Locales.addDefault("selected-chat", "en", "<success>Chat selected: <primary>%ARGUMENT%<sound:'entity.ocelot.death'>");
    		
            Synergy.getLogger().info(String.valueOf(getClass().getSimpleName()) + " module has been initialized!");
        }
    }

	@Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

		if (!sender.hasPermission("synergy."+command.getLabel().toLowerCase())) {
			return null;
		}

		Player player = (Player) sender;
		
    	if (command.getLabel().equals("chat")) {
	    	if (args.length < 2) {
		        return getChats(player);
	    	}
    	}
    	if (command.getLabel().equals("chatfilter")) {
    		if (args.length < 2) {
    			return Arrays.asList(new String[] {"block", "ignore", "remove"});
    		}
	    	if (args.length > 0 && args[0].equalsIgnoreCase("remove")) {
	    		return Stream.concat(Utils.getBlockedWorlds().stream(), Utils.getIgnoredWorlds().stream()).toList();
	    	}
    	}
    	return null;
    }

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

		if (!sender.hasPermission("synergy."+label.toLowerCase())) {
			sender.sendMessage("<lang>no-permission</lang>");
			return true;
		}

		if (label.equalsIgnoreCase("chatfilter")) {
			if (args.length > 1) {
				if (args[0].equalsIgnoreCase("block")) {
					Utils.addBlockedWord(args[1].toLowerCase());
					sender.sendMessage("<lang>word-blocked<arg>"+args[1].toLowerCase()+"</arg></lang>");
				}
				if (args[0].equalsIgnoreCase("ignore")) {
					Utils.addIgnoredWord(args[1].toLowerCase());
					sender.sendMessage("<lang>word-ignored<arg>"+args[1].toLowerCase()+"</arg></lang>");
				}
				if (args[0].equalsIgnoreCase("remove")) {
					Utils.removeBlockedWord(args[1].toLowerCase());
					Utils.removeIgnoredWord(args[1].toLowerCase());
					sender.sendMessage("<lang>word-removed<arg>"+args[1].toLowerCase()+"</arg></lang>");
				}
			} else {
				sender.sendMessage("<lang>command-usage</lang> /chatfilter block/ignore/remove <word>");
			}
		}

		if (label.equalsIgnoreCase("chat")) {

			Player player = (Player) sender;
			
			if (args.length == 0) {
				player.sendMessage("<lang>command-usage</lang> /chat <"+String.join("/", getChats(player))+">");
				return true;
			}

			if (getChats(player).contains(args[0].toLowerCase())) {
				Synergy.getBread(((Player) sender).getUniqueId()).setData("chat", args[0].toLowerCase());
				sender.sendMessage("<lang>selected-chat<arg>"+args[0].toLowerCase()+"</arg></lang>");
				return true;
			}
		}

		if (label.equalsIgnoreCase("colors")) {
		    Map<String, Object> tags = Synergy.getConfig().getConfigurationSection("chat-manager.custom-color-tags");
	        List<String> colors = new ArrayList<>();
	        for (Entry<String, Object> t : tags.entrySet()) {
	        	String color = Synergy.getConfig().getString("chat-manager.custom-color-tags."+t.getKey());
	            colors.add(color+t.getKey().replace("&", "$")/*String.join(color, t.split(""))*/);
	        }
	        if (sender instanceof Player) {
	            BookMessage.sendFakeBook((Player) sender, "Colors", String.join("\n", colors));
	        } else {
	            sender.sendMessage("Only the player can execute this command.");
	        }
		}

		if (label.equalsIgnoreCase("emojis")) {
		    Set<String> tags = Synergy.getConfig().getConfigurationSection("chat-manager.custom-emojis").keySet();
		    int count = 0;
		    StringBuilder messageBuilder = new StringBuilder();
		    int maxEmojiLength = 0;
		    for (String e : tags) {
		        String emoji = Synergy.getConfig().getString("chat-manager.custom-emojis."+e);
		        maxEmojiLength = Math.max(maxEmojiLength, e.length() + emoji.length() + 3);
		    }
		    for (String e : tags) {
		        if (count == 2) {
		            sender.sendMessage(messageBuilder.toString());
		            messageBuilder = new StringBuilder();
		            count = 0;
		        }
		        String emoji = Synergy.getConfig().getString("chat-manager.custom-emojis."+e);
		        int padding = maxEmojiLength - e.length() - emoji.length();
		        messageBuilder.append(String.format("<lang>primary</lang>%s - <lang>secondary</lang>%s%" + padding + "s", e, emoji, ""));
		        count++;
		    }
		    if (messageBuilder.length() > 0) {
		        sender.sendMessage(messageBuilder.toString());
		    }
		}

		return true;
	}
	
	private List<String> getChats(Player player) {
	    return Synergy.getConfig().getConfigurationSection("chat-manager.chats").entrySet().stream()
	        .map(entry -> new Chat(entry.getKey()))
	        .filter(chat -> chat.isEnabled() && (chat.getPermission() == null || player.hasPermission(chat.getPermission())))
	        .map(Chat::getName)
	        .collect(Collectors.toList());
	}

	
}
