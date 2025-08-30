package me.synergy.handlers;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import me.clip.placeholderapi.PlaceholderAPI;
import me.synergy.anotations.SynergyHandler;
import me.synergy.anotations.SynergyListener;
import me.synergy.brains.Synergy;
import me.synergy.events.SynergyEvent;
import me.synergy.integrations.PlotSquaredAPI;
import me.synergy.integrations.SaberFactionsAPI;
import me.synergy.objects.BreadMaker;
import me.synergy.objects.Chat;
import me.synergy.objects.DataObject;
import me.synergy.text.Color;
import me.synergy.utils.Cooldown;
import me.synergy.utils.Translation;
import me.synergy.utils.Utils;

public class ChatHandler implements Listener, SynergyListener {

	public interface MessageSource {
	    String getColor();
	    String getTag();
	}
	
    public void initialize() {
    	if (!Synergy.getConfig().getBoolean("chat-manager.enabled")) {
    		return;
    	}
        Bukkit.getPluginManager().registerEvents(this, Synergy.getSpigot());
        Synergy.getEventManager().registerEvents(this);	
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        BreadMaker bread = Synergy.getBread(player.getUniqueId());
        DataObject data = bread.getData("chat");
		Cooldown cooldown = Synergy.getCooldown(event.getPlayer().getUniqueId());
        
        if (!bread.isAuthenticated() || bread.isMuted()) {
            event.setCancelled(true);
        	return;
		}
        
        Message message = new Message(event.getMessage());
        Chat chat = new Chat(message.hasChatSymbol() && new Chat(getChatNameBySymbol(event.getMessage())).isEnabled() ? getChatNameBySymbol(event.getMessage())
        					: data.isSet() && new Chat(data.getAsString()).isEnabled() ? data.getAsString()
        					: new Chat("local").isEnabled() ? "local"
        					: "global");
        
        if (message.getMessage().isEmpty()) {
        	event.getPlayer().sendMessage("<lang>message-cant-be-empty</lang>");
            event.setCancelled(true);
        	return;
        }
        
        if (cooldown.hasCooldown("chat")) {
        	event.getPlayer().sendMessage("<lang>cooldown</lang>");
            event.setCancelled(true);
        	return;
        }
        cooldown.setCooldown("chat", 2);
        
        switch(chat.getName()) {
        	case "local":
                int radius = chat.getRadius();
                event.getRecipients().removeIf(recipient -> recipient.getWorld() != player.getWorld()
                		|| recipient.getLocation().distance(player.getLocation()) > radius);
                break;
            case "plot":
                if (!Synergy.isDependencyAvailable("PlotSquared")) {
                	player.sendMessage("<lang>dependency-not-found<arg>PlotSquared</arg></lang>");
	            	event.setCancelled(true);
	            	return;
	            }
        		if (PlotSquaredAPI.getCurrentPlot(player) == null) {
                	player.sendMessage("<lang>youre-not-in-plot</lang>");
                	event.setCancelled(true);
                	return;
        		}
                event.getRecipients().removeIf(recipient -> !PlotSquaredAPI.getCurrentPlot(player).equals(PlotSquaredAPI.getCurrentPlot(recipient)));
                break;
            case "faction":
            	if (!Synergy.isDependencyAvailable("Factions")) {
                	player.sendMessage("<lang>dependency-not-found<arg>Factions</arg></lang>");
                	event.setCancelled(true);
                	return;
            	}
        		if (SaberFactionsAPI.getFactionByPlayer(player) == null) {
                	player.sendMessage("<lang>youre-not-in-faction</lang>");
                	event.setCancelled(true);
                	return;
        		}
        		event.getRecipients().removeIf(recipient -> !SaberFactionsAPI.getFactionByPlayer(player).equals(SaberFactionsAPI.getFactionByPlayer(recipient)));
        }
        
        if (chat.getPermission() != null) {
        	event.getRecipients().removeIf(recipient -> !recipient.hasPermission(chat.getPermission()));
        }
        
        if (Synergy.getConfig().getBoolean("chat-manager.use-interactive-tags")) {
        	event.getRecipients().stream().forEach(recipient -> recipient.sendMessage(formatMessage(chat, message, player)));
        	Bukkit.getLogger().info("[CHAT] "+"["+chat.getTag()+"] "+player.getName()+": "+message.getMessage());
        	event.setCancelled(true);
        } else {
        	event.setFormat(formatMessage(chat, message, player));
        }

        logMessage(chat, message, player);
        
        sendDiscordMessage(chat, message, player);
        
        if (Synergy.getConfig().getBoolean("chat-manager.warn-if-nobody-in-chat") && event.getRecipients().size() < 2 && !"global".equals(chat.getName())) {
        	player.sendMessage("<lang>noone-hears-you</lang>");
        }
    }

