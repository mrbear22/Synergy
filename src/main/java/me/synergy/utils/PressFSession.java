package me.synergy.utils;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.scheduler.BukkitRunnable;

import me.synergy.brains.Spigot;

public class PressFSession implements Listener {

    private final int seconds;
    private final Consumer<Player> action;
    private final Set<Player> participatedPlayers;
    private BukkitRunnable timer;
    
    public PressFSession(Consumer<Player> action, int seconds) {
        this.seconds = seconds;
        this.action = action;
        this.participatedPlayers = new HashSet<>();
    }
    
    public void start() {
        Bukkit.getPluginManager().registerEvents(this, Spigot.getInstance());
        
        timer = new BukkitRunnable() {
            @Override
            public void run() {
                stop();
            }
        };
        
        timer.runTaskLater(Spigot.getInstance(), seconds * 20L);
    }
    
    public void stop() {
        HandlerList.unregisterAll(this);
        
        if (timer != null && !timer.isCancelled()) {
            timer.cancel();
        }
    }
    
    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        
        if (participatedPlayers.contains(player)) {
            return;
        }
        
        participatedPlayers.add(player);
        action.accept(player);
        event.setCancelled(true);
    }
    
    public int getParticipantsCount() {
        return participatedPlayers.size();
    }
}