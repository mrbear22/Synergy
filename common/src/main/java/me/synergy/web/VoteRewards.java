package me.synergy.web;

import java.util.UUID;

import me.synergy.anotations.SynergyHandler;
import me.synergy.anotations.SynergyListener;
import me.synergy.brains.Synergy;
import me.synergy.events.SynergyEvent;
import me.synergy.handlers.MessageHandler;
import me.synergy.modules.Config;
import me.synergy.modules.DataManager;
import me.synergy.modules.Module;
import me.synergy.objects.BreadMaker;
import me.synergy.objects.LocaleBuilder;

public class VoteRewards implements SynergyListener, Module {

    @Override public Side side() { return Side.BACKEND; }

    @Override
    public void defaults() {
        Config.addDefault("votifier.rewards", new String[0]);
    }

    @Override
    public void start() {
        Synergy.getEventManager().registerEvents(this);
    }

    @SynergyHandler("vote")
    public void onVote(SynergyEvent event) {
        String username = event.getOption("username").getAsString();
        String service = event.getOption("service").getAsString();
        UUID uuid = DataManager.findUuidByName(username);
        if (uuid == null) {
            Synergy.getLogger().warning("Votifier: unknown player " + username);
            return;
        }
        BreadMaker bread = Synergy.getBread(uuid);
        for (UUID viewer : Synergy.platform().online())
            MessageHandler.send(viewer, LocaleBuilder.of(viewer.equals(uuid) ? "vote.reward.received" : "vote.reward.announce")
                .placeholder("player", username).placeholder("service", service).placeholder("gender", bread.getGender()).build(), false, false);
        Config.getStringList("votifier.rewards").forEach(command -> Synergy.platform().console(command.replace("%PLAYER%", username)));
        Synergy.platform().onVote(uuid, service, username);
    }
}
