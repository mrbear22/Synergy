package me.synergy.bukkit.events;

import java.util.UUID;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class PlayerMuteStatusChangeEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerUniqueId;
    private final boolean muted;
    private final long expires;
    private final String reason;
    private final String staff;
    private boolean cancelled;

    public PlayerMuteStatusChangeEvent(UUID playerUniqueId, boolean muted, long expires, String reason, String staff) {
        this.playerUniqueId = playerUniqueId;
        this.muted = muted;
        this.expires = expires;
        this.reason = reason;
        this.staff = staff;
    }

    public UUID getPlayerUniqueId() {
        return playerUniqueId;
    }

    public boolean isMuted() {
        return muted;
    }

    public long getExpires() {
        return expires;
    }

    public String getReason() {
        return reason;
    }

    public String getStaff() {
        return staff;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}