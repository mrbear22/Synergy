package me.synergy.handlers;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.model.VotifierEvent;

import me.synergy.anotations.SynergyHandler;
import me.synergy.anotations.SynergyListener;
import me.synergy.brains.Synergy;
import me.synergy.events.SynergyEvent;
import me.synergy.objects.BreadMaker;
import me.synergy.utils.Translation;

public class VoteHandler implements Listener, SynergyListener {

    public void initialize() {
        if (!Synergy.getConfig().getBoolean("votifier.enabled")) {
            return;
        }
        if (!Synergy.isDependencyAvailable("Votifier")) {
            Synergy.getLogger().warning("NuVotifier is required to initialize " + getClass().getSimpleName() + " module!");
            return;
        }
        
        Synergy.getEventManager().registerEvents(this);
        Bukkit.getPluginManager().registerEvents(this, Synergy.getSpigot());
        Synergy.getLogger().info(String.valueOf(getClass().getSimpleName()) + " module has been initialized!");
    }

    @SynergyHandler
    public void onSynergyPluginMessage(SynergyEvent event) {
        if (!event.getIdentifier().equals("votifier")) {
            return;
        }

        String service = event.getOption("service").getAsString();
        String username = event.getOption("username").getAsString();
        BreadMaker bread = Synergy.getBread(Synergy.getSpigot().getUniqueIdFromName(username));

        for (String command : Synergy.getConfig().getStringList("votifier.rewards")) {
        	Synergy.dispatchCommand(command.replace("%PLAYER%", bread.getName()));
        }
        
        if (bread == null) {
        	return;
        }
        
        bread.setData("last-voted", String.valueOf(System.currentTimeMillis()));
        
        if (bread.isOnline()) {
        	bread.sendMessage(Translation.processLangTags("<lang>synergy-voted-successfully</lang>", bread.getLanguage()).replace("%SERVICE%", service));
        }

    	Synergy.broadcastMessage("<lang>synergy-player-voted<arg>"+bread.getName()+"</arg></lang>", bread);
    	
    	Synergy.createSynergyEvent("discord-embed").setPlayerUniqueId(bread.getUniqueId())
    	.setOption("chat", "global")
    	.setOption("color", "#55efc4")
    	.setOption("author", Synergy.translate("<lang>synergy-player-voted<arg>"+username+"</arg></lang>", Translation.getDefaultLanguage())
            	.setPlaceholders(bread)
                .setEndings(bread.getPronoun())
                .getStripped())
    	.send();
    	
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onVotifierEvent(VotifierEvent event) {
        Vote vote = event.getVote();
        Synergy.createSynergyEvent("votifier").setOption("service", vote.getServiceName()).setOption("username", vote.getUsername()).send();
    }
}