package me.synergy.commands;

import java.net.URI;

import me.synergy.modules.Config;
import me.synergy.modules.Locales;
import me.synergy.objects.LocaleBuilder;

public class VoteCommand extends Command {

    public VoteCommand() {
        super("vote");
    }

    @Override
    public void defaults() {
        Config.addDefault("votifier.monitorings", new String[] {"https://example.com/vote/example"});
        Locales.addDefault("command.vote", "vote for the server");
        Locales.addDefault("vote.menu.title", "<secondary>Vote for our server on these sites:");
        Locales.addDefault("vote.menu.site", "<primary>▶ <click:open_url:'%url%'><hover:show_text:'<locale:hover.click.open>'><secondary><u>%site%</u></click>");
        Locales.addDefault("vote.menu.thanks", "<primary>Thank you for supporting the server!");
    }

    @Override
    protected void execute(Sender sender, String path, String[] args) {
        sender.book("Vote", "<locale:vote.menu.title>\n\n" + String.join("\n", Config.getStringList("votifier.monitorings").stream()
            .map(url -> LocaleBuilder.of("vote.menu.site").placeholder("url", url).placeholder("site", URI.create(url).getHost().replace("www.", "")).build()).toList())
            + "\n\n<locale:vote.menu.thanks>");
    }
}
