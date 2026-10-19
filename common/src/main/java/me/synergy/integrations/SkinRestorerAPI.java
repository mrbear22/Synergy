package me.synergy.integrations;

import java.util.UUID;

import me.synergy.modules.Module;
import net.skinsrestorer.api.SkinsRestorerProvider;
import net.skinsrestorer.api.exception.DataRequestException;
import net.skinsrestorer.api.property.SkinProperty;

public class SkinRestorerAPI implements Module {

    private static volatile boolean enabled;

    @Override public String name() { return "SkinsRestorer"; }
    @Override public String[] plugins() { return new String[] {"SkinsRestorer"}; }

    @Override
    public void start() {
        enabled = true;
    }

    @Override
    public void stop() {
        enabled = false;
    }

    public static String getSkinTextureURL(UUID uuid, String name) {
        return enabled && uuid != null ? SR.texture(uuid, name) : null;
    }

    private static final class SR {
        static String texture(UUID uuid, String name) {
            var storage = SkinsRestorerProvider.get().getPlayerStorage();
            try {
                return storage.getSkinOfPlayer(uuid).isEmpty() ? null : storage.getSkinForPlayer(uuid, name).map(SkinProperty::getValue).map(MojangAPI::getSkinTextureURLStringFromBase64Blob).orElse(null);
            } catch (DataRequestException e) {
                return null;
            }
        }
    }
}
