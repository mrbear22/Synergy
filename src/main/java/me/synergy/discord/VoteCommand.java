package me.synergy.discord;

import java.awt.Color;
import java.net.URI;
import java.net.URISyntaxException;

import me.synergy.brains.Synergy;
import me.synergy.utils.Translation;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class VoteCommand extends ListenerAdapter {

	public VoteCommand() {
		try {
	        if (!Synergy.getConfig().getBoolean("discord.enabled")) {
	            return;
	        }

	        Synergy.getLogger().info(String.valueOf(getClass().getSimpleName()) + " module has been initialized!");
	    } catch (Exception exception) {
	        Synergy.getLogger().error(String.valueOf(getClass().getSimpleName()) + " module failed to initialize: " + exception.getMessage());
	    	exception.printStackTrace();
	    }
	}
	
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
    	if (!event.getName().equalsIgnoreCase("vote")) {
    		return;
    	}
    	String language = Discord.getUniqueIdByDiscordId(event.getUser().getId()) != null ? Synergy.getBread(Discord.getUniqueIdByDiscordId(event.getUser().getId())).getLanguage() : Translation.getDefaultLanguage();
    	EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(Synergy.translate("<lang>vote-monitorings</lang>", language).getStripped());
        StringBuilder links = new StringBuilder();
        for (String link : Synergy.getConfig().getStringList("votifier.monitorings")) {
        	try {
            	String domain = new URI(link).getHost();
        		String format = Synergy.translate("<lang>vote-monitorings-format-stripped</lang>", language).getStripped().replace("%MONITORING%", domain).replace("%URL%", link) + "\n";
				links.append(format);
			} catch (URISyntaxException e) {e.printStackTrace();}
        }
        embed.setDescription(links);
        embed.setColor(Color.decode("#f1c40f"));
        event.replyEmbeds(embed.build()).queue();
    }
}
