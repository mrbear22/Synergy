package me.synergy.handlers;

import java.net.URI;
import java.util.UUID;

import me.synergy.brains.Synergy;
import me.synergy.modules.Config;
import me.synergy.modules.Module;
import me.synergy.objects.BreadMaker;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;

/** Server resource pack for every backend: sent when the player's session starts, its load status kept in the player's data ("resourcepack": yes/no). */
public class ResourcePacks implements Module {

    @Override public Side side() { return Side.BACKEND; }

    @Override
    public void defaults() {
        Config.addDefault("resource-pack.enabled", false);
        Config.addDefault("resource-pack.url", "https://example.com/pack.zip");
    }

    @Override
    public void start() {}

    @Override
    public void join(BreadMaker bread) {
        if (!Config.getBoolean("resource-pack.enabled")) return;
        String url = Config.getString("resource-pack.url");
        Synergy.platform().audience(bread.getUniqueId()).sendResourcePacks(ResourcePackRequest.resourcePackRequest()
            .packs(ResourcePackInfo.resourcePackInfo(UUID.nameUUIDFromBytes(url.getBytes()), URI.create(url), "")).build());
    }

    @Override
    public void quit(BreadMaker bread) {
        bread.setData("resourcepack", null);
    }

    /** Platforms report the client's answer here. */
    public static void status(UUID uuid, boolean loaded) {
        Synergy.getBread(uuid).setData("resourcepack", loaded ? "yes" : "no");
    }
}
