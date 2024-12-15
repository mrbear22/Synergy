package me.synergy.discord;

import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;

import me.synergy.anotations.SynergyHandler;
import me.synergy.anotations.SynergyListener;
import me.synergy.brains.Synergy;
import me.synergy.events.SynergyEvent;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class RolesHandler extends ListenerAdapter implements SynergyListener {
	
	public void initialize() {
	    try {
	        Synergy.getEventManager().registerEvents(this);
	    	
	        if (Synergy.isRunningSpigot()) {
	        	new PlayerListener().initialize();
	        }
	        
	        Synergy.getLogger().info(String.valueOf(getClass().getSimpleName()) + " module has been initialized!");
	    } catch (Exception exception) {
	        Synergy.getLogger().error(String.valueOf(getClass().getSimpleName()) + " module failed to initialize: " + exception.getMessage());
	    	exception.printStackTrace();
	    }
	}
	
    @Override
	public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
		String userId = event.getMember().getId();
		UUID uuid = Discord.getUniqueIdByDiscordId(userId);
		if (uuid != null) {
			Synergy.createSynergyEvent("sync-roles-from-discord-to-mc").setPlayerUniqueId(uuid).setOption("guild", event.getGuild().getId()).fireEvent();
		}
    }

    @Override
	public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent event) {
		String userId = event.getMember().getId();
		UUID uuid = Discord.getUniqueIdByDiscordId(userId);
		if (uuid != null) {
			Synergy.createSynergyEvent("sync-roles-from-discord-to-mc").setPlayerUniqueId(uuid).setOption("guild", event.getGuild().getId()).fireEvent();
		}
    }
	
	public static class PlayerListener implements Listener {
		
		public void initialize() {
			if (!Synergy.getConfig().getBoolean("discord-roles-sync.enabled")) {
				return;
			}
			Bukkit.getPluginManager().registerEvents(this, Synergy.getSpigot());
	        new BukkitRunnable() {
	            @Override
	            public void run() {
	                Bukkit.getOnlinePlayers().forEach(player -> handle(player));
	            }
	        }.runTaskTimer(Synergy.getSpigot(), 0, 20 * 60);
		}
		
		private void handle(Player player) {
	        if (!Synergy.getBread(player.getUniqueId()).getData("discord").isSet()) {
	        	return;
	        }
	        
	        if (Synergy.getConfig().getBoolean("discord-roles-sync.sync-roles-form-mc-to-discord")) {
	        	SynergyEvent event = Synergy.createSynergyEvent("sync-roles").setPlayerUniqueId(player.getUniqueId());
	        	Set<Entry<String, Object>> roles = Synergy.getConfig().getConfigurationSection("discord-roles-sync.roles").entrySet();
	        	String[] groups = Synergy.getSpigot().getPermissions().getPlayerGroups(player);
	        	
	        	roles.forEach(role -> {
    				event.setOption(String.valueOf(role.getValue()), String.valueOf(Arrays.asList(groups).contains(role.getKey())));
	        	});
	        	
	        	event.send();
	        }

	        if (Synergy.getConfig().getBoolean("discord-roles-sync.sync-roles-from-discord-to-mc")) {
	            Synergy.createSynergyEvent("sync-roles-from-discord-to-mc").setPlayerUniqueId(player.getUniqueId()).setOption("default", Synergy.getConfig().getString("discord-roles-sync.roles.default")).send();
	        }
		}

	}
	
	@SynergyHandler
    public void onSynergyEvent(SynergyEvent event) {
		
		if (event.getIdentifier().equals("sync-roles-from-discord-to-mc") && Synergy.getConfig().getBoolean("discord.enabled")) {
			Guild guild = event.getOption("guild").isSet() ? Synergy.getDiscord().getGuildById(event.getOption("guild").getAsString())
					: Synergy.getDiscord().getRoleById(event.getOption("default").getAsString()).getGuild();
			Member member = guild.getMemberById(Discord.getDiscordIdByUniqueId(event.getPlayerUniqueId()));
			SynergyEvent synergyEvent = Synergy.createSynergyEvent("sync-groups").setPlayerUniqueId(event.getPlayerUniqueId());
			
			member.getRoles().forEach(r -> {
        		synergyEvent.setOption(r.getId(), "true");
			});

			synergyEvent.send();
		}
		
		if (event.getIdentifier().equals("sync-groups") && Synergy.getConfig().getBoolean("discord-roles-sync.sync-roles-from-discord-to-mc")) {
			List<String> groups = Arrays.asList(Synergy.getSpigot().getPermissions().getPlayerGroups(Bukkit.getPlayer(event.getPlayerUniqueId())));
			event.getOptions().forEach(role -> {
				String group = getGroupByRoleId(role.getKey());
				if (group != null && !groups.contains(group)) {
					String command = Synergy.getConfig().getString("discord-roles-sync.custom-command-add")
									.replace("%PLAYER%", event.getBread().getName()).replace("%GROUP%", group);
		        	Synergy.dispatchCommand(command);
				}
			});
			
			groups.forEach(group -> {
			    String role = getRoleIdByGroup(group);
			    if (role != null && !event.getOption(role).isSet()) {
					String command = Synergy.getConfig().getString("discord-roles-sync.custom-command-remove")
									.replace("%PLAYER%", event.getBread().getName()).replace("%GROUP%", group);
		        	Synergy.dispatchCommand(command);
			    }
			});
		}

		if (event.getIdentifier().equals("sync-roles") && Synergy.getConfig().getBoolean("discord.enabled")) {
		    String userId = Discord.getDiscordIdByUniqueId(event.getPlayerUniqueId());
		    
		    if (userId == null) return;
		    
		    event.getOptions().forEach(option -> {
		        try {
		            Role role = Synergy.getDiscord().getRoleById(option.getKey());
		            boolean shouldHaveRole = option.getValue().getAsBoolean();

		            if (role == null) return;

		            Guild guild = role.getGuild();
		            Member member = guild.getMemberById(userId);

		            if (member == null) return;

		            boolean hasRole = member.getRoles().contains(role);

		            if (shouldHaveRole && !hasRole) {
		                guild.addRoleToMember(member, role).queue();
		            } else if (!shouldHaveRole && hasRole) {
		                guild.removeRoleFromMember(member, role).queue();
		            }
		        } catch (Exception ex) {
		            Synergy.getLogger().error(ex.getLocalizedMessage());
		        }
		    });
		}
	}

    public static String getRoleIdByGroup(String group) {
        return Synergy.getConfig().getString("discord-roles-sync.roles." + group);
    }

    public static String getGroupByRoleId(String id) {
        Set<String> roles = Synergy.getConfig().getConfigurationSection("discord-roles-sync.roles").keySet();
        for (String r: roles) {
            if (Synergy.getConfig().getString("discord-roles-sync.roles." + r).equals(id)) {
                return r;
            }
        }
        return null;
    }


}
