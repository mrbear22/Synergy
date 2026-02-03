package me.synergy.discord;

import java.awt.Color;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import me.synergy.anotations.SynergyHandler;
import me.synergy.anotations.SynergyListener;
import me.synergy.brains.Synergy;
import me.synergy.events.SynergyEvent;
import me.synergy.text.Translation;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class ChatHandler extends ListenerAdapter implements SynergyListener {

	public ChatHandler() {
        try {
	        if (!Synergy.getConfig().getBoolean("discord.enabled")) {
	            return;
	        }
	        
	        Synergy.getEventManager().registerEvents(this);

	        Synergy.getLogger().info(String.valueOf(getClass().getSimpleName()) + " module has been initialized!");
	    } catch (Exception exception) {
	        Synergy.getLogger().error(String.valueOf(getClass().getSimpleName()) + " module failed to initialize: " + exception.getMessage());
	    	exception.printStackTrace();
	    }
	}
	
	public static MessageEmbed createEmbed(SynergyEvent event) {
        EmbedBuilder embedBuilder = new EmbedBuilder();
        if (event.getOption("title").isSet()) {
            embedBuilder.setTitle(event.getOption("title").getAsString());
        }
        if (event.getOption("color").isSet()) {
            String colorHex = event.getOption("color").getAsString();
            embedBuilder.setColor(Color.decode(colorHex));
        }
        if (event.getOption("author").isSet()) {
	        String avatar = Synergy.getConfig().getString("discord.avatar-link");
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
            embedBuilder.setAuthor(event.getOption("author").getAsString(), null, avatar);
        }
        if (event.getOption("description").isSet()) {
            embedBuilder.setDescription(event.getOption("description").getAsString());
        }
        if (event.getOption("footer").isSet()) {
            embedBuilder.setFooter(event.getOption("footer").getAsString());
        }
        if (event.getOption("image").isSet()) {
            embedBuilder.setImage(event.getOption("image").getAsString());
        }
        if (event.getOption("thumbnail").isSet()) {
            embedBuilder.setThumbnail(event.getOption("thumbnail").getAsString());
        }
        return embedBuilder.build();
    }
	
    @SynergyHandler
    public void onSynergyEvent(SynergyEvent event) {
    	
    	if (event.getIdentifier().equals("discord-message")) {
        	String channel = event.getOption("channel").isSet() ?  Synergy.getConfig().getString("discord.channels."+event.getOption("channel").getAsString()) : null;
        	
            if (channel == null || channel.isEmpty() || channel.length() != 19) {
            	return;
            }
            
            Synergy.getDiscord().getTextChannelById(channel).sendMessage(event.getOption("message").getAsString()).queue();
    	}
    	
        if (event.getIdentifier().equals("discord-embed")) {
        	String channel = event.getOption("channel").isSet() ? event.getOption("channel").getAsString() : Synergy.getConfig().getString("discord.channels.broadcast");
        	
            if (channel == null || channel.isEmpty() || channel.length() != 19) {
            	return;
            }
  
            MessageEmbed embed = createEmbed(event);
            if (Synergy.getConfig().getBoolean("discord.channels.merge-similar-embeds")) {
                try {
                    Message message = Synergy.getDiscord().getTextChannelById(channel)
                            .retrieveMessageById(Synergy.getDiscord().getTextChannelById(channel).getLatestMessageId())
                            .complete();
                    if ((ChronoUnit.MILLIS.between(message.getTimeCreated().toInstant(), Instant.now()) < 5 * 60 * 1000)
                        && message.getEmbeds().stream().anyMatch(e -> e.getTitle().contains(embed.getTitle())
                        		|| e.getAuthor().equals(embed.getAuthor())
                        		|| e.getDescription().contains(embed.getDescription()))) {
                            List<MessageEmbed> embeds = new ArrayList<>(message.getEmbeds());
                            embeds.add(embed);
                            message.editMessageEmbeds(embeds).queue();
                            return;
                     }
                } catch (Exception e) {}
            }
            Synergy.getDiscord().getTextChannelById(channel).sendMessageEmbeds(embed).queue();
        }
    }
	
    @Override
	public void onMessageReceived(MessageReceivedEvent event) {
        Message message = event.getMessage();
        User user = event.getAuthor();
        String channelId = event.getChannel().getId();
        
        if (event.getAuthor().isBot()) {
        	return;	
        }

        Synergy.createSynergyEvent("discord-chat")
		       .setOption("player", user.getEffectiveName())
		       .setOption("discord-channel-id", channelId)
		       .setOption("message", message.getContentDisplay())
		       .setOption("discord-user-id", user.getId())
		       .send();

        if (Synergy.getConfig().getBoolean("discord.hightlights.enabled") &&
            Synergy.getConfig().getStringList("discord.hightlights.channels").contains(channelId) &&
            message.getAttachments().size() > 0) {
            message.addReaction(Emoji.fromUnicode(Synergy.getConfig().getString("discord.hightlights.reaction-emoji"))).complete();
            message.createThreadChannel(Synergy.translate("<lang>hightlights-comments</lang>", Translation.getDefaultLanguage()).getStripped()).queue();
        }
        return;
        
    }
}
