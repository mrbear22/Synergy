package me.synergy.handlers;

import me.synergy.modules.Module;
import me.synergy.modules.DataManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import io.papermc.paper.event.player.AsyncChatEvent;

import me.synergy.brains.Spigot;
import me.synergy.brains.Synergy;
import me.synergy.objects.BreadMaker;
import me.synergy.objects.LocaleBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public class PlayerSpigotHandler implements Listener, Module {

    @Override
    public void start() {
        Bukkit.getPluginManager().registerEvents(this, Spigot.getInstance());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!ChatHandler.isEnabled()) return;
        Set<UUID> viewers = new HashSet<>();
        event.viewers().forEach(a -> { if (a instanceof Player r) viewers.add(r.getUniqueId()); });
        Player p = event.getPlayer();
        String format = ChatHandler.handle(p.getUniqueId(), p.getName(), LegacyComponentSerializer.legacyAmpersand().serialize(event.message()), viewers, true);
        if (format == null) {
            event.setCancelled(true);
            return;
        }
        event.viewers().removeIf(a -> a instanceof Player r && !viewers.contains(r.getUniqueId()));
        event.renderer((s, n, m, v) -> v instanceof Player r ? ChatHandler.render(format, r.getUniqueId()) : m);
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (ChatHandler.blockCommand(event.getPlayer().getUniqueId(), event.getPlayer().getName(), event.getMessage())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        String username = event.getName();

        try {
        	UUID oldUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
            if (oldUuid != null && !oldUuid.equals(uuid)) {
                DataManager.migrateUuid(oldUuid, uuid);
                Synergy.getLogger().info("Migrated UUID for " + username + ": " + oldUuid + " -> " + uuid);

                for (org.bukkit.World world : Bukkit.getWorlds()) {
                    Path base = world.getWorldFolder().toPath();
                    migrateFile(base.resolve("playerdata").resolve(oldUuid + ".dat"),
                                base.resolve("playerdata").resolve(uuid + ".dat"));
                    migrateFile(base.resolve("playerdata").resolve(oldUuid + ".dat_old"),
                                base.resolve("playerdata").resolve(uuid + ".dat_old"));
                    migrateFile(base.resolve("stats").resolve(oldUuid + ".json"),
                                base.resolve("stats").resolve(uuid + ".json"));
                    migrateFile(base.resolve("advancements").resolve(oldUuid + ".json"),
                                base.resolve("advancements").resolve(uuid + ".json"));
                }
            }
        } catch (Exception e) {
            Synergy.getLogger().warning("UUID migration of " + username + " failed: " + e.getMessage());
        }

        BreadMaker bread = Synergy.getBread(uuid);
        for (String option : new String[] {"twitch-username", "twitch-token", "monobank", "discord", "confirm-discord", "last-voted", "gender", "chat", "theme", "name", "votes", "mojangUUID", "language"})
            bread.getData(option);
    }

    private void migrateFile(Path source, Path target) {
        try {
            if (Files.exists(source) && !Files.exists(target)) {
                Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
            }
        } catch (IOException e) {
            Synergy.getLogger().warning("Failed to migrate " + source + ": " + e.getMessage());
        }
    }
    
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        boolean kicked = PlayerSessions.quitAsync(player.getUniqueId(), player.getName());
        event.quitMessage(kicked ? null : LocaleBuilder.of("player.server.quit")
            .placeholder("player", player.getName())
            .placeholder("gender", Synergy.getBread(player.getUniqueId()).getGender().name().toLowerCase())
            .component());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        event.joinMessage(LocaleBuilder.of("player.server.join")
            .placeholder("player", player.getName())
            .placeholder("gender", Synergy.getBread(player.getUniqueId()).getGender().name().toLowerCase())
            .component());
        PlayerSessions.joinAsync(player.getUniqueId(), player.getName(), String.valueOf(player.getAddress()));
    }
    
    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        BreadMaker bread = Synergy.getBread(event.getPlayer().getUniqueId());
        Component reason = Synergy.translate(event.reason(), bread.getLanguage()).getColoredComponent(bread.getTheme());
        Synergy.getLogger().discord("```Player " + event.getPlayer().getName() + " has been kicked with the reason: "
            + PlainTextComponentSerializer.plainText().serialize(reason) + "```");
        event.reason(reason);
    }

}