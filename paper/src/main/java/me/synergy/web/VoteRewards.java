package me.synergy.web;

import me.synergy.modules.Module;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.synergy.anotations.SynergyHandler;
import me.synergy.anotations.SynergyListener;
import me.synergy.brains.Spigot;
import me.synergy.brains.Synergy;
import me.synergy.events.SynergyEvent;
import me.synergy.events.SynergyVotifierEvent;
import me.synergy.modules.Config;
import me.synergy.modules.DataManager;
import me.synergy.objects.BreadMaker;
import me.synergy.objects.LocaleBuilder;

public class VoteRewards implements SynergyListener, Module {

    @Override
    public void defaults() {
        Config.addDefault("votifier.rewards", new String[] {"eco give %PLAYER% 1"});
    }

    @Override
    public void start() {
        Synergy.getEventManager().registerEvents(this);
    }


    @SynergyHandler("vote")
    public void onSynergyEvent(SynergyEvent event) {
        String username = event.getOption("username").getAsString();
        String service = event.getOption("service").getAsString();
        
        List<String> rewards = Config.getStringList("votifier.rewards");
        if (username == null || rewards == null || rewards.isEmpty()) {
            return;
        }

        UUID uuid = DataManager.findUuidByName(username);

        if (uuid == null) {
        	Synergy.getLogger().warning("Votifier: Could not find UUID for player " + username);
            return;
        }
        
        BreadMaker bread = Synergy.getBread(uuid);
        
        Bukkit.getScheduler().runTask(Spigot.getInstance(), () -> {

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().equals(username)) {
                    player.sendMessage(LocaleBuilder.of("vote.reward.received")
                    		.placeholder("player", username)
                    		.placeholder("service", service)
                    		.placeholder("gender", bread.getGender())
                    		.component()
                    );
                } else {
                    player.sendMessage(LocaleBuilder.of("vote.reward.announce")
                    		.placeholder("player", username)
                    		.placeholder("service", service)
                    		.placeholder("gender", bread.getGender())
                    		.component()
                    );
                }
            }
            
            for (String command : rewards) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%PLAYER%", username));
            }
            
            Bukkit.getPluginManager().callEvent(new SynergyVotifierEvent(bread.getUniqueId(), service, username));
        });

    }
}
