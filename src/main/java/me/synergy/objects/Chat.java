package me.synergy.objects;

import me.synergy.brains.Synergy;
import me.synergy.handlers.ChatHandler.MessageSource;
import me.synergy.handlers.ChatHandler.ChatFilter;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.*;

public class Chat implements MessageSource {
    private final String name;
    private static final Map<String, ChatFilter> FILTERS = new HashMap<>();
    
    public Chat(String name) {
        this.name = name;
    }
    
    public String getName() { return name; }
    public boolean isEnabled() { return Synergy.getConfig().getBoolean("chat-manager.chats." + name + ".enabled", false); }
    public int getRadius() { return Synergy.getConfig().getInt("chat-manager.chats." + name + ".radius", 0); }
    public String getColor() { return Synergy.getConfig().getString("chat-manager.chats." + name + ".color", "<#ffffff>"); }
    public String getTag() { return Synergy.getConfig().getString("chat-manager.chats." + name + ".tag", ""); }
    public String getSymbol() { return Synergy.getConfig().getString("chat-manager.chats." + name + ".symbol", null); }
    public String getPermission() { return Synergy.getConfig().getString("chat-manager.chats." + name + ".permission", null); }
    public String getPlaceholder() { return Synergy.getConfig().getString("chat-manager.chats." + name + ".placeholder", null); }
    public String getFormat() { return Synergy.getConfig().getString("chat-manager.chats." + name + ".format", Synergy.getConfig().getString("chat-manager.format")); }
    public Discord getDiscord() { return new Discord(name); }
    
    public static void register(String name, ChatFilter filter) {
        FILTERS.put(name, filter);
    }
    
    public static boolean applyFilter(String chatName, Player player, AsyncChatEvent event) {
        var filter = FILTERS.get(chatName);
        return filter == null || filter.apply(player, event);
    }
    
    public static void registerAll() {
        Synergy.getConfig().getConfigurationSection("chat-manager.chats").keySet().forEach(name -> {
            var chat = new Chat(name);
            if (!chat.isEnabled()) return;
            
            register(name, (p, e) -> {

                if (chat.getRadius() > 0) {
                    e.viewers().removeIf(v -> !(v instanceof Player r) || r.getWorld() != p.getWorld() || 
                        r.getLocation().distance(p.getLocation()) > chat.getRadius());
                }
                
                if (chat.getPermission() != null) {
                    if (!p.hasPermission(chat.getPermission())) return false;
                    e.viewers().removeIf(v -> !(v instanceof Player r) || !r.hasPermission(chat.getPermission()));
                }
                
                if (chat.getPlaceholder() != null && Synergy.isDependencyAvailable("PlaceholderAPI")) {
                    var value = PlaceholderAPI.setPlaceholders(p, chat.getPlaceholder());
                    e.viewers().removeIf(v -> !(v instanceof Player r) || 
                        !value.equals(PlaceholderAPI.setPlaceholders(r, chat.getPlaceholder())));
                }
                
                return true;
            });
        });
    }
    
    public static class Discord implements MessageSource {
        private final String name;
        
        public Discord(String name) {
            this.name = name;
        }
        
        public String getColor() { return Synergy.getConfig().getString("chat-manager.chats." + name + ".discord.color", "<#ffffff>"); }
        public String getTag() { return Synergy.getConfig().getString("chat-manager.chats." + name + ".discord.tag", ""); }
        public String getChannel() { return Synergy.getConfig().getString("chat-manager.chats." + name + ".discord.channel", "000"); }
        public String getPermission() { return Synergy.getConfig().getString("chat-manager.chats." + name + ".discord.permission", null); }
        public String getFormat() { return Synergy.getConfig().getString("chat-manager.chats." + name + ".discord.format", Synergy.getConfig().getString("chat-manager.format")); }
    }
}