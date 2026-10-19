package me.synergy.integrations;

import java.util.UUID;

import org.bukkit.Bukkit;

import fr.xephi.authme.api.v3.AuthMeApi;
import me.synergy.modules.Module;

public class AuthmeAPI implements Module {

    private static volatile boolean enabled;

    @Override public String name() { return "AuthMe"; }
    @Override public String[] plugins() { return new String[] {"AuthMe"}; }

    @Override
    public void start() {
        enabled = true;
    }

    @Override
    public void stop() {
        enabled = false;
    }

    public static boolean isAuthenticated(UUID uuid) {
        return !enabled || AuthMeApi.getInstance().isAuthenticated(Bukkit.getPlayer(uuid));
    }
}
