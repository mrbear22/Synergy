package me.synergy.commands;

import me.synergy.modules.Locales;
import me.synergy.objects.BreadMaker;
import me.synergy.web.MonobankHandler;

public class MonobankCommand extends Command {

    public MonobankCommand() {
        super("monobank link <token>", "monobank unlink");
    }

    @Override public String toggle() { return "monobank.enabled"; }

    @Override
    public void defaults() {
        Locales.addDefault("command.monobank.link", "link your Monobank (keep the token secret!)");
        Locales.addDefault("command.monobank.unlink", "unlink your Monobank");
    }

    @Override
    protected void execute(Sender sender, String path, String[] args) {
        BreadMaker bread = sender.bread();
        boolean linked = bread.getData("monobank").isSet();
        if (!linkState(sender, "Monobank", linked, !path.equals("monobank.link"))) return;
        MonobankHandler.request(sender.uuid(), linked ? bread.getData("monobank").getAsString() : args[0], !linked);
        bread.setData("monobank", linked ? null : args[0]);
        sender.send(linked ? "<locale:account.unlink.done service=Monobank>" : "<locale:account.link.done service=Monobank>");
    }
}
