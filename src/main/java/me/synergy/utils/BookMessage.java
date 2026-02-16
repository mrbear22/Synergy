package me.synergy.utils;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import me.synergy.brains.Synergy;
import me.synergy.objects.BreadMaker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Arrays;

public class BookMessage {
    
    public static void sendFakeBook(Player player, String title, String content) {
        BreadMaker bread = Synergy.getBread(player.getUniqueId());
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        
        meta.title(MiniMessage.miniMessage().deserialize(title));
        meta.author(Component.text("synergy"));
        
        meta.pages(Arrays.stream(content.split("%np%"))
            .map(page -> Synergy.translate(page, bread.getLanguage())
                .setGendered(bread.getGender())
                .getColoredComponent(bread.getTheme()))
            .toList());
        
        book.setItemMeta(meta);
        player.openBook(book);
        player.playSound(player, Sound.ITEM_BOOK_PAGE_TURN, 1, 1);
    }
}