	@SynergyHandler
    public void onSynergyPluginMessage(SynergyEvent event) {
	    if (event.getIdentifier().equals("system-chat")) {
	    	if (Bukkit.getPlayer(event.getPlayerUniqueId()) != null) {
	    		Bukkit.getPlayer(event.getPlayerUniqueId()).sendMessage(event.getOption("message").getAsString());
	    	}
	    }
	    
	    if (event.getIdentifier().equals("broadcast")) {
	    	for (Player p : Bukkit.getOnlinePlayers()) {
	    		BreadMaker bread = Synergy.getBread(p.getUniqueId());
	    		p.sendMessage(Synergy.translate(event.getOption("message").getAsString(), bread.getLanguage())
	    				.setPlaceholders(bread)
	    				.setEndings(event.getBread().getPronoun())
	    				.setExecuteInteractive(bread)
	    				.getLegacyColored(bread.getTheme()));
	    	}
	    }
	}
    
	@SynergyHandler
	public void onTwitchMessage(SynergyEvent event) throws SQLException {
	    if (!"twitch-chat".equals(event.getIdentifier())) return;

	    String user = event.getOption("player").getAsString();
	    String channel = event.getOption("twitch-channel").getAsString();
	    String message = event.getOption("message").getAsString();
	    	    
	    Chat chat = new Chat("twitch");
	    if (!chat.isEnabled()) return;

	    UUID uuid = Synergy.getDataManager().findUserUUID("twitch-username", channel);
	    if (uuid == null) {
	        return;
	    }

	    Synergy.getBread(uuid).sendMessage(formatMessage(chat, new Message(message), user));
	    Synergy.getLogger().info("[" + channel + "] " + user + ": " + message);
	}
	
	@SynergyHandler
	public void onDiscordMessage(SynergyEvent event) throws SQLException {
	    if (!"discord-chat".equals(event.getIdentifier())) return;

	    String discordUserId = event.getOption("discord-user-id").getAsString();
	    String discordChannelId = event.getOption("discord-channel-id").getAsString();
	    
	    String chatName = getChats().stream()
	        .filter(chat -> chat.getDiscord().getChannel().equals(discordChannelId))
	        .map(Chat::getName)
	        .findFirst()
	        .orElse(null);
	    
	    if (chatName == null) return;
	    
	    Chat chat = new Chat(chatName);
	    if (!chat.isEnabled()) return;

	    UUID uuid = Synergy.getDataManager().findUserUUID("discord", discordUserId);
	    if (uuid == null) {
	        sendEmbedMessage(discordChannelId, "<lang>you-have-to-link-account</lang>");
	        return;
	    }

	    OfflinePlayer player = Synergy.getSpigot().getOfflinePlayerByUniqueId(uuid);
	    Set<Player> recipients = new HashSet<>(Bukkit.getOnlinePlayers());

	    if (Synergy.getBread(uuid).isMuted()) {
	        sendEmbedMessage(discordChannelId, "<lang>you-are-muted</lang>");
	        return;
	    }

	    if (chat.getPermission() != null) {
	        recipients.removeIf(recipient -> !recipient.hasPermission(chat.getPermission()));
	    }

	    recipients.forEach(recipient -> recipient.sendMessage(formatMessage(chat.getDiscord(), new Message(event.getOption("message").getAsString()), player)));
	}

