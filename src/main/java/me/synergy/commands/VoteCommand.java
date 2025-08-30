package me.synergy.commands;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import me.synergy.brains.Synergy;
import me.synergy.objects.BreadMaker;
import me.synergy.utils.BookMessage;
import me.synergy.utils.Translation;

public class VoteCommand implements CommandExecutor {
    public void initialize() {
        if (!Synergy.getConfig().getBoolean("votifier.enabled")) {
        	return;
        }
        Synergy.getSpigot().getCommand("vote").setExecutor(this);
    }
    
    @Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("<lang>command-not-player</lang>");
            return false;
        }
        
        if (!sender.hasPermission("synergy.vote")) {
            sender.sendMessage("<lang>no-permission</lang>");
            return false;
        }
        
        BreadMaker bread = Synergy.getBread(((Player) sender).getUniqueId());
        List<String> monitorings = Synergy.getConfig().getStringList("votifier.monitorings");
        
        StringBuilder build = new StringBuilder();
        StringBuilder list = new StringBuilder();
        
        for (String m : monitorings) {
            try {
            	String domain = new URI(m).getHost();
            	String shortenDomain = domain.replace("www.", "");
            	list.append(Translation.processLangTags("<lang>vote-monitorings-format</lang>", bread.getLanguage())
            			.replace("%URL%", m)
            			.replace("%MONITORING%", shortenDomain));
            	list.append("\n");
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }
        
        build.append(Translation.translate("<lang>monitorings-menu</lang>", bread.getLanguage()).replace("%MONITORINGS%", list.toString()));
        BookMessage.sendFakeBook((Player) sender, "Monitorings", build.toString());
        
        return true;
    }
}