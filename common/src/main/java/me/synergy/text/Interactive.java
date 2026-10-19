package me.synergy.text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.synergy.brains.Synergy;
import me.synergy.objects.BreadMaker;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.sound.Sound.Source;
import java.time.Duration;

public class Interactive {
	
    private static final Pattern SOUND_TAG = Pattern.compile("<sound:'(.*?)'>");
    private static final Pattern TITLE_TAG = Pattern.compile("<title:'([^']*)'(?::'([^']*)')?(?::(\\d+):(\\d+):(\\d+))?>");
    private static final Pattern ACTIONBAR_TAG = Pattern.compile("<actionbar:'(.*?)'>");
    
    public static String process(String text, BreadMaker bread) {
        if (text == null || !bread.isOnline()) {
            return text;
        }
        Audience player = Synergy.platform().audience(bread.getUniqueId());
        
        text = processTag(text, SOUND_TAG, sound -> playSound(player, sound));
        text = processTitleTag(text, player);
        text = processTag(text, ACTIONBAR_TAG, actionbar -> {
            player.sendActionBar(Component.text(actionbar));
        });
        return removeTags(text);
    }
    
    private static String processTitleTag(String text, Audience player) {
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
            
            Title paperTitle = Title.title(
                Component.text(title),
                Component.text(subtitle),
                Title.Times.times(
                    Duration.ofMillis(fadeIn * 50L),
                    Duration.ofMillis(stay * 50L),
                    Duration.ofMillis(fadeOut * 50L)
                )
            );
            
            player.showTitle(paperTitle);
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
        if (text == null) {
            return text;
        }
        text = text.replaceAll("<sound:'[^']*'>", "");
        text = text.replaceAll("<title:'[^']*'(?::'[^']*')?(?::\\d+:\\d+:\\d+)?>", "");
        text = text.replaceAll("<actionbar:'[^']*'>", "");
        return text;
    }
    
    private static void playSound(Audience player, String soundKey) {
        try {
            player.playSound(net.kyori.adventure.sound.Sound.sound(Synergy.platform().sound(soundKey), Source.AMBIENT, 1.0f, 1.0f));
        } catch (Exception e) {
        }
    }
    
    @FunctionalInterface
    private interface TagAction {
        void execute(String value);
    }
}