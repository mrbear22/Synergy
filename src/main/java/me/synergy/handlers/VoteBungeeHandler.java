package me.synergy.handlers;

import me.synergy.brains.Bungee;
import me.synergy.brains.Synergy;
import me.synergy.modules.Config;
import me.synergy.objects.BreadMaker;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import com.vexsoftware.votifier.bungee.events.VotifierEvent;
import com.vexsoftware.votifier.model.Vote;

public class VoteBungeeHandler implements Listener {
    
    public void initialize() {
        if (!Config.getBoolean("votifier.enabled")) {
            return;
        }
        if (!Synergy.isDependencyAvailable("NuVotifier")) {
            Synergy.getLogger().warning("NuVotifier is required to initialize BungeeCord VoteHandler module!");
            return;
        }
        Bungee.getInstance().getProxy().getPluginManager().registerListener(Bungee.getInstance(), this);
        Synergy.getLogger().info("BungeeCord VoteHandler module has been initialized!");
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onVotifierEvent(VotifierEvent event) {
        Vote vote = event.getVote();
        String service = vote.getServiceName();
        String username = vote.getUsername();
        BreadMaker bread = Synergy.getBread(Synergy.getOfflineUniqueId(username));
        
        VoteHandler.processVote(service, username);
        
        bread.setData("votes", bread.getData("votes").isSet() ? bread.getData("votes").getAsInteger() + 1 : 1);
    }
}