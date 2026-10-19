package me.synergy.discord;

import java.awt.Color;

import me.synergy.brains.Synergy;
import me.synergy.objects.LocaleBuilder;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class ListCommand extends ListenerAdapter {


    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
    	if (!event.getName().toLowerCase().equals("list")) {
    		return;
    	}
    	
        EmbedBuilder builder = new EmbedBuilder();

        String language = Discord.language(event.getUser().getId());
        String title = LocaleBuilder.of("discord.list.title").stripped(language);
        builder.setTitle(title);
        builder.setColor(Color.decode("#a29bfe"));

        StringBuilder list = new StringBuilder();

        Synergy.platform().playersByServer().forEach((server, players) -> list
            .append("\n**").append(server).append(": **")
            .append(players.isEmpty() ? LocaleBuilder.of("discord.list.empty").stripped(language) : String.join(", ", players)));

        builder.setDescription(list.toString());
        event.replyEmbeds(builder.build()).queue();
    }
}
