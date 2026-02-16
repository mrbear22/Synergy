package me.synergy.integrations;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;

import me.synergy.anotations.SynergyListener;
import me.synergy.brains.Synergy;
import net.ess3.api.events.JailStatusChangeEvent;
import net.ess3.api.events.MuteStatusChangeEvent;

public class EssentialsAPI implements Listener, SynergyListener {

    public void initialize() {
		if (Synergy.isDependencyAvailable("Essentials")) {
	        Bukkit.getPluginManager().registerEvents(this, Synergy.getSpigot());
	        Synergy.getEventManager().registerEvents(this);
		}
    }
	
	public static boolean essentialsIsPlayerMuted(String player) {
		if (Synergy.isDependencyAvailable("Essentials")) {
			Essentials essentials = (Essentials) Bukkit.getPluginManager().getPlugin("Essentials");
	        User user = essentials.getUser(player);
	        if (user != null && user.isMuted()) {
	            return true;
	        }
		}
        return false;
	}

	@SuppressWarnings("deprecation")
	@EventHandler
	public void onMute(MuteStatusChangeEvent event) {
		Synergy.event("discord-broadcast").setOption("message", "<lang>essentials-player-muted<arg>"+event.getAffected().getName()+"</arg><arg>"+event.getReason()+"</arg></lang>").send();
	}
	
	@SuppressWarnings("deprecation")
	@EventHandler
	public void onJail(JailStatusChangeEvent event) {
		Synergy.event("discord-broadcast").setOption("message", "<lang>essentials-player-jailed<arg>"+event.getAffected().getName()+"</arg></lang>").send();
	}

}
