package me.synergy.discord;

import java.awt.Color;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.time.Duration;

import me.synergy.brains.Synergy;
import me.synergy.modules.Config;
import me.synergy.modules.Locales;
import me.synergy.modules.Module;
import me.synergy.modules.Tasks;
import me.synergy.objects.BreadMaker;
import me.synergy.objects.LocaleBuilder;
import me.synergy.text.Translation;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

public class Discord implements Module {

    private static JDA JDA;
    private static ScheduledFuture<?> activity;
    private static volatile List<String> tags = List.of();
    private static volatile long tagsUpdated;
    
    private static final GatewayIntent[] INTENTS = new GatewayIntent[] {
        GatewayIntent.SCHEDULED_EVENTS, GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MESSAGE_REACTIONS, GatewayIntent.GUILD_VOICE_STATES
    };

    @Override public Side side() { return Side.HUB; }
    @Override public String toggle() { return "discord.enabled"; }
    @Override public String[] filled() { return new String[] {"discord.bot-token", "discord.guild-id"}; }

    @Override
    public void defaults() {
        Locales.addDefault("vote.discord.site", "▶ [%site%](%url%)");
        Locales.addDefault("discord.link.guide", "<primary>Go to Discord <secondary><u>%invite%</u> <primary>and use <secondary><u>/link</u>");
        Locales.addDefault("discord.link.dm.sent", "<success>Check your Discord private messages and confirm the link.");
        Locales.addDefault("discord.link.oauth.url", "<primary>Link your Discord: <click:open_url:'%url%'><hover:show_text:'<locale:hover.click.open>'><secondary><u>click here</u></click>");
        Locales.addDefault("discord.link.confirm", "<primary>Confirm linking to <secondary>%account%<primary>: <click:run_command:/discord confirm><hover:show_text:'<locale:hover.click.select>'><secondary><u>/discord confirm</u></click>");
        Locales.addDefault("discord.link.done", "<success>Linked to <secondary>%account%<success>!");
        Locales.addDefault("discord.unlink.done", "<success>Account unlinked.");
        Locales.addDefault("discord.link.exists", "<danger>Already linked to <secondary>%account%<danger>. Unlink it: <secondary><u>%command%</u>");
        Locales.addDefault("discord.link.missing", "<danger>No linked account. Link it: <secondary><u>%command%</u>");
        Locales.addDefault("discord.link.request.missing", "<danger>Nothing to confirm.");
        Locales.addDefault("discord.link.dm.text", "Confirm linking to the game account `%account%`. Ignore this if it isn't you.");
        Locales.addDefault("discord.link.dm.button", "Confirm");
        Locales.addDefault("discord.link.request.sent", "Now confirm it in the game.");
        Locales.addDefault("discord.link.form.username", "Your Minecraft nickname");
        Locales.addDefault("discord.link.player.unknown", "This player has never played on the server.");
        Locales.addDefault("discord.user.unknown", "Unknown user");
        Locales.addDefault("discord.link.required", "Link your account first: /link");
        Locales.addDefault("discord.link.oauth.expired", "This link has expired. Use /discord link again.");
        Locales.addDefault("discord.service.unavailable", "Service unavailable, try again later.");
        Locales.addDefault("discord.list.title", "Online players");
        Locales.addDefault("discord.list.empty", "nobody");
        Locales.addDefault("discord.timeout.reason", "Discord timeout");
        Locales.addDefault("discord.highlight.thread.title", "Comments");
        Locales.addDefault("discord.post.form.create", "Create a post");
        Locales.addDefault("discord.post.form.edit", "Edit a post");
        Locales.addDefault("discord.post.publish.done", "Published!");
        Locales.addDefault("discord.link.form.title", "Link your Minecraft account");
        Locales.addDefault("discord.unlink.form.title", "Unlink your Minecraft account");
        Locales.addDefault("discord.kick.muted", "You have an active timeout on our Discord server.");
        Locales.addDefault("discord.kick.banned", "You are banned on our Discord server.");
        Locales.addDefault("discord.kick.missing", "You are not on our Discord server.");
        Locales.addDefault("discord.kick.unlinked", "Link your Discord to play on this server: /discord link");
        Locales.addDefault("discord.member.welcome", "Welcome, %NAME%!");
        Locales.addDefault("discord.option.channel", "Channel");
        Locales.addDefault("discord.option.edit", "ID of a message to edit");
        Locales.addDefault("discord.option.title", "Title");
        Locales.addDefault("discord.option.text", "Text");
        Locales.addDefault("discord.option.author", "Author");
        Locales.addDefault("discord.option.image", "Image URL");
        Locales.addDefault("discord.option.color", "#Color");
        Locales.addDefault("discord.option.mention", "Mention");
        Locales.addDefault("discord.option.attachment", "Attachment URL");
        Config.addDefault("discord.enabled", false);
        Config.addDefault("discord.bot-token", "token");
        Config.addDefault("discord.guild-id", "00000000000000000");
        Config.addDefault("discord.oauth.client-id", "");
        Config.addDefault("discord.oauth.client-secret", "");
        Config.addDefault("discord.kick-player.if-muted.enabled", false);
        Config.addDefault("discord.kick-player.if-muted.message", "<locale:discord.kick.muted>");
        Config.addDefault("discord.kick-player.if-banned.enabled", false);
        Config.addDefault("discord.kick-player.if-banned.message", "<locale:discord.kick.banned>");
        Config.addDefault("discord.kick-player.if-missing.enabled", false);
        Config.addDefault("discord.kick-player.if-missing.message", "<locale:discord.kick.missing>");
        Config.addDefault("discord.kick-player.if-has-no-link.enabled", false);
        Config.addDefault("discord.kick-player.if-has-no-link.message", "<locale:discord.kick.unlinked>");
        Config.addDefault("discord.player-join-leave-messages", true);
        Config.addDefault("discord.invite-link", "https://discord.gg/example");
        Config.addDefault("discord.activities", new String[] {"Activity Example", "Another Activity Example"});
        Config.addDefault("discord.avatar-link", "http://example.com:8153/head/%UUID%");
        Config.addDefault("discord.channels.broadcast", "00000000000000000");
        Config.addDefault("discord.channels.log", "00000000000000000");
        Config.addDefault("discord.channels.merge-similar-embeds", false);
        Config.addDefault("discord.highlights.enabled", false);
        Config.addDefault("discord.highlights.comments", false);
        Config.addDefault("discord.highlights.channels", new String[] {"00000000000000000"});
        Config.addDefault("discord.highlights.reaction-emoji", "♥");
        Config.addDefault("discord.welcome-message.enabled", false);
        Config.addDefault("discord.welcome-message.text", "<locale:discord.member.welcome>");
        if (Config.getConfigurationSection("discord.welcome-message.buttons").isEmpty()) {
            Config.addDefault("discord.welcome-message.buttons.link.label", "<locale:discord.link.form.title>");
            Config.addDefault("discord.welcome-message.buttons.link.value", "<locale:discord.link.required>");
            Config.addDefault("discord.welcome-message.buttons.link.style", "primary");
            Config.addDefault("discord.welcome-message.buttons.link.emoji", "🔗");
        }
    }

