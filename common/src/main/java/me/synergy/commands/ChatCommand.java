package me.synergy.commands;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import me.synergy.brains.Synergy;
import me.synergy.handlers.ChatHandler;
import me.synergy.modules.Config;
import me.synergy.modules.Locales;
import me.synergy.objects.Chat;
import me.synergy.objects.LocaleBuilder;
import net.kyori.adventure.text.Component;

public class ChatCommand extends Command {

    public ChatCommand() {
        super("chat <chat>", "chat lock", "chat unlock", "chat clear", "chat slowmode <seconds>",
            "chatfilter block <word>", "chatfilter ignore <word>", "chatfilter remove <word>", "colors", "emojis");
    }

    @Override public String toggle() { return "chat-manager.enabled"; }

    @Override
    protected boolean console(String path) {
        return !path.equals("chat") && !path.equals("colors");
    }

    @Override
    protected List<String> values(Sender sender, String name, String[] input) {
        return switch (name) {
            case "chat" -> chats(sender);
            case "seconds" -> List.of("0", "5", "10", "30");
            case "word" -> input[1].equalsIgnoreCase("remove") ? Stream.concat(ChatHandler.getBlockedWords().stream(), ChatHandler.getIgnoredWords().stream()).toList() : List.of();
            default -> List.of();
        };
    }

    @Override
    public void defaults() {
        Locales.addDefault("command.chat", "switch chat");
        Locales.addDefault("command.chat.lock", "lock chat for everyone");
        Locales.addDefault("command.chat.unlock", "unlock chat");
        Locales.addDefault("command.chat.clear", "clear chat");
        Locales.addDefault("command.chat.slowmode", "set chat cooldown");
        Locales.addDefault("command.chatfilter.block", "block a word");
        Locales.addDefault("command.chatfilter.ignore", "ignore a word in the filter");
        Locales.addDefault("command.chatfilter.remove", "remove a word from the filter");
        Locales.addDefault("command.colors", "show available colors");
        Locales.addDefault("command.emojis", "show available emojis");
        Locales.addDefault("chat.channel.selected", "<success>Chat selected: <primary>%chat%<sound:'entity.ocelot.death'>");
        Locales.addDefault("chat.lock.on", "<danger>Chat has been locked by %staff%.");
        Locales.addDefault("chat.lock.off", "<success>Chat has been unlocked by %staff%.");
        Locales.addDefault("chat.slowmode.set", "<success>Chat cooldown: <primary>%seconds% <success>s.");
        Locales.addDefault("chat.filter.blocked", "<success>Word <primary>%word% <success>is blocked.");
        Locales.addDefault("chat.filter.ignored", "<success>Word <primary>%word% <success>is ignored by the filter.");
        Locales.addDefault("chat.filter.removed", "<success>Word <primary>%word% <success>removed from the filter.");
    }

    @Override
    protected void execute(Sender sender, String path, String[] args) {
        switch (path) {
            case "chat" -> {
                if (!chats(sender).contains(args[0].toLowerCase())) {
                    help(sender);
                    return;
                }
                sender.bread().setData("chat", args[0].toLowerCase());
                sender.send(LocaleBuilder.of("chat.channel.selected").placeholder("chat", args[0].toLowerCase()));
            }
            case "chat.lock", "chat.unlock" -> {
                boolean lock = path.equals("chat.lock");
                ChatHandler.setChatLocked(lock);
                sender.send(LocaleBuilder.of(lock ? "chat.lock.on" : "chat.lock.off").placeholder("staff", sender.name()));
                Synergy.getLogger().discord("```[MOD] " + sender.name() + (lock ? " locked" : " unlocked") + " chat```");
            }
            case "chat.clear" -> {
                Synergy.platform().online().forEach(uuid -> {
                    var audience = Synergy.platform().audience(uuid);
                    for (int i = 0; i < 100; i++) audience.sendMessage(Component.empty());
                });
                Synergy.getLogger().discord("```[MOD] " + sender.name() + " cleared chat```");
            }
            case "chat.slowmode.set" -> {
                if (!args[0].matches("\\d+")) {
                    sender.send(LocaleBuilder.of("input.number.invalid").placeholder("input", args[0]));
                    return;
                }
                ChatHandler.setSlowmode(Integer.parseInt(args[0]));
                sender.send(LocaleBuilder.of("chat.slowmode.set").placeholder("seconds", args[0]));
            }
            case "chatfilter.block" -> {
                ChatHandler.addBlockedWord(args[0].toLowerCase());
                sender.send(LocaleBuilder.of("chat.filter.blocked").placeholder("word", args[0].toLowerCase()));
            }
            case "chatfilter.ignore" -> {
                ChatHandler.addIgnoredWord(args[0].toLowerCase());
                sender.send(LocaleBuilder.of("chat.filter.ignored").placeholder("word", args[0].toLowerCase()));
            }
            case "chatfilter.remove" -> {
                ChatHandler.removeBlockedWord(args[0].toLowerCase());
                ChatHandler.removeIgnoredWord(args[0].toLowerCase());
                sender.send(LocaleBuilder.of("chat.filter.removed").placeholder("word", args[0].toLowerCase()));
            }
            case "colors" -> sender.book("Colors", String.join("\n", Config.getConfigurationSection("chat-manager.custom-color-tags").keySet().stream()
                .map(key -> Config.getString("chat-manager.custom-color-tags." + key) + key.replace("&", "$")).toList()));
            case "emojis" -> emojis(sender);
        }
    }

    private static void emojis(Sender sender) {
        Set<String> keys = Config.getConfigurationSection("chat-manager.custom-emojis").keySet();
        int width = keys.stream().mapToInt(e -> e.length() + Config.getString("chat-manager.custom-emojis." + e).length() + 3).max().orElse(0);
        StringBuilder line = new StringBuilder();
        int count = 0;
        for (String e : keys) {
            String emoji = Config.getString("chat-manager.custom-emojis." + e);
            line.append(String.format("<primary>%s - <secondary>%s%" + Math.max(1, width - e.length() - emoji.length()) + "s", e, emoji, ""));
            if (++count % 2 == 0) {
                sender.send(line.toString());
                line.setLength(0);
            }
        }
        if (line.length() > 0) sender.send(line.toString());
    }

    private static List<String> chats(Sender sender) {
        return Config.getConfigurationSection("chat-manager.chats").keySet().stream().map(Chat::new)
            .filter(chat -> chat.isEnabled() && (chat.getPermission() == null || sender.hasPermission(chat.getPermission())))
            .map(Chat::getName).toList();
    }
}
