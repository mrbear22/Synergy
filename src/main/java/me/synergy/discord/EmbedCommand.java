package me.synergy.discord;

import java.awt.Color;

import javax.annotation.Nonnull;

import me.synergy.brains.Synergy;
import me.synergy.utils.Translation;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

public class EmbedCommand extends ListenerAdapter {

	public EmbedCommand() {
        if (!Synergy.getConfig().getBoolean("discord.enabled")) {
            return;
        }
	}

	private static TextChannel channel = null;
	private static String editMessageId = null;
	
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equalsIgnoreCase("post")) {
            handlePostCommand(event);
        } else if (event.getName().equalsIgnoreCase("embed")) {
            handleEmbedCommand(event);
        }
    }

    private void handlePostCommand(SlashCommandInteractionEvent event) {
        String title = getOptionAsString(event, "title");
        String text = getOptionAsString(event, "text").replace("\\n", System.lineSeparator());
        String author = getOptionAsString(event, "author");
        channel = getTextChannel(event);
        String image = getOptionAsString(event, "image");
        String thumbnail = getOptionAsString(event, "thumbnail");
        String color = getOptionAsString(event, "color", "#a29bfe");
        editMessageId = getOptionAsString(event, "message");

        EmbedBuilder builder = createEmbedBuilder(title, text, author, thumbnail, image, color);

        if (editMessageId == null) {
            channel.sendMessageEmbeds(builder.build()).queue();
        } else {
            channel.retrieveMessageById(editMessageId).complete().editMessageEmbeds(builder.build()).queue();
        }

        event.reply("Published!").setEphemeral(true).queue();
    }

    private void handleEmbedCommand(SlashCommandInteractionEvent event) {
        TextInput titleInput = createTextInput("title", "Title", TextInputStyle.SHORT, 0, 256, "Title");
        TextInput textInput = createTextInput("text", "Text", TextInputStyle.PARAGRAPH, 0, 1000, "Text");
        TextInput authorInput = createTextInput("author", "Author", TextInputStyle.SHORT, 0, 256, "Author", false);
        TextInput imageInput = createTextInput("image", "Image URL", TextInputStyle.SHORT, 0, 256, "URL", false);
        TextInput colorInput = createTextInput("color", "#color", TextInputStyle.SHORT, 0, 256, "#B48EAD");

        Modal modal = Modal.create("embed", getModalTitle(event)).addComponents(
                ActionRow.of(titleInput),
                ActionRow.of(textInput),
                ActionRow.of(authorInput),
                ActionRow.of(imageInput),
                ActionRow.of(colorInput)
        ).build();

        event.replyModal(modal).queue();
    }

    @Override
    public void onModalInteraction(@Nonnull ModalInteractionEvent event) {
        if (event.getModalId().equals("embed")) {
            handleModalInteraction(event);
        }
    }

    private void handleModalInteraction(ModalInteractionEvent event) {
        if (channel == null) {
            channel = event.getChannel().asTextChannel();
        }
        
        EmbedBuilder builder = createEmbedBuilder(
                event.getValue("title") != null ? event.getValue("title").getAsString() : null,
                event.getValue("text") != null ? Synergy.translate(event.getValue("text").getAsString(), Translation.getDefaultLanguage()).getStripped() : null,
                event.getValue("author") != null ? event.getValue("author").getAsString() : null,
                null,
                event.getValue("image") != null ? event.getValue("image").getAsString() : null,
                event.getValue("color") != null ? event.getValue("color").getAsString() : null
        );

        if (editMessageId != null) {
            channel.retrieveMessageById(editMessageId).complete().editMessageEmbeds(builder.build()).queue();
            editMessageId = null;
        } else {
            channel.sendMessageEmbeds(builder.build()).queue();
        }

        event.reply("Published!").setEphemeral(true).queue();
    }

    private String getOptionAsString(SlashCommandInteractionEvent event, String optionName) {
        return event.getOption(optionName) != null ? event.getOption(optionName).getAsString() : null;
    }

    private String getOptionAsString(SlashCommandInteractionEvent event, String optionName, String defaultValue) {
        return event.getOption(optionName) != null ? event.getOption(optionName).getAsString() : defaultValue;
    }

    private TextChannel getTextChannel(SlashCommandInteractionEvent event) {
        return event.getOption("channel") != null 
                ? event.getOption("channel").getAsChannel().asTextChannel() 
                : event.getChannel().asTextChannel();
    }

    private EmbedBuilder createEmbedBuilder(String title, String text, String author, String thumbnail, String image, String color) {
        EmbedBuilder builder = new EmbedBuilder();
        if (author != null) {
            builder.setAuthor(author, null, "https://minotar.net/helm/" + author);
        }
        if (title != null) {
            builder.setTitle(title);
        }
        if (text != null) {
            builder.setDescription(text);
        }
        if (thumbnail != null) {
            builder.setThumbnail(thumbnail);
        }
        if (image != null) {
            builder.setImage(image);
        }
        if (color != null) {
            builder.setColor(Color.decode(color));
        }
        return builder;
    }

    private TextInput createTextInput(String id, String label, TextInputStyle style, int minLength, int maxLength, String placeholder) {
        return TextInput.create(id, label, style)
                .setPlaceholder(placeholder)
                .setMinLength(minLength)
                .setMaxLength(maxLength)
                .build();
    }

    private TextInput createTextInput(String id, String label, TextInputStyle style, int minLength, int maxLength, String placeholder, boolean required) {
        return TextInput.create(id, label, style)
                .setPlaceholder(placeholder)
                .setMinLength(minLength)
                .setMaxLength(maxLength)
                .setRequired(required)
                .build();
    }

    private String getModalTitle(SlashCommandInteractionEvent event) {
        return event.getOption("message") != null 
                ? Synergy.translate("<lang>discord-embed-edit</lang>", Translation.getDefaultLanguage()).getStripped()
                : Synergy.translate("<lang>discord-embed-new</lang>", Translation.getDefaultLanguage()).getStripped();
    }
    
/*
    public void balance(SlashCommandInteractionEvent event) {
    	if (Synergy.getDiscord().getUniqueIdByDiscordId(event.getUser().getId()) != null) {
        	BreadMaker bread = Synergy.getBread(Synergy.getDiscord().getUniqueIdByDiscordId(event.getUser().getId()));
			OfflinePlayer player = Bukkit.getOfflinePlayer(Synergy.getDiscord().getUniqueIdByDiscordId(event.getUser().getId()));
	    	double balance = Synergy.getSpigot().getEconomy().getBalance(player);
	    	EmbedBuilder embed = new EmbedBuilder();
	    	embed.addField(Synergy.translate("<lang>vault-balance-title</lang>", bread.getLanguage()).getStripped(), Synergy.translate("<lang>vault-balance-field</lang>", bread.getLanguage()).getStripped().replace("%AMOUNT%", String.valueOf((int) balance)), true);
	    	embed.setThumbnail("https://minotar.net/helm/"+Synergy.getBread(Synergy.getDiscord().getUniqueIdByDiscordId(event.getUser().getId())).getName());
	    	embed.setColor(Color.decode("#f1c40f"));
	    	embed.setFooter(Synergy.translate("<lang>vault-balance-footer</lang>", bread.getLanguage()).getStripped());
	    	event.replyEmbeds(embed.build()).queue();
    	} else {
    		event.replyEmbeds(warning(Synergy.translate("<lang>you-have-to-link-account</lang>", Translation.getDefaultLanguage()).getStripped())).queue();
    	}
	}*/
}