    @Override
    public void start() {
        JDA = botBuilder().addEventListeners(new ListCommand(), new LinkCommand(), new VoteCommand(), new ChatHandler(), new EmbedCommand(), new MembersHandler()).build();
        updateCommands();
        activityStatus();
        activity = Tasks.every(Discord::activityStatus, Duration.ofMinutes(1));
    }

    private static void updateCommands() {
        DefaultMemberPermissions manage = DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE);
        Synergy.getDiscord().updateCommands().addCommands(
            Commands.slash("list", text("discord.list.title")).setGuildOnly(true),
            Commands.slash("vote", text("command.vote")).setGuildOnly(true),
            Commands.slash("link", text("discord.link.form.title")).addOption(OptionType.STRING, "nickname", text("discord.link.form.username")),
            Commands.slash("unlink", text("discord.unlink.form.title")),
            Commands.slash("embed", text("discord.post.form.edit")).setGuildOnly(true).setDefaultPermissions(manage)
                .addOption(OptionType.CHANNEL, "channel", text("discord.option.channel"), true)
                .addOption(OptionType.STRING, "message", text("discord.option.edit")),
            Commands.slash("post", text("discord.post.form.create")).setGuildOnly(true).setDefaultPermissions(manage)
                .addOption(OptionType.STRING, "title", text("discord.option.title"), true)
                .addOption(OptionType.STRING, "text", text("discord.option.text"), true)
                .addOption(OptionType.STRING, "author", text("discord.option.author"))
                .addOption(OptionType.CHANNEL, "channel", text("discord.option.channel"))
                .addOption(OptionType.STRING, "image", text("discord.option.image"))
                .addOption(OptionType.STRING, "color", text("discord.option.color"))
                .addOption(OptionType.STRING, "thumbnail", text("discord.option.image"))
                .addOption(OptionType.MENTIONABLE, "mention", text("discord.option.mention"))
                .addOption(OptionType.STRING, "attachment", text("discord.option.attachment"))
                .addOption(OptionType.STRING, "edit", text("discord.option.edit"))
        ).queue();
    }

    public static String language(String discordId) {
        UUID uuid = getUniqueIdByDiscordId(discordId);
        return uuid != null ? Synergy.getBread(uuid).getLanguage() : Translation.getDefaultLanguage();
    }

    private static String text(String key) {
        return LocaleBuilder.of(key).stripped();
    }

    public static JDABuilder botBuilder() {
        List<String> activities = Config.getStringList("discord.activities");

        JDABuilder builder = JDABuilder.create(
                Config.getString("discord.bot-token"),
                Arrays.asList(INTENTS)
        )
        .enableCache(CacheFlag.MEMBER_OVERRIDES, CacheFlag.VOICE_STATE)
        .disableCache(
                CacheFlag.ACTIVITY,
                CacheFlag.CLIENT_STATUS,
                CacheFlag.EMOJI,
                CacheFlag.ONLINE_STATUS,
                CacheFlag.STICKER
        )
        .setStatus(OnlineStatus.ONLINE)
        .setMemberCachePolicy(MemberCachePolicy.ALL)
        .setBulkDeleteSplittingEnabled(true);

        if (!activities.isEmpty()) {
            String status = activities.get(0);
            status = Synergy.platform().placeholders(null, status);
            status = status.replace("%online%", String.valueOf(Synergy.platform().online().size()));
            builder.setActivity(Activity.customStatus(status));
        }

        return builder;
    }

    @Override
    public void stop() {
        if (activity != null) activity.cancel(false);
        if (getJda() != null) {
            for (Object listener : getJda().getRegisteredListeners()) {
                getJda().removeEventListener(listener);
            }
            getJda().shutdownNow();
        }
    }
    
    private static void activityStatus() {
        List<String> activities = Config.getStringList("discord.activities");
        if (activities == null || activities.isEmpty()) return;
        String status = activities.get((int) (System.currentTimeMillis() / 60_000 % activities.size()));
        status = Synergy.platform().placeholders(null, status).replace("%online%", String.valueOf(Synergy.platform().online().size()));
        JDA.getPresence().setActivity(Activity.customStatus(status));
    }

    public static Guild getGuild() {
    	return JDA.getGuildById(Config.getString("discord.guild-id"));
    }
    
    public static Member getMember(BreadMaker bread) {
        if (!bread.getData("discord").isSet()) {
            return null;
        }
        try {
            return getGuild().getMemberById(bread.getData("discord").getAsString());
        } catch (Exception e) {
            Synergy.getLogger().warning("Error while receiving a user: " + e.getMessage());
            return null;
        }
    }
    
    public static List<String> getUsersTags() {
        if (System.currentTimeMillis() - tagsUpdated >= 60_000) {
            tagsUpdated = System.currentTimeMillis();
            Synergy.event("discord-tags").toProxy().request().thenAccept(r -> tags = List.of(r.getOption("tags").getAs(String[].class)));
        }
        return tags;
    }

    public static String getDiscordIdByUniqueId(UUID uuid) {
        BreadMaker bread = Synergy.getBread(uuid);
        String discord = bread.getData("discord").getAsString();
        return (discord == null || discord.isEmpty()) ? null : discord;
    }
    
    public static UUID getUniqueIdByDiscordId(String id) {
    	return Synergy.findUserUUID("discord", id);
    }
    
    public static MessageEmbed info(String message) {
    	EmbedBuilder embed = new EmbedBuilder();
    	embed.setColor(Color.decode("#3498db"));
    	embed.setTitle(message);
    	return embed.build();
    }

    public static MessageEmbed warning(String message) {
    	EmbedBuilder embed = new EmbedBuilder();
    	embed.setColor(Color.decode("#f39c12"));
    	embed.setTitle(message);
    	return embed.build();
    }
    
    public static boolean isBanned(BreadMaker bread) {
        String id = bread.getData("discord").getAsString();
        if (id == null || getGuild() == null) return false;
        try {
            getGuild().retrieveBan(UserSnowflake.fromId(id)).complete();
            return true;
        } catch (ErrorResponseException e) {
            return false;
        } catch (Exception e) {
            Synergy.getLogger().warning("Discord ban check failed: " + e.getMessage());
            return false;
        }
    }

    public static String name(String discordId) {
        User user = discordId == null || JDA == null ? null : JDA.getUserById(discordId);
        return user != null ? user.getEffectiveName() : LocaleBuilder.of("discord.user.unknown").stripped();
    }

    public static boolean isMissing(BreadMaker bread) {
        if (!bread.getData("discord").isSet()) {
            return false;
        }
        Member member = getMember(bread);
        return member == null;
    }

    public static boolean isMuted(BreadMaker bread) {
        Member member = getMember(bread);
        if (member == null) return false;
        try {
            OffsetDateTime timeoutEnd = member.getTimeOutEnd();
            return timeoutEnd != null && timeoutEnd.isAfter(OffsetDateTime.now());
        } catch (PermissionException e) {
            Synergy.getLogger().warning("Insufficient rights to check mute: " + e.getMessage());
        } catch (Exception e) {
            Synergy.getLogger().warning("Error during mute check: " + e.getMessage());
        }
        return false;
    }
    
    public static JDA getJda() {
        return JDA;
    }

}