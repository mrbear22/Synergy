package me.synergy.handlers;

import java.util.UUID;

import me.synergy.anotations.SynergyHandler;
import me.synergy.anotations.SynergyListener;
import me.synergy.brains.Synergy;
import me.synergy.events.SynergyEvent;
import me.synergy.modules.Module;
import me.synergy.objects.BreadMaker;
import me.synergy.objects.Locale;

public class MessageHandler implements SynergyListener, Module {

    @Override public Side side() { return Side.BACKEND; }

    @Override
    public void start() {
        Synergy.getEventManager().registerEvents(this);
    }

    @SynergyHandler({"system-chat", "broadcast", "global-chat"})
    public void onSynergyEvent(SynergyEvent event) {
        switch (event.getIdentifier()) {
            case "system-chat" -> {
                UUID uuid = event.getPlayerUniqueId();
                if (uuid != null && Synergy.platform().isOnline(uuid)) {
                    send(uuid, event.getOption("message").getAsString(), true, true);
                }
            }
            case "broadcast" -> Synergy.platform().online().forEach(uuid -> send(uuid, event.getOption("message").getAsString(), true, false));
            case "global-chat" -> Synergy.platform().online().forEach(uuid -> send(uuid, event.getOption("format").getAsString(), true, true));
            default -> {}
        }
    }

    public static void send(UUID uuid, String message, boolean interactive, boolean gendered) {
        BreadMaker bread = Synergy.getBread(uuid);
        Locale locale = Synergy.translate(message, bread.getLanguage()).setPlaceholders(bread).setGendered(gendered ? bread.getGender() : null);
        if (interactive) locale.setExecuteInteractive(bread);
        Synergy.platform().audience(uuid).sendMessage(locale.getColoredComponent(bread.getTheme()));
    }
}
