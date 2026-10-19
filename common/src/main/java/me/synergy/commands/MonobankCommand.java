package me.synergy.commands;

import me.synergy.modules.Locales;
import me.synergy.objects.BreadMaker;

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
        if (path.equals("monobank.link") == linked) {
            sender.send(linked ? "<locale:account.link.exists service=Monobank command=\"/monobank unlink\">" : "<locale:account.link.missing service=Monobank command=\"/monobank link\">");
            return;
        }
        bread.setData("monobank", linked ? null : args[0]);
        sender.send(linked ? "<locale:account.unlink.done service=Monobank>" : "<locale:account.link.done service=Monobank>");
    }
}
