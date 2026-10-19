package me.synergy.commands;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import me.synergy.brains.Synergy;
import me.synergy.modules.Locales;
import me.synergy.modules.Tasks;
import me.synergy.objects.LocaleBuilder;

public class DelayedCommand extends Command {

    public DelayedCommand() {
        super("delayed <seconds> <command...>");
    }

    @Override
    protected boolean console(String path) {
        return true;
    }

    @Override
    protected List<String> values(Sender sender, String name, String[] input) {
        return name.equals("seconds") ? List.of("1", "5", "10", "30", "60") : Synergy.platform().complete(sender.uuid(), String.join(" ", Arrays.copyOfRange(input, 2, input.length)));
    }

    @Override
    public void defaults() {
        Locales.addDefault("command.delayed", "run a command after a delay");
        Locales.addDefault("delayed.command.scheduled", "<success>Command <primary>/%command% <success>will run in <primary>%seconds% <success>s.");
    }

    @Override
    protected void execute(Sender sender, String path, String[] args) {
        if (!args[0].matches("\\d+")) {
            sender.send(LocaleBuilder.of("input.number.invalid").placeholder("input", args[0]));
            return;
        }
        String command = args[1].startsWith("/") ? args[1].substring(1) : args[1];
        Tasks.later(() -> sender.run(command), Duration.ofSeconds(Integer.parseInt(args[0])));
        sender.send(LocaleBuilder.of("delayed.command.scheduled").placeholder("command", command).placeholder("seconds", args[0]));
    }
}