	@EventHandler
	public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
		Cooldown cooldown = Synergy.getCooldown(event.getPlayer().getUniqueId());
		if (!cooldown.hasCooldown("command")) {
		    List<String> commands = Arrays.asList("/reg", "/register", "/l", "/login", "/unregister");
		    if (!commands.stream().anyMatch(event.getMessage()::startsWith)) {
		        Synergy.getLogger().discord("```["+Synergy.getServerName()+"] [cmd] " + event.getPlayer().getName() + ": " + event.getMessage() + "```");
		    }
		}
	}

	private String formatMessage(MessageSource source, Message message, OfflinePlayer player) {
        String format = Synergy.getConfig().getString("chat-manager.format");
        BreadMaker bread = Synergy.getBread(player.getUniqueId());

        if (Synergy.isDependencyAvailable("PlaceholderAPI")) {
        	format = Utils.replacePlaceholderOutputs(player, format);
            format = PlaceholderAPI.setPlaceholders(player, format);
        }
        
        if (!bread.hasPermission("synergy.colors")) {
        	message.setMessage(Utils.stripColorTags(message.getMessage()));
        }

        format = format.replace("%CHAT%", source.getTag());
        format = format.replace("%COLOR%", source.getColor());
        format = format.replace("%DISPLAYNAME%", player.getName());
        format = format.replace("%MESSAGE%", message.getMessage());
        format = format.replace("%", "%%");
        format = Color.processLegacyColors(format, bread.getTheme());
        format = Color.processCustomColorCodes(format);
        
        return format;
    }
    
	private String formatMessage(MessageSource source, Message message, String player) {
        String format = Synergy.getConfig().getString("chat-manager.format");

        if (Synergy.isDependencyAvailable("PlaceholderAPI")) {
        	format = Utils.replacePlaceholderOutputs(null, format);
            format = PlaceholderAPI.setPlaceholders(null, format);
        }
        
        format = format.replace("%CHAT%", source.getTag());
        format = format.replace("%COLOR%", source.getColor());
        format = format.replace("%DISPLAYNAME%", player);
        format = format.replace("%MESSAGE%", message.getMessage());
        format = format.replace("%", "%%");
        
        return format;
    }
	
    private void logMessage(Chat chat, Message message, Player player) {
    	Synergy.getLogger().discord("```["+Synergy.getServerName()+"] [" + chat.getTag() + "] " + player.getName() + ": " + message.getMessage() + "```");
    }
    
	private void sendEmbedMessage(String channelId, String messageKey) {
	    Synergy.createSynergyEvent("discord-embed")
	        .setOption("channel", channelId)
	        .setOption("title", Synergy.translate(messageKey, Translation.getDefaultLanguage()).getStripped())
	        .setOption("color", "#fab1a0")
	        .send();
	}
    
    private void sendDiscordMessage(Chat chat, Message message, Player player) {
		if (chat.getDiscord().getChannel().length() != 19) {
			return;
		}
		if (chat.getPermission() != null && !player.hasPermission(chat.getPermission())) {
			return;
		}
        Synergy.createSynergyEvent("discord-embed").setPlayerUniqueId(player.getUniqueId())
		       .setOption("channel", chat.getDiscord().getChannel())
		       .setOption("author", player.getName())
		       .setOption("title", message.getMessage())
		       .setOption("color", chat.getColor().substring(1, chat.getColor().length()-1)).send();
    }
	
	private static Set<Chat> getChats() {
	    return Synergy.getConfig().getConfigurationSection("chat-manager.chats").entrySet().stream()
	        .map(entry -> new Chat(entry.getKey()))
	        .collect(Collectors.toSet());
	}
    
    private static String getChatNameBySymbol(String message) {
        for (Chat chat : getChats()) {
        	if (chat.getSymbol() != null && !chat.getSymbol().isEmpty() && message.startsWith(chat.getSymbol())) {
                return chat.getName();
            }
        }
        return new Chat("local").isEnabled() ? "local" : "global";
    }

	static class Message  {
		private String message;

		public Message(String message) {
			message = Utils.translateSmiles(message);
			message = Utils.censorBlockedWords(message);
			message = Utils.removeRepetitiveCharacters(message);
			message = Translation.removeAllTags(message);
			this.message = message;
		}
		
		public void setMessage(String message) {
			this.message = message;
		}

		public String getMessage() {
			return Message.removeChatSymbol(message);
		}
		
		public Boolean hasChatSymbol() {
			for (Chat chat : getChats()) {
	        	if (chat.getSymbol() != null && message.startsWith(chat.getSymbol())) {
	                return true;
	            }
			}
	        return false;
		}
		
	    private static String removeChatSymbol(String message) {
	    	Chat chat = new Chat(getChatNameBySymbol(message));
	        if (message.length() > 0 && chat.getSymbol() != null && chat.getSymbol().contains(String.valueOf(message.charAt(0)))) {
	        	message = message.substring(1);
	        }
	        return message;
	    }
	}
}
