package me.synergy.discord;

import java.util.List;
import java.util.UUID;

import me.synergy.brains.Synergy;
import me.synergy.integrations.LuckPermsAPI;
import me.synergy.modules.Config;
import me.synergy.modules.Module;
import me.synergy.modules.Tasks;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GenericGuildMemberEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class RolesHandler extends ListenerAdapter implements Module {

    private static volatile boolean enabled;
    private AutoCloseable subscription;

    @Override public String name() { return "Roles"; }
    @Override public Side side() { return Side.HUB; }
    @Override public String toggle() { return "discord.roles.enabled"; }
    @Override public String[] plugins() { return new String[] {"LuckPerms"}; }
    @Override public String[] filled() { return new String[] {"discord.enabled"}; }

    @Override
    public void defaults() {
        Config.addDefault("discord.roles.enabled", false);
        Config.addDefault("discord.roles.from-discord", true);
        Config.addDefault("discord.roles.to-discord", false);
        if (Config.getConfigurationSection("discord.roles.groups").isEmpty()) Config.addDefault("discord.roles.groups.default", "");
    }

    @Override
    public void start() {
        if (Synergy.getDiscord() == null) throw new IllegalStateException("Discord is disabled");
        Synergy.getDiscord().addEventListener(this);
        subscription = LuckPermsAPI.onChange(RolesHandler::sync);
        enabled = true;
    }

    @Override
    public void stop() throws Exception {
        enabled = false;
        subscription.close();
        if (Synergy.getDiscord() != null) Synergy.getDiscord().removeEventListener(this);
    }

    @Override
    public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
        changed(event);
    }

    @Override
    public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent event) {
        changed(event);
    }

    private static void changed(GenericGuildMemberEvent event) {
        UUID uuid = Discord.getUniqueIdByDiscordId(event.getUser().getId());
        if (uuid != null) sync(uuid);
    }

    public static void sync(UUID uuid) {
        if (enabled) Tasks.async(() -> apply(uuid));
    }

    private static void apply(UUID uuid) {
        Guild guild = Discord.getGuild();
        String id = Discord.getDiscordIdByUniqueId(uuid);
        Member member = guild == null || id == null ? null : guild.getMemberById(id);
        List<String> groups = LuckPermsAPI.groups(uuid);
        Config.getConfigurationSection("discord.roles.groups").forEach((group, roleId) -> {
            Role role = guild == null || String.valueOf(roleId).isBlank() ? null : guild.getRoleById(String.valueOf(roleId));
            if (role == null) return;
            boolean hasRole = member != null && member.getRoles().contains(role);
            if (Config.getBoolean("discord.roles.from-discord")) {
                if (hasRole != LuckPermsAPI.member(uuid, group)) LuckPermsAPI.parent(uuid, group, hasRole);
            } else if (Config.getBoolean("discord.roles.to-discord") && member != null && groups.contains(group) != hasRole) {
                if (hasRole) guild.removeRoleFromMember(member, role).queue();
                else guild.addRoleToMember(member, role).queue();
            }
        });
    }
}
