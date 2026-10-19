package me.synergy.modules;

import java.io.PrintWriter;
import java.io.StringWriter;

import me.synergy.brains.Synergy;

public class Logger {

    public void info(String string) {
        log(() -> Synergy.platform().info(string), string);
    }

    public void info(String string, boolean broadcast) {
        info(string);
    }

    public void warning(String string) {
        log(() -> Synergy.platform().warning(string), string);
    }

    public void error(String string) {
        log(() -> Synergy.platform().error(string), string);
    }

    public void error(String string, Throwable error) {
        StringWriter trace = new StringWriter();
        error.printStackTrace(new PrintWriter(trace));
        error(string + System.lineSeparator() + trace);
    }

    public void discord(String string) {
        Synergy.event("discord-message").toProxy().setOption("message", string).setOption("channel", "log").send();
    }

    public void debug(String string) {
        info(string);
    }

    private static void log(Runnable action, String fallback) {
        if (Synergy.platform() == null) {
            System.err.println("[Synergy] " + fallback);
            return;
        }
        action.run();
    }
}
