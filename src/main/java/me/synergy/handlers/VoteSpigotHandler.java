package me.synergy.handlers;

import me.synergy.anotations.SynergyHandler;
import me.synergy.anotations.SynergyListener;
import me.synergy.brains.Synergy;
import me.synergy.events.SynergyEvent;
import me.synergy.objects.BreadMaker;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.model.VotifierEvent;

public class VoteSpigotHandler {
    
    public void initialize() {
        if (!Synergy.getConfig().getBoolean("votifier.enabled")) {
            return;
        }

        Synergy.getEventManager().registerEvents(new SynergyVoteListener());

        if (Synergy.isDependencyAvailable("Votifier")) {
            Bukkit.getPluginManager().registerEvents(new BukkitVoteListener(), Synergy.getSpigot());
            Synergy.getLogger().info("Bukkit VoteHandler module has been initialized in standalone mode!");
        } else {
            Synergy.getLogger().info("Bukkit VoteHandler module has been initialized in network mode!");
        }
    }
    
    public class BukkitVoteListener implements Listener {
        
        @EventHandler(priority = EventPriority.NORMAL)
        public void onVotifierEvent(VotifierEvent event) {
            Vote vote = event.getVote();
            String service = vote.getServiceName();
            String username = vote.getUsername();

            VoteHandler.processVote(service, username);
        }
    }

    public class SynergyVoteListener implements SynergyListener {
        
        @SynergyHandler
        public void onSynergyPluginMessage(SynergyEvent event) {

            if (!event.getIdentifier().equals("votifier")) {
                return;
            }

            String service = event.getOption("service").getAsString();
            String username = event.getOption("username").getAsString();
            BreadMaker bread = event.getBread();
            
            Synergy.getLogger().info("Player " + username + " voted on " + service);

            for (Player player : Bukkit.getOnlinePlayers()) {
            	BreadMaker b = Synergy.getBread(player.getUniqueId());
                if (player.getName().equals(username)) {
                	String message = Synergy.getConfig().getString("votifier.message").replace("%PLAYER%", username).replace("%SERVICE%", service);
                    player.sendMessage(Synergy.translate(message, b.getLanguage()).setEndings(bread.getPronoun()).getString());
                } else {
                	String message = Synergy.getConfig().getString("votifier.announcement").replace("%PLAYER%", username).replace("%SERVICE%", service);
                    player.sendMessage(Synergy.translate(message, b.getLanguage()).setEndings(bread.getPronoun()).getString());
                }
            }

            for (String command : Synergy.getConfig().getStringList("votifier.rewards")) {
                Synergy.dispatchCommand(command.replace("%PLAYER%", username));
            }
        }
    }
}