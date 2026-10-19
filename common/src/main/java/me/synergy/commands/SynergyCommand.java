package me.synergy.commands;

import java.util.ArrayList;
import java.util.List;

import me.synergy.brains.Synergy;
import me.synergy.integrations.DeepL;
import me.synergy.modules.Config;
import me.synergy.modules.Locales;
import me.synergy.modules.Messaging;
import me.synergy.modules.Tasks;
import me.synergy.modules.Timings;
import me.synergy.objects.LocaleBuilder;
import me.synergy.text.Translation;

public class SynergyCommand extends Command {

    public SynergyCommand() {
        super("synergy reload", "synergy trace", "synergy timings", "synergy locale <language>");
    }

    @Override
    public Side side() {
        return Side.ANY;
    }

    @Override
    public void defaults() {
        Locales.addDefault("synergy.reload.done", "<success>Configuration and translations reloaded!");
        Locales.addDefault("synergy.trace.on", "<secondary>Event trace: <success>on");
        Locales.addDefault("synergy.trace.off", "<secondary>Event trace: <danger>off");
        Locales.addDefault("command.synergy.reload", "reload configuration and translations");
        Locales.addDefault("command.synergy.trace", "log every cross-server event");
        Locales.addDefault("command.synergy.timings", "show average timings");
        Locales.addDefault("command.synergy.locale", "translate missing keys with DeepL");
        Locales.addDefault("synergy.locale.started", "<secondary>Translating %count% keys into %language%...");
        Locales.addDefault("synergy.locale.unsupported", "<danger>DeepL doesn't support %language%. Supported: %languages%");
        Locales.addDefault("synergy.locale.progress", "<secondary>Progress: %current%/%total%");
        Locales.addDefault("synergy.locale.failed", "<danger>Couldn't translate %key%: %error%");
        Locales.addDefault("synergy.locale.done", "<success>Done: %translated% translated, %failed% failed.");
        Locales.addDefault("synergy.timings.empty", "<danger>No timings recorded yet.");
        Locales.addDefault("synergy.timings.entry", "<secondary>%id%: <primary>%time% ms");
        Config.addDefault("deepl.api-key", "");
        Config.addDefault("deepl.pro-account", false);
    }

    @Override
    protected boolean console(String path) {
        return true;
    }

    @Override
    protected List<String> values(Sender sender, String name, String[] input) {
        return DeepL.LANGUAGES;
    }

    @Override
    protected void execute(Sender sender, String path, String[] args) {
        switch (path) {
            case "synergy.reload" -> {
                Synergy.reload();
                sender.send("<locale:synergy.reload.done>");
            }
            case "synergy.trace" -> sender.send(Messaging.toggleTrace() ? "<locale:synergy.trace.on>" : "<locale:synergy.trace.off>");
            case "synergy.timings" -> {
                if (Timings.getAllAverages().isEmpty()) sender.send("<locale:synergy.timings.empty>");
                Timings.getAllAverages().forEach((id, average) -> sender.send(LocaleBuilder.of("synergy.timings.entry").placeholder("id", id).placeholder("time", String.format("%.2f", average))));
            }
            case "synergy.locale" -> {
                String target = args[0].toLowerCase();
                if (!DeepL.LANGUAGES.contains(target)) sender.send(LocaleBuilder.of("synergy.locale.unsupported").placeholder("language", target).placeholder("languages", String.join(", ", DeepL.LANGUAGES)));
                else Tasks.async(() -> translate(sender, Translation.getDefaultLanguage(), target));
            }
        }
    }

    private static void translate(Sender sender, String source, String target) {
        List<String> keys = Locales.keys(source).stream().filter(key -> !Locales.has(key, target)).sorted().toList();
        sender.send(LocaleBuilder.of("synergy.locale.started").placeholder("count", keys.size()).placeholder("language", target));
        int failed = 0;
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            try {
                Object raw = Locales.raw(key, source);
                Object value;
                if (raw instanceof List<?> lines) {
                    List<String> translated = new ArrayList<>();
                    for (Object line : lines) translated.add(DeepL.translate(String.valueOf(line), source, target));
                    value = translated;
                } else value = DeepL.translate(String.valueOf(raw), source, target);
                Locales.put(key, target, value);
                Thread.sleep(200);
            } catch (Exception e) {
                failed++;
                sender.send(LocaleBuilder.of("synergy.locale.failed").placeholder("key", key).placeholder("error", e.getMessage()));
            }
            if ((i + 1) % 20 == 0) sender.send(LocaleBuilder.of("synergy.locale.progress").placeholder("current", i + 1).placeholder("total", keys.size()));
        }
        sender.send(LocaleBuilder.of("synergy.locale.done").placeholder("translated", keys.size() - failed).placeholder("failed", failed));
    }
}
