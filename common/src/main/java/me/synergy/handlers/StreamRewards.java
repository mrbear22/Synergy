package me.synergy.handlers;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.PatternSyntaxException;

import me.synergy.brains.Platform.Position;
import me.synergy.brains.Synergy;
import me.synergy.modules.Config;
import me.synergy.objects.BreadMaker;

/**
 * Rewards of every stream service (Twitch, Monobank, TikTok): "&lt;service&gt;.rewards.&lt;key&gt;" in the config.
 * Services only pick the reward and supply their own placeholders; the streamer's ones are shared:
 * %streamer_name%, %streamer_uuid%, %streamer_uuid_nbt%, and the position as %streamer_x/y/z/world% (also %target_...%).
 */
public final class StreamRewards {

    private StreamRewards() {}

    public static Optional<String> find(String service, Predicate<String> path) {
        return Config.getConfigurationSection(service + ".rewards").keySet().stream().filter(key -> path.test(service + ".rewards." + key)).findFirst();
    }

    /** Runs the reward's commands with the given placeholder/value pairs; false when the viewer's input doesn't pass the reward's input-regex. */
    public static boolean run(String service, String key, BreadMaker streamer, String input, String... placeholders) {
        String path = service + ".rewards." + key, tag = "[" + service + "] [" + streamer.getName() + "] ";
        String regex = Config.getString(path + ".input-regex");
        try {
            if (regex != null && !regex.isEmpty() && input != null && !input.isEmpty() && !input.matches(regex)) {
                Synergy.getLogger().warning(tag + "Input '" + input + "' doesn't match " + regex);
                return false;
            }
        } catch (PatternSyntaxException e) {
            Synergy.getLogger().error(tag + "Invalid regex " + regex + ": " + e.getMessage());
            return false;
        }
        UUID uuid = streamer.getUniqueId();
        Optional<Position> position = Synergy.platform().position(uuid);
        for (String command : Config.getStringList(path + ".commands")) {
            command = command.replace("%streamer_name%", streamer.getName()).replace("%streamer_uuid%", uuid.toString()).replace("%streamer_uuid_nbt%", nbt(uuid));
            if (position.isPresent()) command = position.get().apply(position.get().apply(command, "streamer"), "target");
            for (int i = 0; i + 1 < placeholders.length; i += 2) command = command.replace(placeholders[i], placeholders[i + 1] == null ? "" : placeholders[i + 1]);
            Synergy.getLogger().info(tag + "Running command: " + command);
            Synergy.platform().console(command);
        }
        return true;
    }

    private static String nbt(UUID uuid) {
        long msb = uuid.getMostSignificantBits(), lsb = uuid.getLeastSignificantBits();
        return "[I;" + (int) (msb >> 32) + "," + (int) msb + "," + (int) (lsb >> 32) + "," + (int) lsb + "]";
    }
}
