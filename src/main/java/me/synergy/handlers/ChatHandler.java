package me.synergy.handlers;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.clip.placeholderapi.PlaceholderAPI;
import me.synergy.anotations.SynergyHandler;
import me.synergy.anotations.SynergyListener;
import me.synergy.brains.Synergy;
import me.synergy.events.SynergyEvent;
import me.synergy.modules.Config;
import me.synergy.modules.Locales;
import me.synergy.objects.Chat;
import me.synergy.objects.LocaleBuilder;
import me.synergy.text.Translation;
import me.synergy.utils.Utils;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class ChatHandler implements Listener, SynergyListener {

    private static volatile boolean chatLocked = false;
    private static volatile int slowmodeSeconds = 2;

    public static boolean isChatLocked() {
        return chatLocked;
    }

    /** Локальна зміна + розсилка по всій мережі (proxy + бекенди) */
    public static void setChatLocked(boolean locked) {
        chatLocked = locked;
        Synergy.event("chat-lock-sync").setOption("locked", String.valueOf(locked)).send();
    }

    public static int getSlowmodeSeconds() {
        return slowmodeSeconds;
    }

    public static void setSlowmode(int seconds) {
        slowmodeSeconds = Math.max(seconds, 0);
    }

    @FunctionalInterface
    public interface ChatFilter {
        boolean apply(Player player, AsyncChatEvent event);
    }
    
    public interface MessageSource {
        String getColor();
        String getTag();
        String getFormat();
    }

    public void initialize() {
        if (!Config.getBoolean("chat-manager.enabled")) return;
        
        Chat.registerAll();
        
        Bukkit.getPluginManager().registerEvents(this, Synergy.getSpigot());
        Synergy.getEventManager().registerEvents(this);
        
        Locales.addDefault("cooldown", "en", "<danger>Please wait a few seconds!");
    }

    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
        var p = event.getPlayer();
        var bread = Synergy.getBread(p.getUniqueId());
        var cd = Synergy.getCooldown(p.getUniqueId());
        
        if (!bread.isAuthenticated()) {
            event.setCancelled(true);
            return;
        }

        if (bread.isMuted()) {
            long remaining = bread.getMuteRemainingSeconds();
            String remainingText = remaining == -1 ? "permanent" : Utils.formatDuration(remaining);
            String reason = bread.getMuteReason();

            p.sendMessage(LocaleBuilder.of("you-are-muted")
                .placeholder("reason", reason == null || reason.isEmpty() ? "-" : reason)
                .placeholder("remaining", remainingText)
                .build());
            event.setCancelled(true);
            return;
        }

        if (chatLocked && !p.hasPermission("synergy.chat.bypass")) {
            p.sendMessage(Synergy.translate("<locale:chat-is-locked>", bread.getLanguage())
                .setPlaceholders(bread).setGendered(bread.getGender()).getColoredComponent(bread.getTheme()));
            event.setCancelled(true);
            return;
        }
        
        var raw = LegacyComponentSerializer.legacyAmpersand().serialize(event.message());
        var msg = new Message(raw);
        var chat = new Chat(Optional.ofNullable(msg.hasChatSymbol() ? getChatBySymbol(msg.getRaw()) : null)
            .filter(n -> new Chat(n).isEnabled())
            .orElseGet(() -> Optional.ofNullable(bread.getData("chat"))
                .filter(d -> d.isSet() && new Chat(d.getAsString()).isEnabled())
                .map(d -> d.getAsString())
                .orElse(new Chat("local").isEnabled() ? "local" : "global")));
        
        if (msg.getMessage().isEmpty()) {
            p.sendMessage(Synergy.translate("<lang>message-cant-be-empty</lang>", bread.getLanguage())
                .setPlaceholders(bread).setGendered(bread.getGender()).getColoredComponent(bread.getTheme()));
            event.setCancelled(true);
            return;
        }
        
        if (cd.hasCooldown("chat")) {
            p.sendMessage(Synergy.translate("<lang>cooldown</lang>", bread.getLanguage())
                .setPlaceholders(bread).setGendered(bread.getGender()).getColoredComponent(bread.getTheme()));
            event.setCancelled(true);
            return;
        }
        cd.setCooldown("chat", slowmodeSeconds);
        
        if (!Chat.applyFilter(chat.getName(), p, event)) {
            event.setCancelled(true);
            return;
        }
        
        if (chat.getPermission() != null) {
            event.viewers().removeIf(a -> !(a instanceof Player r) || !r.hasPermission(chat.getPermission()));
        }
        
        var format = formatMessage(chat, msg, p);
        
        if ("global".equals(chat.getName())) {
            Synergy.event("global-chat")
                .setPlayerUniqueId(p.getUniqueId())
                .setOption("format", format)
                .setOption("message", msg.getMessage())
                .setOption("tag", chat.getTag())
                .send();
        
        }
        
        if (Config.getBoolean("chat-manager.use-interactive-tags")) {
        	if (!"global".equals(chat.getName())) {
	            event.viewers().forEach(a -> {
	                if (a instanceof Player r) {
	                    var b = Synergy.getBread(r.getUniqueId());
	                    r.sendMessage(Synergy.translate(format, b.getLanguage()).setPlaceholders(b).setGendered(b.getGender()).setExecuteInteractive(b).getColoredComponent(b.getTheme()));
	                }
	            });
        	}
            Bukkit.getLogger().info("[CHAT] [" + chat.getTag() + "] " + p.getName() + ": " + msg.getMessage());
            event.setCancelled(true);
        } else {
            event.renderer((s, n, m, v) -> v instanceof Player r ? 
                Synergy.translate(format, Synergy.getBread(r.getUniqueId()).getLanguage())
                    .setPlaceholders(Synergy.getBread(r.getUniqueId()))
                    .setGendered(Synergy.getBread(r.getUniqueId()).getGender())
                    .getColoredComponent(Synergy.getBread(r.getUniqueId()).getTheme()) : m);
        }

        Synergy.getLogger().discord("```[" + Synergy.getServerName() + "] [" + chat.getTag() + "] " + 
            p.getName() + ": " + msg.getMessage() + "```");
        
        if (chat.getDiscord().getChannel().length() == 19 && 
            (chat.getPermission() == null || p.hasPermission(chat.getPermission()))) {
            Synergy.event("discord-embed").setPlayerUniqueId(p.getUniqueId())
                .setOption("channel", chat.getDiscord().getChannel())
                .setOption("author", p.getName())
                .setOption("title", msg.getMessage())
                .setOption("color", chat.getColor().substring(1, chat.getColor().length() - 1)).send();
        }
        
        if (Config.getBoolean("chat-manager.warn-if-nobody-in-chat") && 
            event.viewers().size() < 2 && !"global".equals(chat.getName())) {
            p.sendMessage(Synergy.translate("<lang>noone-hears-you</lang>", bread.getLanguage())
                .setPlaceholders(bread).setGendered(bread.getGender()).getColoredComponent(bread.getTheme()));
        }
    }

    @SynergyHandler
    public void onSynergyPluginMessage(SynergyEvent event) {
        if ("system-chat".equals(event.getIdentifier())) {
            Optional.ofNullable(Bukkit.getPlayer(event.getPlayerUniqueId())).ifPresent(p -> {
                var b = Synergy.getBread(p.getUniqueId());
                p.sendMessage(Synergy.translate(event.getOption("message").getAsString(), b.getLanguage())
                    .setPlaceholders(b).setGendered(b.getGender()).getColoredComponent(b.getTheme()));
            });
        }
        
        if ("broadcast".equals(event.getIdentifier())) {
            Bukkit.getOnlinePlayers().forEach(p -> {
                var b = Synergy.getBread(p.getUniqueId());
                p.sendMessage(Synergy.translate(event.getOption("message").getAsString(), b.getLanguage())
                    .setPlaceholders(b).setGendered(event.getBread().getGender())
                    .setExecuteInteractive(b).getColoredComponent(b.getTheme()));
            });
        }
        
        if ("global-chat".equals(event.getIdentifier())) {
        	Synergy.getLogger().info(Synergy.getServerName() + " --- " + event.getServerName());
            if (Synergy.getServerName().equals(event.getServerName())) return;
            var format = event.getOption("format").getAsString();
            Bukkit.getOnlinePlayers().forEach(p -> {
                var b = Synergy.getBread(p.getUniqueId());
                p.sendMessage(Synergy.translate(format, b.getLanguage())
                    .setPlaceholders(b).setGendered(b.getGender())
                    .setExecuteInteractive(b).getColoredComponent(b.getTheme()));
            });
        }

        if ("chat-lock-sync".equals(event.getIdentifier())) {
            chatLocked = Boolean.parseBoolean(event.getOption("locked").getAsString());
        }
    }
    
    @SynergyHandler
    public void onTwitchMessage(SynergyEvent event) throws SQLException {
        if (!"twitch-chat".equals(event.getIdentifier())) return;

        var chat = new Chat("twitch");
        if (!chat.isEnabled()) return;

        UUID uuid = Synergy.getDataManager().findUserUUID("twitch-username",
            event.getOption("twitch-channel").getAsString());
        if (uuid == null) return;

        if (Bukkit.getPlayer(uuid) == null) return;

        Synergy.getBread(uuid).sendMessage(formatMessage(chat,
            new Message(event.getOption("message").getAsString()),
            event.getOption("player").getAsString()));

        Synergy.getLogger().info("[" + event.getOption("twitch-channel").getAsString() + "] " +
            event.getOption("player").getAsString() + ": " + event.getOption("message").getAsString());
    }
    
    @SynergyHandler
    public void onDiscordMessage(SynergyEvent event) throws SQLException {
        if (!"discord-chat".equals(event.getIdentifier())) return;
        
        var channelId = event.getOption("discord-channel-id").getAsString();
        var chat = getChats().stream()
            .filter(c -> c.getDiscord().getChannel().equals(channelId))
            .findFirst().orElse(null);
        
        if (chat == null || !chat.isEnabled()) return;
        
        var uuid = Synergy.getDataManager().findUserUUID("discord", event.getOption("discord-user-id").getAsString());
        
        if (uuid == null) {
            Synergy.event("discord-embed")
                .setOption("channel", channelId)
                .setOption("title", Synergy.translate("<lang>you-have-to-link-account</lang>", Translation.getDefaultLanguage()).getStripped())
                .setOption("color", "#fab1a0").send();
            return;
        }
        
        if (Synergy.getBread(uuid).isMuted()) {
            return;
        }
        
        var player = Synergy.getSpigot().getOfflinePlayerByUniqueId(uuid);
        var format = formatMessage(chat.getDiscord(), new Message(event.getOption("message").getAsString()), player);
        
        Bukkit.getOnlinePlayers().stream()
            .filter(p -> chat.getPermission() == null || p.hasPermission(chat.getPermission()))
            .forEach(p -> {
                var b = Synergy.getBread(p.getUniqueId());
                p.sendMessage(Synergy.translate(format, b.getLanguage())
                    .setPlaceholders(b).setGendered(b.getGender()).getColoredComponent(b.getTheme()));
            });
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        var p = event.getPlayer();
        var bread = Synergy.getBread(p.getUniqueId());

        if (bread.isMuted() && matchesCommand(event.getMessage(), "chat-manager.muted-blocked-commands")) {
            long remaining = bread.getMuteRemainingSeconds();
            String remainingText = remaining == -1 ? "permanent" : Utils.formatDuration(remaining);
            String reason = bread.getMuteReason();

            p.sendMessage(LocaleBuilder.of("you-are-muted")
                .placeholder("reason", reason == null || reason.isEmpty() ? "-" : reason)
                .placeholder("remaining", remainingText)
                .build());
            event.setCancelled(true);
            return;
        }

        if (!Synergy.getCooldown(p.getUniqueId()).hasCooldown("command") &&
            Config.getStringList("chat-manager.log-ignored-commands").stream()
                .noneMatch(event.getMessage()::startsWith)) {
            Synergy.getLogger().discord("```[" + Synergy.getServerName() + "] [cmd] " + 
                p.getName() + ": " + event.getMessage() + "```");
        }
    }

    private boolean matchesCommand(String message, String configPath) {
        String cmd = message.split(" ")[0].toLowerCase();
        return Config.getStringList(configPath).stream()
            .map(String::toLowerCase)
            .anyMatch(cmd::equals);
    }
    
    public static String formatMessage(MessageSource source, Message msg, Object player) {
        var format = source.getFormat()
            .replace("%CHAT%", source.getTag())
            .replace("%COLOR%", source.getColor())
            .replace("%DISPLAYNAME%", player instanceof String s ? s : ((OfflinePlayer) player).getName())
            .replace("%MESSAGE%", msg.getMessage());
        
        if (player instanceof OfflinePlayer op) {
            var bread = Synergy.getBread(op.getUniqueId());
            if (Synergy.isDependencyAvailable("PlaceholderAPI")) {
                format = PlaceholderAPI.setPlaceholders(op, Utils.replacePlaceholderOutputs(op, format));
            }
            if (!bread.hasPermission("synergy.colors")) {
                format = format.replace(msg.getMessage(), Utils.stripColorTags(msg.getMessage()));
            }
        } else if (Synergy.isDependencyAvailable("PlaceholderAPI")) {
            format = PlaceholderAPI.setPlaceholders(null, Utils.replacePlaceholderOutputs(null, format));
        }
        
        return format;
    }
    
    private static Set<Chat> getChats() {
        return Config.getConfigurationSection("chat-manager.chats").keySet().stream()
            .map(Chat::new).collect(Collectors.toSet());
    }
    
    private static String getChatBySymbol(String msg) {
        return getChats().stream()
            .filter(c -> c.getSymbol() != null && !c.getSymbol().isEmpty() && msg.startsWith(c.getSymbol()))
            .map(Chat::getName).findFirst()
            .orElse(new Chat("local").isEnabled() ? "local" : "global");
    }

    public static class Message {
        private final String raw;
        private final String processed;

        public Message(String msg) {
            this.raw = msg;
            this.processed = Utils.convertMinecraftColorsToHex(Utils.removeRepetitiveCharacters(Utils.censorBlockedWords(Utils.translateSmiles(msg))));
        }
        
        public String getRaw() { return raw; }
        
        public String getMessage() { 
            var chat = new Chat(getChatBySymbol(processed));
            return processed.length() > 0 && chat.getSymbol() != null && 
                chat.getSymbol().contains(String.valueOf(processed.charAt(0))) 
                ? processed.substring(1) : processed;
        }
        
        public boolean hasChatSymbol() {
            return getChats().stream().anyMatch(c -> c.getSymbol() != null && raw.startsWith(c.getSymbol()));
        }
    }
}