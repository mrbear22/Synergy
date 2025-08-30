package me.synergy.utils;

import me.synergy.brains.Bungee;
import me.synergy.brains.Synergy;
import me.synergy.brains.Velocity;

public class Logger {

  private void opLog(String string) {
    //for (Player p : Bukkit.getOnlinePlayers().stream().filter(p -> p.hasPermission("synergy.sudo")).collect(Collectors.toList())) {
	//	p.sendMessage(ChatColor.GRAY +""+ ChatColor.ITALIC + "[Console] " + string);
	//}
  }

  public void info(String string) {
    info(string, false);
  }

  public void info(String string, boolean broadcast) {
	string = Translation.translate(string, Translation.getDefaultLanguage());
	try {
	    if (Synergy.isRunningSpigot()) {
	      Synergy.getSpigot().getLogger().info(string);
	      if (broadcast) {
	        opLog(string);
	      }
	    }
	    if (Synergy.isRunningVelocity()) {
	      Velocity.getLogger().info(string);
	    }
	    if (Synergy.isRunningBungee()) {
		  Bungee.getInstance().getLogger().info(string);
		}
	} catch (Exception c) {
		System.out.print(string);
	}
  }

  public void warning(String string) {
	string = Translation.translate(string, Translation.getDefaultLanguage());
	try {
	    if (Synergy.isRunningSpigot()) {
	      Synergy.getSpigot().getLogger().warning(string);
	    }
	    if (Synergy.isRunningVelocity()) {
	    	Velocity.getLogger().warn(string);
	    }
	    if (Synergy.isRunningBungee()) {
		  Bungee.getInstance().getLogger().warning(string);
		}
	} catch (Exception c) {
		System.out.print(string);
	}
  }

  public void error(String string) {
    string = Translation.translate(string, Translation.getDefaultLanguage());
	try {
	    if (Synergy.isRunningSpigot()) {
	      Synergy.getSpigot().getLogger().severe(string);
	    }
	    if (Synergy.isRunningVelocity()) {
	    	Velocity.getLogger().error(string);
	    }
	    if (Synergy.isRunningBungee()) {
		  Bungee.getInstance().getLogger().severe(string);
		}
	} catch (Exception c) {
		System.out.print(string);
	}
  }

  public void discord(String string) {
	    Synergy.createSynergyEvent("discord-message").setOption("message", string).setOption("channel", "log").send();
  }

  public void debug(String string) {
	  info(string, false);
  }
  
}
