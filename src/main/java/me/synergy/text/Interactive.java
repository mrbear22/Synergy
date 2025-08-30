package me.synergy.text;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.entity.Player;
import me.synergy.objects.BreadMaker;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;

public class Interactive {
    private static final Pattern SOUND_TAG = Pattern.compile("<sound:'(.*?)'>");
    private static final Pattern TITLE_TAG = Pattern.compile("<title:'([^']*)'(?::'([^']*)')?(?::(\\d+):(\\d+):(\\d+))?>");
    private static final Pattern ACTIONBAR_TAG = Pattern.compile("<actionbar:'(.*?)'>");
    
    public static String process(String text, BreadMaker bread) {
        Player player = Bukkit.getPlayer(bread.getName());
        if (text == null || player == null) return text;
        
        text = processTag(text, SOUND_TAG, (sound) -> playSound(player, sound));
        text = processTitleTag(text, player);
        text = processTag(text, ACTIONBAR_TAG, (actionbar) -> player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(actionbar)));
        
        return text;
    }
    
    private static String processTitleTag(String text, Player player) {
        Matcher m = TITLE_TAG.matcher(text);
        StringBuffer sb = new StringBuffer();
        
        while (m.find()) {
            String title = m.group(1);
            String subtitle = m.group(2) != null ? m.group(2) : "";
            
            int fadeIn = 10;
            int stay = 100;
            int fadeOut = 10;
            
            if (m.group(3) != null && m.group(4) != null && m.group(5) != null) {
                fadeIn = Integer.parseInt(m.group(3));
                stay = Integer.parseInt(m.group(4));
                fadeOut = Integer.parseInt(m.group(5));
            }
            
            player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
            m.appendReplacement(sb, "<reset>");
        }
        m.appendTail(sb);
        return sb.toString();
    }
    
    private static String processTag(String text, Pattern pattern, TagAction action) {
        Matcher m = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String value = m.group(1);
            action.execute(value);
            m.appendReplacement(sb, "<reset>");
        }
        m.appendTail(sb);
        return sb.toString();
    }
    
    public static String removeTags(String text) {
        if (text == null) return text;
        text = text.replaceAll("<sound:'[^']*'>", "");
        text = text.replaceAll("<title:'[^']*'(?::'[^']*')?(?::\\d+:\\d+:\\d+)?>", "");
        text = text.replaceAll("<actionbar:'[^']*'>", "");
        return text;
    }
    
    private static void playSound(Player player, String sound) {
        try {
            player.playSound(player.getLocation(), sound, SoundCategory.AMBIENT, 1.0f, 1.0f);
        } catch (Exception ignored) {}
    }
    
    @FunctionalInterface
    private interface TagAction {
        void execute(String value);
    }
}