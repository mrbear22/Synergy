package me.synergy.discord;

import java.awt.Color;

import me.synergy.brains.Synergy;
import me.synergy.objects.LocaleBuilder;
import me.synergy.modules.Http;
import me.synergy.text.Translation;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.utils.FileUpload;

public class EmbedCommand extends ListenerAdapter {


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
        TextChannel channel = getTextChannel(event);
        String image = getOptionAsString(event, "image");
        String thumbnail = getOptionAsString(event, "thumbnail");
        String color = getOptionAsString(event, "color", "#a29bfe");
        String messageImage = getOptionAsString(event, "attachment");
        String editMessageId = getOptionAsString(event, "message");

        EmbedBuilder builder = createEmbedBuilder(title, text, author, thumbnail, image, color);

        if (editMessageId == null) {
            if (messageImage != null && !messageImage.isEmpty()) {
                sendMessageWithAttachment(channel, builder, messageImage);
            } else {
                channel.sendMessageEmbeds(builder.build()).queue();
            }
        } else {
            channel.editMessageEmbedsById(editMessageId, builder.build()).queue();
        }

        event.reply(LocaleBuilder.of("discord.post.publish.done").stripped()).setEphemeral(true).queue();
    }

    private void handleEmbedCommand(SlashCommandInteractionEvent event) {
        TextInput titleInput = createTextInput("title", LocaleBuilder.of("discord.option.title").stripped(), TextInputStyle.SHORT, 0, 256, LocaleBuilder.of("discord.option.title").stripped());
        TextInput textInput = createTextInput("text", LocaleBuilder.of("discord.option.text").stripped(), TextInputStyle.PARAGRAPH, 0, 1000, LocaleBuilder.of("discord.option.text").stripped());
        TextInput authorInput = createTextInput("author", LocaleBuilder.of("discord.option.author").stripped(), TextInputStyle.SHORT, 0, 256, LocaleBuilder.of("discord.option.author").stripped(), false);
        TextInput imageInput = createTextInput("image", LocaleBuilder.of("discord.option.image").stripped(), TextInputStyle.SHORT, 0, 256, "https://", false);
        TextInput colorInput = createTextInput("color", LocaleBuilder.of("discord.option.color").stripped(), TextInputStyle.SHORT, 0, 256, "#B48EAD");

        String target = getTextChannel(event).getId() + ":" + getOptionAsString(event, "message", "");
        Modal modal = Modal.create("embed:" + target, getModalTitle(event)).addComponents(
                ActionRow.of(titleInput),
                ActionRow.of(textInput),
                ActionRow.of(authorInput),
                ActionRow.of(imageInput),
                ActionRow.of(colorInput)
        ).build();

        event.replyModal(modal).queue();
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (event.getModalId().startsWith("embed:")) {
            handleModalInteraction(event);
        }
    }

    private void handleModalInteraction(ModalInteractionEvent event) {
        String[] target = event.getModalId().split(":", -1);
        TextChannel channel = event.getJDA().getTextChannelById(target[1]);
        String editMessageId = target[2].isEmpty() ? null : target[2];
        if (channel == null) channel = event.getChannel().asTextChannel();

        EmbedBuilder builder = createEmbedBuilder(
                event.getValue("title") != null ? event.getValue("title").getAsString() : null,
                event.getValue("text") != null ? Synergy.translate(event.getValue("text").getAsString(), Translation.getDefaultLanguage()).getStripped() : null,
                event.getValue("author") != null ? event.getValue("author").getAsString() : null,
                null,
                event.getValue("image") != null ? event.getValue("image").getAsString() : null,
                event.getValue("color") != null ? event.getValue("color").getAsString() : null
        );

        if (editMessageId != null) {
            channel.editMessageEmbedsById(editMessageId, builder.build()).queue();
        } else {
            channel.sendMessageEmbeds(builder.build()).queue();
        }

        event.reply(LocaleBuilder.of("discord.post.publish.done").stripped()).setEphemeral(true).queue();
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
                ? LocaleBuilder.of("discord.post.form.edit").stripped()
                : LocaleBuilder.of("discord.post.form.create").stripped();
    }
    
    private void sendMessageWithAttachment(TextChannel channel, EmbedBuilder builder, String imageUrl) {
        String fileName = imageUrl.substring(imageUrl.lastIndexOf('/') + 1).replaceAll("\\?.*", "");
        String file = fileName.contains(".") ? fileName : fileName + ".png";
        Http.get(imageUrl).async().whenComplete((response, error) -> {
            if (error != null || !response.ok()) channel.sendMessageEmbeds(builder.build()).queue();
            else channel.sendMessageEmbeds(builder.build()).addFiles(FileUpload.fromData(response.body(), file)).queue();
        });
    }
}
