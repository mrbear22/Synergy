package me.synergy.handlers;

import me.synergy.modules.Config;
import me.synergy.modules.Module;
import me.synergy.objects.Locale;
import me.synergy.text.Color;
import me.synergy.text.Translation;
import net.kyori.adventure.text.Component;

/** Server list MOTD for every platform: platforms ask for it from their ping event. */
public class Motd implements Module {

    @Override public Side side() { return Side.BACKEND; }

    @Override
    public void defaults() {
        Config.addDefault("motd.enabled", true);
        Config.addDefault("motd.message", "Message of The Day Example");
        Config.addDefault("motd.max-players", 100);
    }

    @Override
    public void start() {}

    /** The MOTD to show, or null when the platform should keep its own. */
    public static Component message() {
        return Config.getBoolean("motd.enabled") ? new Locale(Config.getString("motd.message"), Translation.getDefaultLanguage()).getColoredComponent(Color.getDefaultTheme()) : null;
    }

    public static int maxPlayers() {
        return Config.getInt("motd.max-players");
    }
}
