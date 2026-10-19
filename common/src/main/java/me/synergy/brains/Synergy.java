package me.synergy.brains;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import me.synergy.discord.Discord;
import me.synergy.events.SynergyEvent;
import me.synergy.commands.ChatCommand;
import me.synergy.commands.DelayedCommand;
import me.synergy.commands.DiscordCommand;
import me.synergy.commands.DynamicCommands;
import me.synergy.commands.LanguageCommand;
import me.synergy.commands.MonobankCommand;
import me.synergy.commands.PronounCommand;
import me.synergy.commands.SynergyCommand;
import me.synergy.commands.TempMuteCommand;
import me.synergy.commands.ThemeCommand;
import me.synergy.commands.TikTokCommand;
import me.synergy.commands.TwitchCommand;
import me.synergy.commands.VoteCommand;
import me.synergy.handlers.ChatHandler;
import me.synergy.events.SynergyEventManager;
import me.synergy.handlers.MessageHandler;
import me.synergy.discord.RolesHandler;
import me.synergy.integrations.LuckPermsAPI;
import me.synergy.integrations.PlanAPI;
import me.synergy.integrations.SkinRestorerAPI;
import me.synergy.modules.Config;
import me.synergy.modules.Messaging;
import me.synergy.modules.DataManager;
import me.synergy.objects.BreadMaker;
import me.synergy.objects.Locale;
import me.synergy.twitch.Twitch;
import me.synergy.modules.Locales;
import me.synergy.modules.Logger;
import me.synergy.modules.Module;
import me.synergy.modules.Tasks;
import me.synergy.modules.UpdateChecker;
import me.synergy.web.MonobankHandler;
import me.synergy.web.TikTokHandler;
import me.synergy.web.VotifierServer;
import me.synergy.web.WebServer;
import net.dv8tion.jda.api.JDA;
import net.kyori.adventure.text.Component;

public class Synergy {

    private static final Logger LOGGER = new Logger();
    private static Platform platform;
    private static final List<Module> MODULES = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void start(Platform impl, Module... platformModules) {
        platform = impl;
        Config.start();
        try {
            Messaging.start();
            register(new Locales(), new DataManager(), new Discord(), new Twitch(), new MonobankHandler(), new WebServer(), new TikTokHandler(), new VotifierServer(),
                new MessageHandler(), new ChatHandler(), new SynergyCommand(), new ChatCommand(), new VoteCommand(), new PronounCommand(), new LanguageCommand(),
                new DiscordCommand(), new TwitchCommand(), new MonobankCommand(), new ThemeCommand(), new TikTokCommand(), new DelayedCommand(), new TempMuteCommand(),
                new DynamicCommands(), new LuckPermsAPI(), new SkinRestorerAPI(), new PlanAPI(), new RolesHandler());
            register(platformModules);
            getLogger().info("Enabled: " + String.join(", ", MODULES.stream().map(Module::name).toList()));
            UpdateChecker.check("mrbear22", "Synergy");
        } finally {
            Config.save();
            Locales.save();
        }
    }

    public static void register(Module... modules) {
        for (Module module : modules) {
            if (module == null) continue;
            try {
                Config.defaults(here(module), module::defaults);
                String reason = reason(module);
                if (reason == null) {
                    module.start();
                    MODULES.add(module);
                } else if (!reason.isEmpty()) {
                    getLogger().warning(module.name() + " is disabled: " + reason);
                }
            } catch (Throwable e) {
                getLogger().error(module.name() + " failed to start: " + e, e);
            }
        }
    }

    private static boolean here(Module module) {
        return module.side() == Module.Side.ANY || (module.side() == Module.Side.BACKEND ? !isProxy() : isHub());
    }

    private static String reason(Module module) {
        if (module.toggle() != null && !Config.getBoolean(module.toggle())) return "";
        if (!here(module)) return module.side() == Module.Side.HUB ? "this server is part of a network, enable it on the proxy" : "";
        List<String> missing = Arrays.stream(module.plugins()).filter(p -> !platform.hasDependency(p)).toList();
        if (!missing.isEmpty()) return module.plugins().length == missing.size() && module.toggle() == null ? "" : "missing " + String.join(", ", missing);
        List<String> unfilled = Arrays.stream(module.filled()).filter(k -> !Config.isFilled(k)).toList();
        return unfilled.isEmpty() ? null : "fill in " + String.join(", ", unfilled);
    }

    public static void reload() {
        Config.start();
        MODULES.forEach(module -> {
            try {
                Config.defaults(here(module), module::defaults);
                module.refresh();
            } catch (Throwable e) {
                getLogger().error(module.name() + " failed to reload: " + e, e);
            }
        });
        Config.save();
        Locales.save();
    }

    public static void stop() {
        for (int i = MODULES.size() - 1; i >= 0; i--) {
            Module module = MODULES.get(i);
            try {
                module.stop();
            } catch (Throwable e) {
                getLogger().error(module.name() + " failed to stop: " + e, e);
            }
        }
        MODULES.clear();
        Tasks.stop();
    }

    public static Platform platform() {
        return platform;
    }

    public static boolean isProxy() {
        return platform.isProxy();
    }

    public static boolean isHub() {
        return isProxy() || !Messaging.isEnabled();
    }

    public static String getSynergyToken() {
        return Config.getString("messaging.token");
    }

    public static String getServerName() {
        return Config.getString("messaging.servername", "server");
    }

    public static SynergyEvent event(String identifier) {
        return new SynergyEvent(identifier);
    }

    public static JDA getDiscord() {
        return Discord.getJda();
    }

    public static Logger getLogger() {
        return LOGGER;
    }

    public static BreadMaker getBread(UUID uuid) {
        return new BreadMaker(uuid);
    }

    public static void debug(String string) {
        LOGGER.info(string, true);
    }

    public static Locale translate(String string, String language) {
        return new Locale(string, language);
    }

    public static Locale translate(Component component, String language) {
        return new Locale(component, language);
    }

    public static File getDataFolder() {
        return new File("plugins/Synergy");
    }

    public static SynergyEventManager getEventManager() {
        return SynergyEventManager.getInstance();
    }

    public static void dispatchCommand(String command) {
        if (!isProxy()) {
            platform.console(command);
        } else {
            event("dispatch-command").setOption("command", command).send();
        }
    }

    public static void broadcastMessage(String string, BreadMaker bread) {
        event("broadcast").setPlayerUniqueId(bread.getUniqueId()).setOption("message", string).send();
    }

    public static UUID findUserUUID(String option, String value) {
        return DataManager.findUserUUID(option, value);
    }
}
