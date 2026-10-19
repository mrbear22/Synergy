package me.synergy.commands;

import java.util.List;
import java.util.stream.Stream;

import me.synergy.modules.Locales;
import me.synergy.objects.LocaleBuilder;

public class LanguageCommand extends Command {

    public LanguageCommand() {
        super("language|lang [language]");
    }

    @Override public String toggle() { return "localizations.enabled"; }

    @Override
    protected List<String> values(Sender sender, String name, String[] input) {
        return Stream.concat(Locales.languages().stream().sorted(), Stream.of("auto")).toList();
    }

    @Override
    public void defaults() {
        Locales.addDefault("command.language", "choose your language");
        Locales.addDefault("language.native.name", "English");
        Locales.addDefault("language.menu.title", "<danger>Choose your language");
        Locales.addDefault("language.menu.option", "<primary>▶ <click:run_command:'/language %code%'><hover:show_text:'<locale:hover.click.select>'><secondary>%name%</click>");
        Locales.addDefault("language.auto.name", "Game language");
        Locales.addDefault("language.choice.saved", "<success>Language selected: <primary>%language%<sound:'entity.ocelot.death'>");
    }

    @Override
    protected void execute(Sender sender, String path, String[] args) {
        if (args.length == 0) {
            sender.book("Languages", "<locale:language.menu.title>\n\n" + String.join("\n", Locales.languages().stream().sorted()
                .map(code -> LocaleBuilder.of("language.menu.option").placeholder("code", code).placeholder("name", Locales.get("language.native.name", code)).build()).toList())
                + "\n" + LocaleBuilder.of("language.menu.option").placeholder("code", "auto").placeholder("name", "<locale:language.auto.name>").build());
            return;
        }
        String language = args[0].toLowerCase();
        boolean auto = language.equals("auto");
        if (!auto && !Locales.languages().contains(language)) {
            help(sender);
            return;
        }
        sender.bread().setData("language", auto ? null : language);
        sender.send(LocaleBuilder.of("language.choice.saved").placeholder("language", auto ? "<locale:language.auto.name>" : Locales.get("language.native.name", language)));
    }
}
