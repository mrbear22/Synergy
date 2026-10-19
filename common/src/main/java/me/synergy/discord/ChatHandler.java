package me.synergy.discord;

import me.synergy.modules.DataManager;
import java.awt.Color;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import me.synergy.anotations.SynergyHandler;
import me.synergy.anotations.SynergyListener;
import me.synergy.brains.Synergy;
import me.synergy.events.SynergyEvent;
import me.synergy.modules.Config;
import me.synergy.modules.PunishmentsManager;
import me.synergy.objects.Locale;
import me.synergy.objects.LocaleBuilder;
import me.synergy.text.Translation;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.audit.AuditLogChange;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.audit.AuditLogKey;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.guild.GuildAuditLogEntryCreateEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class ChatHandler extends ListenerAdapter implements SynergyListener {

    public ChatHandler() {
        Synergy.getEventManager().registerEvents(this);
    }
	
	public static MessageEmbed createEmbed(SynergyEvent event) {
        EmbedBuilder embedBuilder = new EmbedBuilder();
        if (event.getOption("title").isSet()) {
            embedBuilder.setTitle(new Locale(event.getOption("title").getAsString(), Translation.getDefaultLanguage()).setGendered(null).getStripped());
        }
        if (event.getOption("color").isSet()) {
            String colorHex = event.getOption("color").getAsString();
            embedBuilder.setColor(Color.decode(colorHex));
        }
        if (event.getOption("author").isSet()) {
	        String avatar = event.getOption("author-icon").isSet() ? event.getOption("author-icon").getAsString() : Config.getString("discord.avatar-link");
        	if (event.getPlayerUniqueId() != null) {
        		avatar = avatar.replace("%UUID%", event.getPlayerUniqueId().toString());
	        }
	        if (event.getOption("player").isSet()) {
	        	avatar = avatar.replace("%PLAYER%", event.getOption("player").getAsString());
	        }
	        if (event.getOption("uuid").isSet()) { 
	        	avatar = avatar.replace("%UUID%", event.getOption("uuid").getAsString());
	        }
	        if (event.getOption("avatar").isSet()) { 
	        	avatar = event.getOption("avatar").getAsString();
	        	avatar = avatar.equals("%self%") ? Synergy.getDiscord().getSelfUser().getAvatarUrl() : avatar;
	        }
            embedBuilder.setAuthor(new Locale(event.getOption("author").getAsString(), Translation.getDefaultLanguage()).setGendered(null).getStripped(), event.getOption("author-url").getAsString(), avatar);
        }
        if (event.getOption("description").isSet()) {
            embedBuilder.setDescription(new Locale(event.getOption("description").getAsString(), Translation.getDefaultLanguage()).setGendered(null).getStripped());
        }
        if (event.getOption("footer").isSet()) {
            embedBuilder.setFooter(new Locale(event.getOption("footer").getAsString(), Translation.getDefaultLanguage()).setGendered(null).getStripped(), event.getOption("footer-icon").getAsString());
        }
        if (event.getOption("url").isSet()) {
            embedBuilder.setUrl(event.getOption("url").getAsString());
        }
        if (Boolean.TRUE.equals(event.getOption("timestamp").getAsBoolean())) {
            embedBuilder.setTimestamp(Instant.now());
        }
        if (event.getOption("image").isSet()) {
            embedBuilder.setImage(event.getOption("image").getAsString());
        }
        if (event.getOption("thumbnail").isSet()) {
            embedBuilder.setThumbnail(event.getOption("thumbnail").getAsString());
        }
        return embedBuilder.build();
    }
	
    @SynergyHandler("discord-message")
    public void onMessage(SynergyEvent event) {
        TextChannel target = channel(event.getOption("channel").isSet() ? Config.getString("discord.channels." + event.getOption("channel").getAsString()) : null);
        if (target != null) target.sendMessage(event.getOption("message").getAsString()).queue();
    }

    @SynergyHandler("discord-embed")
    public void onEmbed(SynergyEvent event) {
        TextChannel target = channel(event.getOption("channel").isSet() ? event.getOption("channel").getAsString() : Config.getString("discord.channels.broadcast"));
        if (target == null) return;
        MessageEmbed embed = createEmbed(event);
        if (!Config.getBoolean("discord.channels.merge-similar-embeds") || target.getLatestMessageIdLong() == 0 || embed.getAuthor() == null) {
            target.sendMessageEmbeds(embed).queue();
            return;
        }
        target.retrieveMessageById(target.getLatestMessageId()).queue(last -> {
            boolean merge = ChronoUnit.MILLIS.between(last.getTimeCreated().toInstant(), Instant.now()) < 300_000 && last.getEmbeds().size() < 10
                && last.getAuthor().equals(target.getJDA().getSelfUser())
                && last.getEmbeds().stream().anyMatch(e -> e.getAuthor() != null && Objects.equals(e.getAuthor().getName(), embed.getAuthor().getName()));
            if (!merge) {
                target.sendMessageEmbeds(embed).queue();
                return;
            }
            List<MessageEmbed> embeds = new ArrayList<>(last.getEmbeds());
            embeds.add(embed);
            last.editMessageEmbeds(embeds).queue();
        }, error -> target.sendMessageEmbeds(embed).queue());
    }

    private static TextChannel channel(String id) {
        if (id == null || !id.matches("\\d{17,20}") || id.matches("0+") || Synergy.getDiscord() == null) return null;
        TextChannel channel = Synergy.getDiscord().getTextChannelById(id);
        if (channel == null) Synergy.getLogger().warning("Discord channel " + id + " not found");
        return channel;
    }

    @Override
	public void onMessageReceived(MessageReceivedEvent event) {
        Message message = event.getMessage();
        User user = event.getAuthor();
        String channelId = event.getChannel().getId();
        
        if (event.getAuthor().isBot()) {
        	return;	
        }

        UUID uuid = resolveLinkedUUID(user.getId());

        if (uuid != null && Synergy.getBread(uuid).isMuted()) {
            message.delete().queue(success -> {}, failure -> {});

            Synergy.event("discord-embed").toProxy()
                .setOption("channel", channelId)
                .setOption("title", LocaleBuilder.of("chat.mute.active")
                    .placeholder("reason", Synergy.getBread(uuid).getMuteReason())
                    .build())
                .setOption("color", "#fab1a0")
                .send();
            return;
        }

        Synergy.event("discord-chat")
		       .setOption("player", user.getEffectiveName())
		       .setOption("discord-channel-id", channelId)
		       .setOption("message", message.getContentDisplay())
		       .setOption("discord-user-id", user.getId())
		       .send();

        if (Config.getBoolean("discord.highlights.enabled") &&
            Config.getStringList("discord.highlights.channels").contains(channelId) &&
            message.getAttachments().size() > 0) {
            message.addReaction(Emoji.fromUnicode(Config.getString("discord.highlights.reaction-emoji")))
                .flatMap(v -> message.createThreadChannel(LocaleBuilder.of("discord.highlight.thread.title").stripped())).queue();
        }
        return;
        
    }
    
    @Override
    public void onGuildAuditLogEntryCreate(GuildAuditLogEntryCreateEvent event) {
        AuditLogEntry entry = event.getEntry();
        AuditLogChange change = entry.getChangeByKey(AuditLogKey.MEMBER_TIME_OUT);
        if (change == null) return;

        UUID uuid = resolveLinkedUUID(entry.getTargetId());
        if (uuid == null) return;

        var bread = Synergy.getBread(uuid);
        String until = change.getNewValue();
        long durationMillis = until == null ? 0
            : OffsetDateTime.parse(until).toInstant().toEpochMilli() - System.currentTimeMillis();

        if (durationMillis <= 0) {
            PunishmentsManager.unmute(bread);
            return;
        }

        User actor = entry.getUser();
        String reason = entry.getReason() == null || entry.getReason().isBlank()
            ? "<locale:discord.timeout.reason>" : entry.getReason();

        PunishmentsManager.mute(bread, durationMillis, reason,
            actor != null ? "@" + actor.getEffectiveName() : "Discord");
    }

    private UUID resolveLinkedUUID(String discordUserId) {
        return DataManager.findUserUUID("discord", discordUserId);
    }
}