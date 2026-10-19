package me.synergy.discord;

import java.awt.Color;
import java.net.URI;

import me.synergy.modules.Config;
import me.synergy.objects.LocaleBuilder;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class VoteCommand extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equalsIgnoreCase("vote")) return;
        String language = Discord.language(event.getUser().getId());
        String sites = String.join("\n", Config.getStringList("votifier.monitorings").stream()
            .map(url -> LocaleBuilder.of("vote.discord.site").placeholder("site", URI.create(url).getHost().replace("www.", "")).placeholder("url", url).stripped(language)).toList());
        event.replyEmbeds(new EmbedBuilder().setTitle(LocaleBuilder.of("vote.menu.title").stripped(language)).setDescription(sites).setColor(Color.decode("#f1c40f")).build()).queue();
    }
}
