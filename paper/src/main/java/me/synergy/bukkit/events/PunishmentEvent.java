package me.synergy.bukkit.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import me.synergy.objects.Punishment;

/**
 * Fired on every server of the network when a punishment is issued, revoked or expires, whatever issued it
 * (game, Discord, website, another plugin). Informational: to punish, call {@code me.synergy.modules.Punishments}.
 */
public class PunishmentEvent extends Event {

    public enum Action { ISSUE, REVOKE, EXPIRE }

    private static final HandlerList HANDLERS = new HandlerList();

    private final Punishment punishment;
    private final Action action;
    private final String origin;

    public PunishmentEvent(Punishment punishment, Action action, String origin) {
        super(true);
        this.punishment = punishment;
        this.action = action;
        this.origin = origin;
    }

    public Punishment getPunishment() { return punishment; }
    public Action getAction() { return action; }
    /** Where this change came from: game, console, discord, web, auto. */
    public String getOrigin() { return origin; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
