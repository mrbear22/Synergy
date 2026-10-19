package me.synergy.brains;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import me.synergy.commands.Command;
import me.synergy.integrations.LuckPermsAPI;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

public interface Platform {

    String name();
    boolean isProxy();
    String version();

    void info(String message);
    void warning(String message);
    void error(String message);

    void console(String command);
    boolean hasDependency(String id);
    boolean send(String server, byte[] data);

    Collection<UUID> online();
    boolean isOnline(UUID uuid);
    boolean hasPermission(UUID uuid, String node);
    String locale(UUID uuid);
    Audience audience(UUID uuid);
    void kick(UUID uuid, Component reason);
    Map<String, List<String>> playersByServer();

    default Optional<Position> position(UUID uuid) { return Optional.empty(); }
    default String serverOf(UUID uuid) { return null; }
    default String label(String label) { return label; }
    default void register(Command command) {}
    default void unregister(Command command) {}
    default void dispatch(UUID uuid, String command) { console(command); }
    default List<String> complete(UUID uuid, String input) { return List.of(); }
    default String placeholders(UUID uuid, String text) { return LuckPermsAPI.placeholders(uuid, text); }
    default boolean isAuthenticated(UUID uuid) { return true; }
    default void onMuteChange(UUID uuid, boolean muted, long expires, String reason, String staff) {}
    default Key sound(String name) { return Key.key(name); }

    record Position(String world, double x, double y, double z) {
        public String apply(String text, String prefix) {
            return text.replace("%" + prefix + "_x%", String.valueOf(x))
                       .replace("%" + prefix + "_y%", String.valueOf(y))
                       .replace("%" + prefix + "_z%", String.valueOf(z))
                       .replace("%" + prefix + "_world%", world);
        }
    }
}
