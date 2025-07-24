package me.synergy.discord;

import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
                    Bukkit.getOnlinePlayers().forEach(player -> Bukkit.getScheduler().runTaskAsynchronously(Synergy.getSpigot(),
                    		() -> handle(player))
                    );
                }
            }.runTaskTimer(Synergy.getSpigot(), 20, 20 * 60);
        }

        private void handle(Player player) {
        	
            if (!Synergy.getConfig().getBoolean("discord-roles-sync.enabled")) {
                return;
            }
        	
            if (Synergy.getConfig().getBoolean("discord-roles-sync.sync-roles-form-mc-to-discord")) {
                Bukkit.getScheduler().runTaskAsynchronously(Synergy.getSpigot(), () -> {
                    SynergyEvent event = Synergy.createSynergyEvent("sync-roles").setPlayerUniqueId(player.getUniqueId());
                    Set<Entry<String, Object>> roles = Synergy.getConfig().getConfigurationSection("discord-roles-sync.roles").entrySet();
                	String[] groups = Synergy.getSpigot().getPermissions().getPlayerGroups(player);
                    roles.forEach(role -> event.setOption(
                            String.valueOf(role.getValue()),
                            String.valueOf(Arrays.asList(groups).contains(role.getKey()))
                    ));
                    event.send();
                });
            }

            if (Synergy.getConfig().getBoolean("discord-roles-sync.sync-roles-from-discord-to-mc")) {
                Synergy.createSynergyEvent("sync-roles-from-discord-to-mc")
                        .setPlayerUniqueId(player.getUniqueId())
                        .send();
            }
        }

        @EventHandler
        public void onJoin(PlayerJoinEvent event) {
            Bukkit.getScheduler().runTaskAsynchronously(Synergy.getSpigot(), () -> handle(event.getPlayer()));
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
        	//
        }
    }
	
	@SynergyHandler
    public void onSynergyEvent(SynergyEvent event) {
		
		if (event.getIdentifier().equals("sync-roles-from-discord-to-mc") && Synergy.getConfig().getBoolean("discord.enabled")) {
			
			if (Synergy.getDiscord() == null) {
			    Synergy.getLogger().warning("Discord bot is unavailable.");
				return;
			}
			
			String discordId = Discord.getDiscordIdByUniqueId(event.getPlayerUniqueId());
			
			if (discordId == null) {
				Synergy.createSynergyEvent("sync-groups").setPlayerUniqueId(event.getPlayerUniqueId()).send();
				return;
			}
			
			Guild guild = Discord.getGuild();

			if (guild == null) {
			    Synergy.getLogger().warning("Discord guild is not found.");
			    return;
			}

			
			Member member = guild.getMemberById(discordId);

			if (member == null) {
			    Synergy.getLogger().warning("Member with Discord ID " + discordId + " is not found.");
			    return;
			}

			SynergyEvent synergyEvent = Synergy.createSynergyEvent("sync-groups")
			        .setPlayerUniqueId(event.getPlayerUniqueId());

			member.getRoles().forEach(role -> synergyEvent.setOption(role.getId(), "true"));

			synergyEvent.send();
		}
		
		if (event.getIdentifier().equals("sync-groups") && Synergy.getConfig().getBoolean("discord-roles-sync.sync-roles-from-discord-to-mc")) {

		    Player player = Bukkit.getPlayer(event.getPlayerUniqueId());
		    if (player == null) return;

		    List<String> groups = Arrays.asList(Synergy.getSpigot().getPermissions().getPlayerGroups(player));

		    event.getOptions().forEach(role -> {
		        String group = getGroupByRoleId(role.getKey());
		        if (group != null && !groups.contains(group)) {
		            String command = Synergy.getConfig().getString("discord-roles-sync.custom-command-add")
		                            .replace("%PLAYER%", event.getBread().getName())
		                            .replace("%GROUP%", group);
		            Synergy.dispatchCommand(command);
		        }
		    });

		    for (String group : groups) {
		        String role = getRoleIdByGroup(group);
		        if (role != null && role.length() == 19 && !event.getOption(role).isSet()) {
		            String command = Synergy.getConfig().getString("discord-roles-sync.custom-command-remove")
		                            .replace("%PLAYER%", event.getBread().getName())
		                            .replace("%GROUP%", group);
		            Synergy.dispatchCommand(command);
		        }
		    }

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
