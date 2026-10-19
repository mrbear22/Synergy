package me.synergy.handlers;

import me.synergy.modules.Module;
import org.bukkit.Bukkit;
import java.net.URI;
import java.util.UUID;

import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import me.synergy.anotations.SynergyListener;
import me.synergy.brains.Spigot;
import me.synergy.brains.Synergy;
import me.synergy.modules.Config;

public class ResourcePackHandler implements Listener, SynergyListener, Module {

    @Override
    public void defaults() {
        Config.addDefault("resource-pack.enabled", false);
        Config.addDefault("resource-pack.url", "https://example.com/pack.zip");
    }

    @Override
    public void start() {
        Bukkit.getPluginManager().registerEvents(this, Spigot.getInstance());
        Synergy.getEventManager().registerEvents(this);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Synergy.getBread(event.getPlayer().getUniqueId()).setData("resourcepack", null);
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        if (event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            Synergy.getBread(event.getPlayer().getUniqueId()).setData("resourcepack", "yes");
            return;
        }
        Synergy.getBread(event.getPlayer().getUniqueId()).setData("resourcepack", "no");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!Config.getBoolean("resource-pack.enabled")) return;
        String url = Config.getString("resource-pack.url");
        event.getPlayer().sendResourcePacks(ResourcePackRequest.resourcePackRequest()
            .packs(ResourcePackInfo.resourcePackInfo(UUID.nameUUIDFromBytes(url.getBytes()), URI.create(url), "")).build());
    }
}