package me.synergy.utils;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import me.synergy.brains.Synergy;
import me.synergy.objects.BreadMaker;
import me.synergy.text.Color;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;

public class BookMessage {

    private static final MiniMessage miniMessage = MiniMessage.miniMessage();
    private static final GsonComponentSerializer gsonSerializer = GsonComponentSerializer.gson();

    public static void sendFakeBook(Player player, String title, String content) {
        BreadMaker bread = Synergy.getBread(player.getUniqueId());
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        
        Component titleComponent = miniMessage.deserialize(title);
        meta.setTitle(gsonSerializer.serialize(titleComponent));

        content = Translation.processLangTags(content, bread.getLanguage());
        content = Endings.processEndings(content, bread.getPronoun());
        content = Color.processThemeTags(content, bread.getTheme());
        content = Color.processColorReplace(content, bread.getTheme());
        
        String[] pages = content.split("%np%");

        for (String page : pages) {

            Component pageComponent = miniMessage.deserialize(page);
            String jsonPage = gsonSerializer.serialize(pageComponent);
            
            BaseComponent[] components = ComponentSerializer.parse(jsonPage);
            meta.spigot().addPage(components);
        }

        meta.setAuthor("synergy");
        book.setItemMeta(meta);
        player.openBook(book);
        player.playSound(player, Sound.ITEM_BOOK_PAGE_TURN, 1, 1);
    }

}