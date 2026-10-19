package me.synergy.integrations;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.synergy.modules.Module;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.query.QueryOptions;

public class LuckPermsAPI implements Module {

    private static volatile boolean enabled;

    @Override public String name() { return "LuckPerms"; }
    @Override public String[] plugins() { return new String[] {"LuckPerms"}; }

    @Override
    public void start() {
        enabled = true;
    }

    @Override
    public void stop() {
        enabled = false;
    }

    public static boolean enabled() {
        return enabled;
    }

    public static String placeholders(UUID uuid, String text) {
        return enabled && uuid != null && text.contains("%luckperms_") ? LP.placeholders(uuid, text) : text;
    }

    public static List<String> groups(UUID uuid) {
        return LP.groups(uuid);
    }

    public static boolean member(UUID uuid, String group) {
        return LP.member(uuid, group);
    }

    public static void parent(UUID uuid, String group, boolean add) {
        LP.parent(uuid, group, add);
    }

    public static void deny(UUID uuid, String permission, Duration duration) {
        LP.deny(uuid, permission, duration);
    }

    public static void allow(UUID uuid, String permission) {
        LP.allow(uuid, permission);
    }

    public static AutoCloseable onChange(Consumer<UUID> handler) {
        return LP.onChange(handler);
    }

    private static final class LP {

        private static final Pattern META = Pattern.compile("%luckperms_meta_([^%]+)%");

        static String placeholders(UUID uuid, String text) {
            var meta = user(uuid).getCachedData().getMetaData();
            text = text.replace("%luckperms_prefix%", Objects.toString(meta.getPrefix(), ""))
                       .replace("%luckperms_suffix%", Objects.toString(meta.getSuffix(), ""))
                       .replace("%luckperms_primary_group_name%", Objects.toString(meta.getPrimaryGroup(), ""));
            return META.matcher(text).replaceAll(m -> Matcher.quoteReplacement(Objects.toString(meta.getMetaValue(m.group(1)), "")));
        }

        static List<String> groups(UUID uuid) {
            return user(uuid).getInheritedGroups(QueryOptions.nonContextual()).stream().map(Group::getName).toList();
        }

        static boolean member(UUID uuid, String group) {
            return user(uuid).getNodes().contains(InheritanceNode.builder(group).build());
        }

        static void parent(UUID uuid, String group, boolean add) {
            modify(uuid, InheritanceNode.builder(group).build(), add);
        }

        static void deny(UUID uuid, String permission, Duration duration) {
            modify(uuid, duration == null ? Node.builder(permission).value(false).build() : Node.builder(permission).value(false).expiry(duration).build(), true);
        }

        static void allow(UUID uuid, String permission) {
            LuckPermsProvider.get().getUserManager().modifyUser(uuid, user -> user.data().clear(node -> node.getKey().equals(permission) && !node.getValue()));
        }

        static AutoCloseable onChange(Consumer<UUID> handler) {
            return LuckPermsProvider.get().getEventBus().subscribe(UserDataRecalculateEvent.class, event -> handler.accept(event.getUser().getUniqueId()));
        }

        private static void modify(UUID uuid, Node node, boolean add) {
            LuckPermsProvider.get().getUserManager().modifyUser(uuid, user -> {
                if (add) user.data().add(node);
                else user.data().remove(node);
            });
        }

        private static User user(UUID uuid) {
            var users = LuckPermsProvider.get().getUserManager();
            User user = users.getUser(uuid);
            return user != null ? user : users.loadUser(uuid).join();
        }
    }
}
