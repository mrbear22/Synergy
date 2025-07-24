package me.synergy.handlers;

import me.synergy.brains.Bungee;
import me.synergy.brains.Synergy;
import me.synergy.brains.Velocity;
import com.vexsoftware.votifier.model.Vote;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.EventTask;

public class VoteProxyHandler {
    
    public static class BungeeHandler implements net.md_5.bungee.api.plugin.Listener {
        public void initialize() {
            if (!Synergy.getConfig().getBoolean("votifier.enabled")) {
                return;
            }
            if (!Synergy.isDependencyAvailable("NuVotifier")) {
                Synergy.getLogger().warning("NuVotifier is required to initialize BungeeCord VoteHandler module!");
                return;
            }
            Bungee.getInstance().getProxy().getPluginManager().registerListener(Bungee.getInstance(), this);
            Synergy.getLogger().info("BungeeCord VoteHandler module has been initialized!");
        }
        
        @net.md_5.bungee.event.EventHandler(priority = net.md_5.bungee.event.EventPriority.NORMAL)
        public void onVotifierEvent(com.vexsoftware.votifier.bungee.events.VotifierEvent event) {
            Vote vote = event.getVote();
            String service = vote.getServiceName();
            String username = vote.getUsername();
            
            VoteHandler.processVote(service, username);
        }
    }
    
    public static class VelocityHandler {
        public void initialize() {
            if (!Synergy.getConfig().getBoolean("votifier.enabled")) {
                return;
            }
            if (!Synergy.isDependencyAvailable("NuVotifier")) {
                Synergy.getLogger().warning("NuVotifier is required to initialize Velocity VoteHandler module!");
                return;
            }
            Synergy.getVelocity();
            Velocity.getProxy().getEventManager().register(Synergy.getVelocity(), this);
            Synergy.getLogger().info("Velocity VoteHandler module has been initialized!");
        }
        
        @Subscribe
        public EventTask onVotifierEvent(com.vexsoftware.votifier.bungee.events.VotifierEvent event) {
            return EventTask.async(() -> {
                Vote vote = event.getVote();
                String service = vote.getServiceName();
                String username = vote.getUsername();
                
                VoteHandler.processVote(service, username);
            });
        }
    }
}