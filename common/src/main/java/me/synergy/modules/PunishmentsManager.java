package me.synergy.modules;

import java.time.Duration;
import java.util.UUID;

import me.synergy.brains.Synergy;
import me.synergy.integrations.LuckPermsAPI;
import me.synergy.objects.BreadMaker;

public class PunishmentsManager {

    private static final String VOICE = "voicechat.speak";

    public static boolean isMuted(BreadMaker bread) {
        return bread.getData("mute-expires").isSet();
    }

    public static long getMuteExpires(BreadMaker bread) {
        Long expires = bread.getData("mute-expires").getAsLong();
        return expires == null ? 0 : expires;
    }

    public static String getMuteReason(BreadMaker bread) {
        return bread.getData("mute-reason").getAsString();
    }

    public static String getMuteBy(BreadMaker bread) {
        return bread.getData("mute-by").getAsString();
    }

    public static long getMuteRemainingSeconds(BreadMaker bread) {
        long expires = getMuteExpires(bread);
        return expires <= 0 ? expires : Math.max((expires - System.currentTimeMillis()) / 1000L, 0);
    }

    public static void mute(BreadMaker bread, long millis, String reason, String staff) {
        Duration ttl = millis <= 0 ? null : Duration.ofMillis(millis);
        long expires = ttl == null ? -1 : System.currentTimeMillis() + millis;
        Synergy.platform().onMuteChange(bread.getUniqueId(), true, expires, reason, staff);
        bread.setData("mute-expires", expires, ttl);
        bread.setData("mute-reason", reason, ttl);
        bread.setData("mute-by", staff, ttl);
        if (LuckPermsAPI.enabled()) LuckPermsAPI.deny(bread.getUniqueId(), VOICE, ttl);
    }

    public static void unmute(BreadMaker bread) {
        Synergy.platform().onMuteChange(bread.getUniqueId(), false, 0, null, null);
        bread.setData("mute-expires", null);
        bread.setData("mute-reason", null);
        bread.setData("mute-by", null);
        if (LuckPermsAPI.enabled()) LuckPermsAPI.allow(bread.getUniqueId(), VOICE);
    }

    public static void kick(UUID uuid, String reason) {
        BreadMaker bread = Synergy.getBread(uuid);
        Synergy.platform().kick(uuid, Synergy.translate(reason, bread.getLanguage()).setPlaceholders(bread).setGendered(bread.getGender()).getColoredComponent(bread.getTheme()));
    }
}
