package me.synergy.commands;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import me.synergy.brains.Synergy;
import me.synergy.modules.Config;
import me.synergy.modules.Locales;
import me.synergy.objects.BreadMaker;
import me.synergy.objects.LocaleBuilder;

public class TempMuteCommand implements CommandExecutor, TabCompleter {

    public void initialize() {
        if (!Config.getBoolean("chat-manager.enabled")) return;

        Synergy.getSpigot().getCommand("tempmute").setExecutor(this);
        Synergy.getSpigot().getCommand("tempmute").setTabCompleter(this);
        Synergy.getSpigot().getCommand("unmute").setExecutor(this);
        Synergy.getSpigot().getCommand("unmute").setTabCompleter(this);

        Locales.addDefault("command_description_tempmute", "en", "Temporarily mute a player");
        Locales.addDefault("command_usage_tempmute", "en", new String[] {
            "<danger>Usage: /tempmute <player> <amount><s/m/h/d/w|perm> <reason>",
            "",
            "<secondary>Examples:",
            "<primary>  /tempmute Steve 10m spam",
            "<primary>  /tempmute Steve 1d toxicity",
            "<primary>  /tempmute Steve perm insults"
        });
        Locales.addDefault("command_description_unmute", "en", "Unmute a player");
        Locales.addDefault("command_usage_unmute", "en", "<danger>Usage: /unmute <player>");

        Locales.addDefault("player-not-found", "en", "<danger>Player %name% not found.");
        Locales.addDefault("invalid-duration", "en", "<danger>Invalid duration. Use e.g. 10m, 2h, 1d or perm.");
        Locales.addDefault("mute-cancelled", "en", "<danger>Mute action was cancelled by another plugin.");

        Locales.addDefault("player-muted", "en", "<success>%target% has been muted.");
        Locales.addDefault("player-unmuted", "en", "<success>%target% has been unmuted.");
        Locales.addDefault("you-were-muted", "en", "<danger>You have been muted. Reason: %reason%");
        Locales.addDefault("you-were-unmuted", "en", "<success>You have been unmuted.");

        Locales.addDefault("player-muted-broadcast", "en",
            "<danger>%target% has been muted by %staff% for %duration%. Reason: %reason%");
        Locales.addDefault("player-unmuted-broadcast", "en",
            "<success>%target% has been unmuted by %staff%.");

        Locales.addDefault("discord-mute-title", "en", "Player muted");
        Locales.addDefault("discord-unmute-title", "en", "Player unmuted");
        Locales.addDefault("discord-mute-description", "en", "Duration: %duration%\nReason: %reason%\nModerator: %staff%");
        Locales.addDefault("discord-unmute-description", "en", "Moderator: %staff%");

        Synergy.getLogger().info(String.valueOf(getClass().getSimpleName()) + " module has been initialized!");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("synergy." + command.getLabel().toLowerCase())) return null;

        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
        }
        if (command.getLabel().equalsIgnoreCase("tempmute") && args.length == 2) {
            return List.of("10m", "30m", "1h", "6h", "1d", "7d", "perm");
        }
        return null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("synergy." + label.toLowerCase())) {
            sender.sendMessage("<locale:no-permission>");
            return true;
        }

        if (label.equalsIgnoreCase("unmute")) {
            if (args.length < 1) {
                sender.sendMessage("<locale:command-usage> /unmute <player>");
                return true;
            }
            UUID uuid = resolveUUID(args[0]);
            if (uuid == null) {
                sender.sendMessage(LocaleBuilder.of("player-not-found").placeholder("name", args[0]).build());
                return true;
            }

            BreadMaker bread = Synergy.getBread(uuid);
            String targetName = displayName(bread, args[0]);

            if (!bread.unmute()) {
                sender.sendMessage("<locale:mute-cancelled>");
                return true;
            }

            sender.sendMessage(LocaleBuilder.of("player-unmuted").placeholder("target", targetName).build());
            bread.sendMessage("<locale:you-were-unmuted>");

            broadcastChat(LocaleBuilder.of("player-unmuted-broadcast")
                .placeholder("target", targetName)
                .placeholder("staff", sender.getName())
                .build());

            sendDiscordEmbed(
                LocaleBuilder.of("discord-unmute-title").build(),
                LocaleBuilder.of("discord-unmute-description").placeholder("staff", sender.getName()).build(),
                targetName, "#2ecc71");

            Synergy.getLogger().discord("```[MOD] " + sender.getName() + " unmuted " + targetName + "```");
            return true;
        }

        if (label.equalsIgnoreCase("tempmute")) {
            if (args.length < 3) {
                sender.sendMessage("<locale:command-usage> /tempmute <player> <duration> <reason>");
                return true;
            }

            UUID uuid = resolveUUID(args[0]);
            if (uuid == null) {
                sender.sendMessage(LocaleBuilder.of("player-not-found").placeholder("name", args[0]).build());
                return true;
            }

            Long duration = parseDuration(args[1]);
            if (duration == null) {
                sender.sendMessage("<locale:invalid-duration>");
                return true;
            }

            String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            BreadMaker bread = Synergy.getBread(uuid);
            String targetName = displayName(bread, args[0]);

            if (!bread.mute(duration, reason, sender.getName())) {
                sender.sendMessage("<locale:mute-cancelled>");
                return true;
            }

            String durationLabel = duration == -1L ? "permanent" : args[1];

            sender.sendMessage(LocaleBuilder.of("player-muted").placeholder("target", targetName).build());
            bread.sendMessage(LocaleBuilder.of("you-were-muted").placeholder("reason", reason).build());

            broadcastChat(LocaleBuilder.of("player-muted-broadcast")
                .placeholder("target", targetName)
                .placeholder("staff", sender.getName())
                .placeholder("duration", durationLabel)
                .placeholder("reason", reason)
                .build());

            sendDiscordEmbed(
                LocaleBuilder.of("discord-mute-title").build(),
                LocaleBuilder.of("discord-mute-description")
                    .placeholder("duration", durationLabel)
                    .placeholder("reason", reason)
                    .placeholder("staff", sender.getName())
                    .build(),
                targetName, "#e74c3c");

            Synergy.getLogger().discord("```[MOD] " + sender.getName() + " muted " + targetName +
                " for " + args[1] + " (" + reason + ")```");
            return true;
        }

        return true;
    }

    /** Мережевий бродкаст у чат (усiм гравцям на всіх серверах, через існуючий "broadcast" event). */
    private void broadcastChat(String localeMessage) {
        Synergy.event("broadcast").setOption("message", localeMessage).send();
    }

    /** Без "channel" піде в дефолтний broadcast-канал (discord.channels.broadcast). Title/description — locale-теги, перекладаються всередині createEmbed(). */
    private void sendDiscordEmbed(String title, String description, String targetName, String color) {
        Synergy.event("discord-embed")
            .setOption("author", targetName)
            .setOption("title", title)
            .setOption("description", description)
            .setOption("color", color)
            .send();
    }

    private String displayName(BreadMaker bread, String fallback) {
        return bread.getName() != null ? bread.getName() : fallback;
    }

    private UUID resolveUUID(String name) {
        Player online = Bukkit.getPlayer(name);
        if (online != null) return online.getUniqueId();
        try {
            return Synergy.getDataManager().findUserUUID("name", name);
        } catch (SQLException e) {
            return null;
        }
    }

    private Long parseDuration(String input) {
        if (input.equalsIgnoreCase("perm") || input.equalsIgnoreCase("permanent")) {
            return -1L;
        }
        if (!input.matches("^\\d+[smhdw]$")) {
            return null;
        }

        long amount = Long.parseLong(input.substring(0, input.length() - 1));
        long multiplier = switch (input.charAt(input.length() - 1)) {
            case 's' -> 1000L;
            case 'm' -> 60_000L;
            case 'h' -> 3_600_000L;
            case 'd' -> 86_400_000L;
            case 'w' -> 604_800_000L;
            default -> 0L;
        };
        return amount * multiplier;
    }
}