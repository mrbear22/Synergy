package me.synergy.bukkit.events;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import me.synergy.objects.BreadMaker;
import me.synergy.objects.DataObject;

public class SynergyEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();
    private final me.synergy.events.SynergyEvent event;

    public SynergyEvent(me.synergy.events.SynergyEvent event) {
        this.event = event;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public me.synergy.events.SynergyEvent getEvent() { return event; }
    public String getIdentifier() { return event.getIdentifier(); }
    public DataObject getOption(String option) { return event.getOption(option); }
    public UUID getPlayerUniqueId() { return event.getPlayerUniqueId(); }
    public BreadMaker getBread() { return event.getBread(); }
    public String getOptionsAsJson() { return event.getOptionsAsJson(); }

    public OfflinePlayer getOfflinePlayer() {
        return getPlayerUniqueId() == null ? null : Bukkit.getOfflinePlayer(getPlayerUniqueId());
    }
}
