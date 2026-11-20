package me.synergy.commands;

import me.synergy.modules.Locales;
import me.synergy.brains.Synergy;
import me.synergy.events.SynergyEvent;
import me.synergy.objects.BreadMaker;
import me.synergy.objects.LocaleBuilder;
import me.synergy.web.TikTokHandler;

public class TikTokCommand extends Command {

    public TikTokCommand() {
        super("tiktok link <username>", "tiktok unlink", "tiktok connect", "tiktok disconnect",
            "tiktok testreward gift <gift>", "tiktok testreward like <count>", "tiktok testreward follow");
    }

    @Override public String toggle() { return "tiktok.enabled"; }

    @Override
    public void defaults() {
        Locales.addDefault("command.tiktok.link", "link your TikTok");
        Locales.addDefault("command.tiktok.unlink", "unlink your TikTok");
        Locales.addDefault("command.tiktok.connect", "connect to TikTok Live");
        Locales.addDefault("command.tiktok.disconnect", "disconnect from TikTok Live");
        Locales.addDefault("command.tiktok.testreward.gift", "test a gift reward");
        Locales.addDefault("command.tiktok.testreward.like", "test a like reward");
        Locales.addDefault("command.tiktok.testreward.follow", "test a follow reward");
        Locales.addDefault("tiktok.reward.tested", "<success>Test reward executed!");
    }

    @Override
    protected void execute(Sender sender, String path, String[] args) {
        BreadMaker bread = sender.bread();
        String username = bread.getData("tiktok").getAsString();
        boolean anyState = path.equals("tiktok.disconnect") || path.startsWith("tiktok.testreward");
        if (!anyState && !linkState(sender, "TikTok", username != null, !path.equals("tiktok.link"))) return;
        SynergyEvent test = Synergy.event(path.replace("tiktok.testreward.", "tiktok-")).toPlayer(sender.uuid());
        switch (path) {
            case "tiktok.link" -> {
                bread.setData("tiktok", args[0].replace("@", ""));
                sender.send("<locale:account.link.done service=TikTok>");
                TikTokHandler.request(sender.uuid(), args[0].replace("@", ""));
            }
            case "tiktok.unlink" -> {
                TikTokHandler.request(sender.uuid(), null);
                bread.setData("tiktok", null);
                sender.send("<locale:account.unlink.done service=TikTok>");
            }
            case "tiktok.connect" -> TikTokHandler.request(sender.uuid(), username);
            case "tiktok.disconnect" -> TikTokHandler.request(sender.uuid(), null);
            case "tiktok.testreward.gift" -> test.setOption("gift-name", args[0]).setOption("gift-id", "999").setOption("combo-count", "1").setOption("viewer-name", "TestDonater");
            case "tiktok.testreward.like" -> {
                if (!args[0].matches("\\d+")) {
                    sender.send(LocaleBuilder.of("input.number.invalid").placeholder("input", args[0]));
                    return;
                }
                test.setOption("viewer-name", "TestViewer").setOption("like-count", args[0]);
            }
            case "tiktok.testreward.follow" -> test.setOption("viewer-name", "TestFollower");
        }
        if (path.startsWith("tiktok.testreward")) {
            test.send();
            sender.send("<locale:tiktok.reward.tested>");
        }
    }
}
