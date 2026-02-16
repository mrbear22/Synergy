package me.synergy.discord;

import java.awt.Color;
import java.net.URI;
import java.net.URISyntaxException;

import me.synergy.brains.Synergy;
import me.synergy.modules.Locales;
import me.synergy.text.Translation;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class VoteCommand extends ListenerAdapter {

	public VoteCommand() {
		try {
	        if (!Synergy.getConfig().getBoolean("discord.enabled")) {
	            return;
	        }
	        
	        Locales.addDefault("command_description_vote", "en", "Vote for the server");
	        Locales.addDefault("command_usage_vote", "en", new String[] {
			    "<danger>Usage: /vote",
			    "",
			    "<secondary>Vote for our server to receive:",
			    "<primary>• <secondary>Experience points and currency",
			    "<primary>• <secondary>Special voting rewards",
			    "<primary>• <secondary>Help the server grow"
			});

	        Locales.addDefault("votifier-message", "en", "<success>Vote from %service% counted. Thank you!<sound:'entity.player.levelup'>");
	        Locales.addDefault("votifier-announcement", "en", "<success>%player% successfully voted on %service%!");
	        Locales.addDefault("vote-for-server", "en", "Vote for the server");
	        Locales.addDefault("vote-monitorings", "en", "<secondary>Earn by voting for the server:");
	        Locales.addDefault("player-voted", "en", "<primary>%ARGUMENT% <secondary>voted for the server!");
	        
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
