package me.synergy.commands;

import java.util.ArrayList;
import java.util.List;

import me.synergy.brains.Synergy;
import me.synergy.modules.Config;
import me.synergy.modules.Locales;
import me.synergy.modules.Module;

public class DynamicCommands implements Module {

    private static final List<Command> registered = new ArrayList<>();

    @Override public Side side() { return Side.BACKEND; }

    @Override
    public void defaults() {
        Locales.addDefault("server.help.message", "<primary>/language <secondary>— choose your language", "<primary>/theme <secondary>— choose a color theme", "<primary>/pronoun <secondary>— set your pronouns", "<primary>/discord link <secondary>— link your Discord");
        if (Config.getConfigurationSection("commands").isEmpty()) {
            Config.addDefault("commands.help.message", "<locale:server.help.message>");
            Config.addDefault("commands.help.book", false);
        }
    }

    @Override
    public void start() {
        for (String name : Config.getConfigurationSection("commands").keySet()) {
            Command command = new Command(name) {
                @Override
                protected String permission(String path) {
                    return Config.getString("commands." + name + ".permission");
                }

                @Override
                protected boolean console(String path) {
                    return true;
                }

                @Override
                protected void execute(Sender sender, String path, String[] args) {
                    Object message = Config.get("commands." + name + ".message");
                    String text = message instanceof List<?> list ? String.join("\n", list.stream().map(String::valueOf).toList()) : String.valueOf(message);
                    if (Config.getBoolean("commands." + name + ".book") && sender.isPlayer()) sender.book(name, text);
                    else sender.send(text);
                }
            };
            command.start();
            registered.add(command);
        }
    }

    @Override
    public void refresh() {
        stop();
        start();
    }

    @Override
    public void stop() {
        registered.forEach(Command::stop);
        registered.clear();
    }

    @Override
    public String name() {
        return "Commands (" + registered.size() + ")";
    }
}
