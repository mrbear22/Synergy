package me.synergy.commands;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.command.TabCompleter;
import me.synergy.brains.Synergy;
import me.synergy.modules.Locales;
import me.synergy.objects.LocaleBuilder;

public class DelayedCommand implements CommandExecutor, TabCompleter {

	public void initialize() {
	    Synergy.getSpigot().getCommand("delayed").setExecutor(this);
	    Synergy.getSpigot().getCommand("delayed").setTabCompleter(this);
	    Locales.addDefault("command_description_delayed", "en", "Execute a command after a delay");
	    Locales.addDefault("command_usage_delayed", "en", new String[] {
	        "<danger>Usage: /delayed <seconds> <command>",
	        "",
	        "<secondary>Arguments:",
	        "<primary>  seconds <secondary>- Amount of seconds to wait",
	        "<primary>  command <secondary>- Command to execute after the delay"
	    });
	    Locales.addDefault("delayed-scheduled", "en", "<success>Command <primary>/%command% <success>will be executed in <primary>%seconds% <success>second(s).");
	    Locales.addDefault("delayed-invalid-seconds", "en", "<danger>Invalid number of seconds: <primary>%input%");
	}

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("synergy.delayed")) {
            sender.sendMessage(LocaleBuilder.of("no-permission").build());
            return false;
        }

        if (args.length < 2) {
            sender.sendMessage(LocaleBuilder.of("command_usage_delayed").build());
            return false;
        }

        int seconds;
        try {
            seconds = Integer.parseInt(args[0]);
            if (seconds < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sender.sendMessage(LocaleBuilder.of("delayed-invalid-seconds").placeholder("input", args[0]).build());
            return false;
        }

        String command = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        if (command.startsWith("/")) command = command.substring(1);

        final String finalCommand = command;

        Bukkit.getScheduler().runTaskLater(Synergy.getSpigot(), () -> {
            Bukkit.dispatchCommand(sender, finalCommand);
        }, seconds * 20L);

        sender.sendMessage(LocaleBuilder.of("delayed-scheduled")
        	    .placeholder("command", finalCommand)
        	    .placeholder("seconds", String.valueOf(seconds))
        	    .build());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("synergy.delayed")) return new ArrayList<>();

        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>(Arrays.asList("1", "5", "10", "30", "60"));
            suggestions.removeIf(s -> !s.startsWith(args[0]));
            return suggestions;
        }

        if (args.length >= 2) {
            String fakeInput = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            if (fakeInput.startsWith("/")) fakeInput = fakeInput.substring(1);

            try {
                Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
                commandMapField.setAccessible(true);
                SimpleCommandMap commandMap = (SimpleCommandMap) commandMapField.get(Bukkit.getServer());

                List<String> result = commandMap.tabComplete(sender, "/" + fakeInput);
                if (result == null) return new ArrayList<>();

                List<String> completions = new ArrayList<>();
                for (String s : result) {
                    completions.add(s.startsWith("/") ? s.substring(1) : s);
                }
                return completions;
            } catch (NoSuchFieldException | IllegalAccessException e) {
                return new ArrayList<>();
            }
        }

        return new ArrayList<>();
    }
}