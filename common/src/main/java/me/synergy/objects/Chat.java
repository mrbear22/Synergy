package me.synergy.objects;

import me.synergy.brains.Platform.Position;
import me.synergy.brains.Synergy;
import me.synergy.handlers.ChatHandler.ChatFilter;
import me.synergy.handlers.ChatHandler.MessageSource;
import me.synergy.modules.Config;
import java.util.*;

public class Chat implements MessageSource {
    private final String name;
    private static final Map<String, ChatFilter> FILTERS = new HashMap<>();

    public Chat(String name) {
        this.name = name;
    }

    public String getName() { return name; }
    public boolean isEnabled() { return Config.getBoolean("chat-manager.chats." + name + ".enabled", false); }
    public int getRadius() { return Config.getInt("chat-manager.chats." + name + ".radius", 0); }
    public String getColor() { return Config.getString("chat-manager.chats." + name + ".color", "<#ffffff>"); }
    public String getTag() { return Config.getString("chat-manager.chats." + name + ".tag", ""); }
    public String getSymbol() { return Config.getString("chat-manager.chats." + name + ".symbol", null); }
    public String getPermission() { return Config.getString("chat-manager.chats." + name + ".permission", null); }
    public String getPlaceholder() { return Config.getString("chat-manager.chats." + name + ".placeholder", null); }
    public String getFormat() { return Config.getString("chat-manager.chats." + name + ".format", Config.getString("chat-manager.format")); }
    public Discord getDiscord() { return new Discord(name); }

    public static void register(String name, ChatFilter filter) {
        FILTERS.put(name, filter);
    }

    public static boolean applyFilter(String chatName, UUID sender, Set<UUID> viewers) {
        var filter = FILTERS.get(chatName);
        return filter == null || filter.apply(sender, viewers);
    }

    public static void registerAll() {
        var platform = Synergy.platform();
        Config.getConfigurationSection("chat-manager.chats").keySet().forEach(name -> {
            var chat = new Chat(name);
            if (!chat.isEnabled()) return;

            register(name, (p, viewers) -> {

                if (chat.getRadius() > 0) {
                    var from = platform.position(p).orElse(null);
                    viewers.removeIf(v -> from == null || platform.position(v).map(to -> !in(from, to, chat.getRadius())).orElse(true));
                }

                if (chat.getPermission() != null) {
                    if (!platform.hasPermission(p, chat.getPermission())) return false;
                    viewers.removeIf(v -> !platform.hasPermission(v, chat.getPermission()));
                }

                if (chat.getPlaceholder() != null) {
                    var value = platform.placeholders(p, chat.getPlaceholder());
                    if (value.isEmpty()) return false;
                    viewers.removeIf(v -> !value.equals(platform.placeholders(v, chat.getPlaceholder())));
                }

                return true;
            });
        });
    }

    private static boolean in(Position a, Position b, int radius) {
        double dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
        return a.world().equals(b.world()) && dx * dx + dy * dy + dz * dz <= (double) radius * radius;
    }

    public static class Discord implements MessageSource {
        private final String name;

        public Discord(String name) {
            this.name = name;
        }

        public String getColor() { return Config.getString("chat-manager.chats." + name + ".discord.color", "<#ffffff>"); }
        public String getTag() { return Config.getString("chat-manager.chats." + name + ".discord.tag", ""); }
        public String getChannel() { return Config.getString("chat-manager.chats." + name + ".discord.channel", "000"); }
        public String getPermission() { return Config.getString("chat-manager.chats." + name + ".discord.permission", null); }
        public String getFormat() { return Config.getString("chat-manager.chats." + name + ".discord.format", Config.getString("chat-manager.format")); }
    }
}
