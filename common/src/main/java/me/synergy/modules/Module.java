package me.synergy.modules;

import me.synergy.objects.BreadMaker;

public interface Module {

    enum Side { ANY, HUB, BACKEND }

    default String name() { return getClass().getSimpleName(); }
    default Side side() { return Side.ANY; }
    default String toggle() { return null; }
    default String[] plugins() { return new String[0]; }
    default String[] filled() { return new String[0]; }
    default void defaults() {}
    void start() throws Exception;
    default void stop() throws Exception {}
    default void refresh() throws Exception {}
    /** Called by PlayerSessions on every running module when a player's session starts (after the kick checks) and ends. */
    default void join(BreadMaker bread) throws Exception {}
    default void quit(BreadMaker bread) throws Exception {}
}
