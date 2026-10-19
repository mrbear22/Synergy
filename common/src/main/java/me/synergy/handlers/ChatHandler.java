package me.synergy.handlers;

import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Map.Entry;
import me.synergy.modules.Locales;
import me.synergy.text.Color;
import me.synergy.objects.Locale;
import me.synergy.modules.DataManager;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import me.synergy.anotations.SynergyHandler;
import me.synergy.anotations.SynergyListener;
import me.synergy.brains.Synergy;
import me.synergy.events.SynergyEvent;
import me.synergy.modules.Config;
import me.synergy.modules.Module;
import me.synergy.objects.Chat;
import me.synergy.objects.LocaleBuilder;

public class ChatHandler implements SynergyListener, Module {

    private static volatile boolean chatLocked = false;
    private static volatile int slowmodeSeconds = 2;
    private static volatile boolean enabled = false;

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean isChatLocked() {
        return chatLocked;
    }

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
        boolean apply(UUID sender, Set<UUID> viewers);
    }
    
    public interface MessageSource {
        String getColor();
        String getTag();
        String getFormat();
    }

    @Override public Side side() { return Side.BACKEND; }
    @Override public String toggle() { return "chat-manager.enabled"; }

    @Override
    public void defaults() {
        Locales.addDefault("player.cooldown.active", "<danger>Please wait a few seconds!");
        Locales.addDefault("chat.message.empty", "<danger>Message can't be empty!");
        Locales.addDefault("chat.listeners.missing", "<danger>No one can hear you. Use ! for global chat.");
        Locales.addDefault("chat.mute.active", "<danger>You are muted. Reason: %reason%");
        Locales.addDefault("chat.lock.active", "<danger>Chat is locked.");
        boolean fresh = Config.getConfigurationSection("chat-manager").isEmpty();
        Config.addDefault("chat-manager.enabled", true);
        Config.addDefault("chat-manager.chat-filter.blocked-words", new String[] {"fuck", "bitch"});
        Config.addDefault("chat-manager.chat-filter.ignored-words", new String[] {"чіпідрос", "книга"});
        Config.addDefault("chat-manager.chat-filter.blocked-words-tolerance-percentage", 38.5);
        Config.addDefault("chat-manager.use-interactive-tags", false);
        Config.addDefault("chat-manager.warn-if-nobody-in-chat", true);
        Config.addDefault("chat-manager.muted-blocked-commands", new String[] {"/msg", "/tell", "/w", "/r", "/reply"});
        Config.addDefault("chat-manager.log-ignored-commands", new String[] {"/reg", "/register", "/l", "/login", "/unregister", "/changepassword"});
        Config.addDefault("chat-manager.chats.global.enabled", true);
        Config.addDefault("chat-manager.chats.global.color", "<#dedee0>");
        Config.addDefault("chat-manager.chats.global.tag", "[G]");
        Config.addDefault("chat-manager.chats.global.symbol", "!");
        Config.addDefault("chat-manager.chats.local.enabled", true);
        Config.addDefault("chat-manager.chats.local.radius", 500);
        Config.addDefault("chat-manager.chats.local.color", "<#deceb4>");
        Config.addDefault("chat-manager.chats.local.tag", "[L]");
        Config.addDefault("chat-manager.chats.twitch.enabled", false);
        Config.addDefault("chat-manager.chats.twitch.color", "<#c9ffcd>");
        Config.addDefault("chat-manager.chats.twitch.tag", "[Twitch]");
        Config.addDefault("chat-manager.chats.twitch.symbol", "none");
        Config.addDefault("chat-manager.chats.tiktok.enabled", false);
        Config.addDefault("chat-manager.chats.tiktok.color", "<#c9ffcd>");
        Config.addDefault("chat-manager.chats.tiktok.tag", "[TikTok]");
        Config.addDefault("chat-manager.chats.tiktok.symbol", "none");
        if (fresh) {
            Config.addDefault("chat-manager.chats.global.discord.color", "<#dedee0>");
            Config.addDefault("chat-manager.chats.global.discord.tag", "[Discord]");
            Config.addDefault("chat-manager.chats.global.discord.channel", "00000000000000000");
            Config.addDefault("chat-manager.chats.admin.enabled", true);
            Config.addDefault("chat-manager.chats.admin.color", "<#ffb4a1>");
            Config.addDefault("chat-manager.chats.admin.tag", "[A]");
            Config.addDefault("chat-manager.chats.admin.symbol", "\\");
            Config.addDefault("chat-manager.chats.admin.permission", "synergy.chat.admin");
            Config.addDefault("chat-manager.chats.admin.discord.color", "<#ffb4a1>");
            Config.addDefault("chat-manager.chats.admin.discord.tag", "[Discord]");
            Config.addDefault("chat-manager.chats.admin.discord.channel", "00000000000000000");
        }
        if (Config.getConfigurationSection("chat-manager.discord-embed").isEmpty()) {
            Config.addDefault("chat-manager.discord-embed.author", "%PLAYER%");
            Config.addDefault("chat-manager.discord-embed.author-icon", "");
            Config.addDefault("chat-manager.discord-embed.description", "%MESSAGE%");
            Config.addDefault("chat-manager.discord-embed.footer", "%CHAT% · %SERVER%");
            Config.addDefault("chat-manager.discord-embed.color", "%COLOR%");
            Config.addDefault("chat-manager.discord-embed.timestamp", true);
        }
        if (Config.getConfigurationSection("placeholder-output-replacements").isEmpty()) {
            Config.addDefault("placeholder-output-replacements.%faction_name%.%faction_name%", "[%faction_name%] ");
            Config.addDefault("placeholder-output-replacements.%faction_name%.%none%", "");
        }
        Config.addDefault("chat-manager.format", "%COLOR%%CHAT% %DISPLAYNAME%%COLOR%: %MESSAGE%");
        if (Config.getConfigurationSection("chat-manager.custom-emojis").isEmpty()) {
            Config.addDefault("chat-manager.custom-emojis.<3", "❤");
            Config.addDefault("chat-manager.custom-emojis.:flip:", "(╯°益°）╯︵ ┻━┻");
            Config.addDefault("chat-manager.custom-emojis.:v:", "✔");
            Config.addDefault("chat-manager.custom-emojis.:x:", "✘");
            Config.addDefault("chat-manager.custom-emojis.(c)", "©");
            Config.addDefault("chat-manager.custom-emojis.:hi:", "(´• ω •`)ﾉ");
            Config.addDefault("chat-manager.custom-emojis.:love:", "╰(❤ω❤)╯");
            Config.addDefault("chat-manager.custom-emojis.:cry:", "o(╥﹏╥)o");
            Config.addDefault("chat-manager.custom-emojis.(r)", "®");
            Config.addDefault("chat-manager.custom-emojis.:hugs:", "⊂(￣▽￣)⊃");
            Config.addDefault("chat-manager.custom-emojis.:hid:", "┬┴┤･ω･)ﾉ");
        }

    }

    @Override
    public void refresh() {
        Chat.registerAll();
    }

    @Override
    public void start() {
        Chat.registerAll();
        Synergy.getEventManager().registerEvents(this);
        enabled = true;
    }

    public static String handle(UUID uuid, String name, String raw, Set<UUID> viewers, boolean nativeRender) {
        var bread = Synergy.getBread(uuid);

        if (!bread.isAuthenticated()) {
            return null;
        }

        if (bread.isMuted()) {
            tellMuted(bread);
            return null;
        }

        if (chatLocked && !bread.hasPermission("synergy.chat.bypass")) {
            tell(uuid, "<locale:chat.lock.active>");
            return null;
        }
        
        var msg = new Message(raw);
        var chat = new Chat(Optional.ofNullable(msg.hasChatSymbol() ? getChatBySymbol(msg.getRaw()) : null)
            .filter(n -> new Chat(n).isEnabled())
            .orElseGet(() -> Optional.ofNullable(bread.getData("chat"))
                .filter(d -> d.isSet() && new Chat(d.getAsString()).isEnabled())
                .map(d -> d.getAsString())
                .orElse(new Chat("local").isEnabled() ? "local" : "global")));
        
        if (msg.getMessage().isEmpty()) {
            tell(uuid, "<locale:chat.message.empty>");
            return null;
        }
        
        if (bread.cooldown("chat", slowmodeSeconds)) {
            tell(uuid, "<locale:player.cooldown.active>");
            return null;
        }
        
        if (!Chat.applyFilter(chat.getName(), uuid, viewers)) {
            return null;
        }
        
        if (chat.getPermission() != null) {
            viewers.removeIf(v -> !Synergy.platform().hasPermission(v, chat.getPermission()));
        }
        
        var format = formatMessage(chat, msg, uuid, name);
        
        if ("global".equals(chat.getName())) {
            Synergy.event("global-chat")
                .setPlayerUniqueId(uuid)
                .setOption("format", format)
                .setOption("message", msg.getMessage())
                .setOption("tag", chat.getTag())
                .send();
        }
        
        boolean global = "global".equals(chat.getName());
        boolean deliverHere = global || !nativeRender || Config.getBoolean("chat-manager.use-interactive-tags");
        if (deliverHere) {
            if (!global) {
                viewers.forEach(v -> MessageHandler.send(v, format, Config.getBoolean("chat-manager.use-interactive-tags"), true));
            }
            Synergy.getLogger().info("[CHAT] [" + chat.getTag() + "] " + name + ": " + msg.getMessage());
        }

        Synergy.getLogger().discord("```[" + Synergy.getServerName() + "] [" + chat.getTag() + "] " + 
            name + ": " + msg.getMessage() + "```");
        
        if (chat.getDiscord().getChannel().length() == 19 && 
            (chat.getPermission() == null || bread.hasPermission(chat.getPermission()))) {
            var embed = Synergy.event("discord-embed").toProxy().setPlayerUniqueId(uuid)
                .setOption("channel", chat.getDiscord().getChannel())
                .setOption("player", name);
            Map<String, Object> template = new LinkedHashMap<>(Config.getConfigurationSection("chat-manager.discord-embed"));
            template.putAll(Config.getConfigurationSection("chat-manager.chats." + chat.getName() + ".discord.embed"));
            template.forEach((key, value) -> {
                String text = Synergy.platform().placeholders(uuid, String.valueOf(value)
                    .replace("%PLAYER%", name)
                    .replace("%MESSAGE%", msg.getMessage())
                    .replace("%SERVER%", Synergy.getServerName())
                    .replace("%CHAT%", chat.getTag())
                    .replace("%COLOR%", chat.getColor().substring(1, chat.getColor().length() - 1))
                    .replace("%UUID%", uuid.toString()));
                if (!text.isBlank()) embed.setOption(key, text);
            });
            embed.send();
        }
        
        if (Config.getBoolean("chat-manager.warn-if-nobody-in-chat") && 
            viewers.size() < 2 && !"global".equals(chat.getName())) {
            tell(uuid, "<locale:chat.listeners.missing>");
        }
        return deliverHere ? null : format;
    }

    public static net.kyori.adventure.text.Component render(String format, UUID viewer) {
        var b = Synergy.getBread(viewer);
        return Synergy.translate(format, b.getLanguage()).setPlaceholders(b).setGendered(b.getGender()).getColoredComponent(b.getTheme());
    }

    public static String formatChat(String name, String message, String author) {
        var chat = new Chat(name);
        return chat.isEnabled() ? formatMessage(chat, new Message(message), null, author) : null;
    }

    private static void tell(UUID uuid, String text) {
        MessageHandler.send(uuid, text, false, true);
    }

    private static void tellMuted(me.synergy.objects.BreadMaker bread) {
        long remaining = bread.getMuteRemainingSeconds();
        String reason = bread.getMuteReason();
        tell(bread.getUniqueId(), LocaleBuilder.of("chat.mute.active")
            .placeholder("reason", reason == null || reason.isEmpty() ? "-" : reason)
            .placeholder("remaining", remaining == -1 ? "permanent" : formatDuration(remaining))
            .build());
    }

    @SynergyHandler("chat-lock-sync")
    public void onChatLock(SynergyEvent event) {
        chatLocked = Boolean.parseBoolean(event.getOption("locked").getAsString());
    }
    
    @SynergyHandler("twitch-chat")
    public void onTwitchMessage(SynergyEvent event) {

        var chat = new Chat("twitch");
        if (!chat.isEnabled()) return;

        UUID uuid = DataManager.findUserUUID("twitch-username",
            event.getOption("twitch-channel").getAsString());
        if (uuid == null) return;

        if (!Synergy.platform().isOnline(uuid)) return;

        Synergy.getBread(uuid).sendMessage(formatMessage(chat,
            new Message(event.getOption("message").getAsString()),
            null, event.getOption("player").getAsString()));

        Synergy.getLogger().info("[" + event.getOption("twitch-channel").getAsString() + "] " +
            event.getOption("player").getAsString() + ": " + event.getOption("message").getAsString());
    }
    
    @SynergyHandler("discord-chat")
    public void onDiscordMessage(SynergyEvent event) {
        var channelId = event.getOption("discord-channel-id").getAsString();
        var chat = getChats().stream()
            .filter(c -> c.getDiscord().getChannel().equals(channelId))
            .findFirst().orElse(null);
        
        if (chat == null || !chat.isEnabled()) return;
        
        var uuid = DataManager.findUserUUID("discord", event.getOption("discord-user-id").getAsString());
        
        if (uuid == null) {
            Synergy.event("discord-embed").toProxy()
                .setOption("channel", channelId)
                .setOption("title", LocaleBuilder.of("discord.link.required").stripped())
                .setOption("color", "#fab1a0").send();
            return;
        }
        
        var bread = Synergy.getBread(uuid);
        if (bread.isMuted()) {
            return;
        }
        
        var format = formatMessage(chat.getDiscord(), new Message(event.getOption("message").getAsString()), uuid, bread.getName());
        
        Synergy.platform().online().stream()
            .filter(p -> chat.getPermission() == null || Synergy.platform().hasPermission(p, chat.getPermission()))
            .forEach(p -> Synergy.platform().audience(p).sendMessage(render(format, p)));
    }

    public static boolean blockCommand(UUID uuid, String name, String message) {
        if (!enabled) return false;
        var bread = Synergy.getBread(uuid);

        if (bread.isMuted() && matchesCommand(message, "chat-manager.muted-blocked-commands")) {
            tellMuted(bread);
            return true;
        }

        String lower = message.toLowerCase();
        String hidden = Stream.concat(Stream.of("/twitch link", "/monobank link"), Config.getStringList("chat-manager.log-ignored-commands").stream())
            .map(String::toLowerCase).filter(c -> lower.equals(c) || lower.startsWith(c + " ")).findFirst().orElse(null);
        Synergy.getLogger().discord("```[" + Synergy.getServerName() + "] [cmd] " + name + ": " + (hidden == null ? message : hidden + " ***") + "```");
        return false;
    }

    private static boolean matchesCommand(String message, String configPath) {
        String cmd = message.split(" ")[0].toLowerCase();
        return Config.getStringList(configPath).stream()
            .map(String::toLowerCase)
            .anyMatch(cmd::equals);
    }
    
    public static String formatMessage(MessageSource source, Message msg, UUID uuid, String name) {
        var format = source.getFormat()
            .replace("%CHAT%", source.getTag())
            .replace("%COLOR%", source.getColor())
            .replace("%DISPLAYNAME%", name)
            .replace("%MESSAGE%", msg.getMessage());
        
        format = Synergy.platform().placeholders(uuid, Locale.replacePlaceholderOutputs(uuid, format));
        if (uuid != null && !Synergy.getBread(uuid).hasPermission("synergy.colors")) {
            format = format.replace(msg.getMessage(), Color.stripColorTags(msg.getMessage()));
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
            this.processed = Color.convertMinecraftColorsToHex(removeRepetitiveCharacters(censorBlockedWords(translateSmiles(msg))));
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

    public static void addBlockedWord(String word) {
    	editWords("chat-manager.chat-filter.blocked-words", words -> words.add(word));
    }

    public static void removeBlockedWord(String word) {
    	editWords("chat-manager.chat-filter.blocked-words", words -> words.remove(word));
    }

    public static void addIgnoredWord(String word) {
    	editWords("chat-manager.chat-filter.ignored-words", words -> words.add(word));
    }

    public static void removeIgnoredWord(String word) {
    	editWords("chat-manager.chat-filter.ignored-words", words -> words.remove(word));
    }

    public static List<String> getBlockedWords() {
        return words("chat-manager.chat-filter.blocked-words");
    }

    public static List<String> getIgnoredWords() {
        return words("chat-manager.chat-filter.ignored-words");
    }

    private static List<String> words(String key) {
        List<String> words = Config.getStringList(key);
        return words == null ? new ArrayList<>() : new ArrayList<>(words);
    }

    private static void editWords(String key, java.util.function.Consumer<List<String>> edit) {
        List<String> words = words(key);
        edit.accept(words);
        Config.set(key, words);
        Config.save();
    }

    private static String censorBlockedWords(String sentence) {
        double tolerance = Config.getDouble("chat-manager.chat-filter.blocked-words-tolerance-percentage");

        for (String blockedWord : getBlockedWords()) {
            if (blockedWord.length() == 1) {
                sentence = sentence.replace(blockedWord, "*");
                continue;
            }
            String blockedWordLower = blockedWord.toLowerCase();
            String match = "";
            int start = 0, end = 0;

            String sentenceWithoutColors = removeColorCodes(sentence);
            
            for (int i = 0; i < sentenceWithoutColors.length(); i++) {
                if (Character.isAlphabetic(sentenceWithoutColors.charAt(i))) {
                    char currentChar = Character.toLowerCase(sentenceWithoutColors.charAt(i));
                    
                    boolean isFirstLetterMatches = blockedWordLower.charAt(0) == currentChar;
                    boolean isWordStartsWithBadWord = blockedWordLower.startsWith((match + currentChar).toLowerCase());
                    boolean isWordWithoutDuplicatesStartsWithBadWord = blockedWordLower.startsWith(removeConsecutiveDuplicates((match + currentChar).toLowerCase()));

                    if (isFirstLetterMatches || isWordStartsWithBadWord || isWordWithoutDuplicatesStartsWithBadWord) {
                        if (match.isEmpty()) {
                            start = i;
                        }
                        match += sentenceWithoutColors.charAt(i);
                        end = i;
                    } else {
                        match = "";
                    }

                    String matchLower = match.toLowerCase();
                    String matchWithoutDuplicatesLower = removeConsecutiveDuplicates(match).toLowerCase();
                    
                    if (blockedWordLower.equals(matchLower) || blockedWordLower.equals(matchWithoutDuplicatesLower)) {
                        String word = findWordInRange(sentenceWithoutColors, start, end);

                        boolean isIgnored = false;
                        for (String ignoredWord : getIgnoredWords()) {
                            if (ignoredWord.toLowerCase().equals(word.toLowerCase())) {
                                isIgnored = true;
                                break;
                            }
                        }
                        
                        if (isIgnored) {
                            continue;
                        }

                        double percentage = (double) match.length() / (double) word.length() * 100;
                        if (tolerance < percentage || !word.toLowerCase().contains(blockedWordLower)) {
                            sentence = censorPartOfSentence(sentence, start, end);
                        }
                    }
                }
            }
        }
        return sentence;
    }

    private static String findWordInRange(String sentence, int start, int end) {
        String[] words = sentence.split("\\s+");

        for (String word : words) {
            int wordStart = sentence.indexOf(word, start - 5 >= 0 ? start - 5 : 0);
            int wordEnd = wordStart + word.length() - 1;

            if (start >= wordStart && end <= wordEnd) {
                return word;
            }
        }
        return sentence.substring(start, Math.min(end + 1, sentence.length()));
    }

    private static String censorPartOfSentence(String sentence, int start, int end) {
        if (end - start < 2) {
            return sentence;
        }

        char[] charArray = sentence.toCharArray();
        for (int i = start + 1; i < end; i++) {
            if (Character.isAlphabetic(sentence.charAt(i))) {
                charArray[i] = '*';
            }
        }
        return new String(charArray);
    }

	private static String translateSmiles(String string) {
		for (Entry<String, Object> e : Config.getConfigurationSection("chat-manager.custom-emojis").entrySet()) {
			string = string.replace(e.getKey(), Config.getString("chat-manager.custom-emojis."+e.getKey()));
		}
		return string;
	}

    private static String removeColorCodes(String input) {
        String regex = "&[0-9a-f]";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);
        String result = matcher.replaceAll("**");
        return result;
    }

    private static String removeConsecutiveDuplicates(String text) {
        StringBuilder result = new StringBuilder();
        char prevChar = '\0';
        for (char currentChar : text.toCharArray()) {
            if (currentChar != prevChar) {
                result.append(currentChar);
                prevChar = currentChar;
            }
        }
        return result.toString();
    }

    private static String removeRepetitiveCharacters(String sentence) {
        StringBuilder sb = new StringBuilder();
        char[] chars = sentence.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char currentChar = chars[i];
            int count = 1;
            while (i + 1 < chars.length && Character.toLowerCase(chars[i + 1]) == Character.toLowerCase(currentChar)) {
                count++;
                i++;
            }
            int repeatCount = Math.min(count, 5);
            for (int j = 0; j < repeatCount; j++) {
                sb.append(currentChar);
            }
        }
        return sb.toString();
    }

    public static String formatDuration(long totalSeconds) {
        if (totalSeconds <= 0) return "0s";

        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (days == 0 && hours == 0) sb.append(seconds).append("s");

        return sb.toString().trim();
    }
}
