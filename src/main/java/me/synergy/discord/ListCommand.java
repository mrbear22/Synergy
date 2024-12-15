package me.synergy.discord;

import java.awt.Color;
import java.util.List;
import java.util.stream.Collectors;

import me.synergy.brains.Bungee;
import me.synergy.brains.Synergy;
import me.synergy.objects.BreadMaker;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class ListCommand extends ListenerAdapter {

	public ListCommand() {
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
    	if (!event.getName().toLowerCase().equals("list")) {
    		return;
    	}
    	
        EmbedBuilder builder = new EmbedBuilder();

        String language = "en";
        String userId = event.getUser().getId();
        if (Discord.getUniqueIdByDiscordId(userId) != null) {
            BreadMaker bread = Synergy.getBread(Discord.getUniqueIdByDiscordId(userId));
            language = bread.getLanguage();
        }

        String title = Synergy.translate("<lang>synergy-online-players-list</lang>", language).getStripped();
        builder.setTitle(title);
        builder.setColor(Color.decode("#a29bfe"));

        StringBuilder list = new StringBuilder();

        if (Synergy.isRunningBungee()) {
            Bungee.getInstance().getProxy().getServers().forEach((serverName, server) -> {
                list.append("\n**").append(serverName).append(": **");
                if (server.getPlayers().isEmpty()) {
                    list.append("ні душі");
                } else {
                    List<String> players = server.getPlayers().stream()
                                                 .map(player -> player.getDisplayName())
                                                 .collect(Collectors.toList());
                    list.append(String.join(", ", players));
                }
            });
        } else {
            list.append("\n**").append(Synergy.getServerName()).append(": **");
            List<String> players = org.bukkit.Bukkit.getOnlinePlayers().stream()
                                                 .map(player -> player.getName())
                                                 .collect(Collectors.toList());
            list.append(String.join(", ", players));
        }

        builder.setDescription(list.toString());
        event.replyEmbeds(builder.build()).queue();
    }
}
