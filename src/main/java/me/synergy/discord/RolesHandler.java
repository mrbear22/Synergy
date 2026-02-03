package me.synergy.discord;

import java.util.*;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.guild.member.*;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class RolesHandler extends ListenerAdapter implements SynergyListener {
	
	public void initialize() {
	    try {
	        Synergy.getEventManager().registerEvents(this);
	        if (Synergy.isRunningSpigot()) {
	            new PlayerListener().initialize();
	        }
	        Synergy.getLogger().info(getClass().getSimpleName() + " module has been initialized!");
	    } catch (Exception e) {
	        Synergy.getLogger().error(getClass().getSimpleName() + " module failed to initialize: " + e.getMessage());
	        e.printStackTrace();
	    }
	}
	
    @Override
	public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
        handleRoleChange(event.getMember().getUser().getId(), event.getGuild().getId());
    }

    @Override
	public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent event) {
        handleRoleChange(event.getMember().getUser().getId(), event.getGuild().getId());
    }

    private void handleRoleChange(String userId, String guildId) {
        UUID uuid = Discord.getUniqueIdByDiscordId(userId);
        if (uuid != null) {
            onSynergyEvent(Synergy.createSynergyEvent("sync-roles-from-discord-to-mc")
                .setPlayerUniqueId(uuid)
                .setOption("guild", guildId));
        }
    }
	
    public static class PlayerListener implements Listener {

        public void initialize() {
            if (!Synergy.getConfig().getBoolean("discord-roles-sync.enabled")) return;
            
            Bukkit.getPluginManager().registerEvents(this, Synergy.getSpigot());
            new BukkitRunnable() {
                @Override
                public void run() {
                    Bukkit.getOnlinePlayers().forEach(player -> 
                        Bukkit.getScheduler().runTaskAsynchronously(Synergy.getSpigot(), () -> handle(player))
                    );
                }
            }.runTaskTimer(Synergy.getSpigot(), 20, 20 * 60);
        }

        private void handle(Player player) {
            if (!Synergy.getConfig().getBoolean("discord-roles-sync.enabled")) return;

            if (Synergy.getConfig().getBoolean("discord-roles-sync.sync-roles-form-mc-to-discord")) {
                Bukkit.getScheduler().runTaskAsynchronously(Synergy.getSpigot(), () -> syncMcToDiscord(player));
            }

            if (Synergy.getConfig().getBoolean("discord-roles-sync.sync-roles-from-discord-to-mc")) {
                Synergy.createSynergyEvent("sync-roles-from-discord-to-mc")
                    .setPlayerUniqueId(player.getUniqueId())
                    .send();
            }
        }

        private void syncMcToDiscord(Player player) {
            SynergyEvent event = Synergy.createSynergyEvent("sync-roles").setPlayerUniqueId(player.getUniqueId());
            Set<Map.Entry<String, Object>> roles = Synergy.getConfig().getConfigurationSection("discord-roles-sync.roles").entrySet();
            List<String> groups = Arrays.asList(Synergy.getSpigot().getPermissions().getPlayerGroups(player));
            
            roles.forEach(role -> event.setOption(
                String.valueOf(role.getValue()),
                String.valueOf(groups.contains(role.getKey()))
            ));
            event.send();
        }

        @EventHandler
        public void onJoin(PlayerJoinEvent event) {
            Bukkit.getScheduler().runTaskAsynchronously(Synergy.getSpigot(), () -> handle(event.getPlayer()));
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {}
    }
	
	@SynergyHandler
    public void onSynergyEvent(SynergyEvent event) {
        switch (event.getIdentifier()) {
            case "sync-roles-from-discord-to-mc":
                if (Synergy.getConfig().getBoolean("discord.enabled")) {
                    syncDiscordToMc(event);
                }
                break;
            case "sync-groups":
                if (Synergy.getConfig().getBoolean("discord-roles-sync.sync-roles-from-discord-to-mc") 
                    && Synergy.isRunningSpigot()) {
                    syncGroups(event);
                }
                break;
            case "sync-roles":
                if (Synergy.getConfig().getBoolean("discord.enabled")) {
                    syncMcRolesToDiscord(event);
                }
                break;
        }
    }

    private void syncDiscordToMc(SynergyEvent event) {
        if (Synergy.getDiscord() == null) return;

        String discordId = Discord.getDiscordIdByUniqueId(event.getPlayerUniqueId());
        if (discordId == null || "0000000000000000000".equals(discordId)) {
            Synergy.createSynergyEvent("sync-groups").setPlayerUniqueId(event.getPlayerUniqueId()).send();
            return;
        }

        Guild guild = Discord.getGuild();
        if (guild == null) return;

        Member member = guild.getMemberById(discordId);
        if (member == null) return;

        SynergyEvent synergyEvent = Synergy.createSynergyEvent("sync-groups")
            .setPlayerUniqueId(event.getPlayerUniqueId());
        member.getRoles().forEach(role -> synergyEvent.setOption(role.getId(), "true"));
        synergyEvent.send();
    }

    private void syncGroups(SynergyEvent event) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(event.getPlayerUniqueId());
        if (player == null) return;

        List<String> groups = Arrays.asList(Synergy.getSpigot().getPermissions()
            .getPlayerGroups(Bukkit.getWorlds().get(0).getName(), player));

        event.getOptions().forEach(role -> {
            String group = getGroupByRoleId(role.getKey());
            if (group != null && !groups.contains(group)) {
                executeCommand("discord-roles-sync.custom-command-add", event.getBread().getName(), group);
            }
        });

        groups.forEach(group -> {
            String role = getRoleIdByGroup(group);
            if (role != null && role.length() == 19 && !event.getOption(role).isSet()) {
                executeCommand("discord-roles-sync.custom-command-remove", event.getBread().getName(), group);
            }
        });
    }

    private void syncMcRolesToDiscord(SynergyEvent event) {
        String userId = Discord.getDiscordIdByUniqueId(event.getPlayerUniqueId());
        if (userId == null) return;

        event.getOptions().forEach(option -> {
            try {
                Role role = Synergy.getDiscord().getRoleById(option.getKey());
                if (role == null) return;

                Guild guild = role.getGuild();
                Member member = guild.getMemberById(userId);
                if (member == null) return;

                boolean shouldHaveRole = option.getValue().getAsBoolean();
                boolean hasRole = member.getRoles().contains(role);

                if (shouldHaveRole && !hasRole) {
                    guild.addRoleToMember(member, role).queue();
                } else if (!shouldHaveRole && hasRole) {
                    guild.removeRoleFromMember(member, role).queue();
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    private void executeCommand(String configKey, String playerName, String group) {
        String command = Synergy.getConfig().getString(configKey)
            .replace("%PLAYER%", playerName)
            .replace("%GROUP%", group);
        Synergy.dispatchCommand(command);
    }

    public static String getRoleIdByGroup(String group) {
        return Synergy.getConfig().getString("discord-roles-sync.roles." + group);
    }

    public static String getGroupByRoleId(String id) {
        return Synergy.getConfig().getConfigurationSection("discord-roles-sync.roles").keySet().stream()
            .filter(r -> Synergy.getConfig().getString("discord-roles-sync.roles." + r).equals(id))
            .findFirst()
            .orElse(null);
    }
}