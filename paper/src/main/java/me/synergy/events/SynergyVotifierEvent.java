package me.synergy.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import java.util.UUID;

public class SynergyVotifierEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID uuid;
    private final String service;
    private final String username;

    public SynergyVotifierEvent(UUID uuid, String service, String username) {
        this.uuid = uuid;
        this.service = service;
        this.username = username;
    }

    public UUID getPlayerUniqueId() { return uuid; }
    public String getService() { return service; }
    public String getUsername() { return username; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}