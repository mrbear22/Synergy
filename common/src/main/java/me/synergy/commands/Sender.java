package me.synergy.commands;

import java.util.Arrays;
import java.util.UUID;

import me.synergy.brains.Synergy;
import me.synergy.handlers.MessageHandler;
import me.synergy.objects.BreadMaker;
import me.synergy.objects.LocaleBuilder;
import me.synergy.text.Translation;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public record Sender(UUID uuid, String name) {

    public static final Sender CONSOLE = new Sender(null, "CONSOLE");

    public boolean isPlayer() {
        return uuid != null;
    }

    public BreadMaker bread() {
        return Synergy.getBread(uuid);
    }

    public boolean hasPermission(String node) {
        return uuid == null || Synergy.platform().hasPermission(uuid, node);
    }

    public void send(String message) {
        if (uuid != null) MessageHandler.send(uuid, message, true, true);
        else Synergy.getLogger().info(Synergy.translate(message, Translation.getDefaultLanguage()).getStripped());
    }

    public void send(LocaleBuilder locale) {
        send(locale.build());
    }

    public void book(String title, String content) {
        BreadMaker bread = bread();
        var audience = Synergy.platform().audience(uuid);
        audience.openBook(Book.book(MiniMessage.miniMessage().deserialize(title), Component.text("synergy"), Arrays.stream(content.split("%np%"))
            .map(page -> Synergy.translate(page, bread.getLanguage()).setGendered(bread.getGender()).getColoredComponent(bread.getTheme()))
            .toList()));
        audience.playSound(Sound.sound(Key.key("item.book.page_turn"), Sound.Source.MASTER, 1, 1));
    }

    public void run(String command) {
        Synergy.platform().dispatch(uuid, command);
    }
}
