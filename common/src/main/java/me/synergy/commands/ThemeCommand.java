package me.synergy.commands;

import java.util.List;
import java.util.Set;

import me.synergy.modules.Config;
import me.synergy.modules.Locales;
import me.synergy.objects.LocaleBuilder;

public class ThemeCommand extends Command {

    public ThemeCommand() {
        super("theme [theme]");
    }

    @Override public String toggle() { return "localizations.enabled"; }

    @Override
    protected List<String> values(Sender sender, String name, String[] input) {
        return List.copyOf(themes());
    }

    @Override
    public void defaults() {
        Locales.addDefault("command.theme", "choose a color theme");
        Locales.addDefault("theme.menu.title", "<danger>Choose a theme");
        Locales.addDefault("theme.menu.option", "<primary>▶ <click:run_command:'/theme %theme%'><hover:show_text:'<locale:hover.click.select>'>%color%%theme%</click>");
        Locales.addDefault("theme.choice.saved", "<success>Theme selected: <primary>%theme%<sound:'entity.ocelot.death'>");
    }

    @Override
    protected void execute(Sender sender, String path, String[] args) {
        if (args.length == 0) {
            sender.book("Themes", "<locale:theme.menu.title>\n\n" + String.join("\n", themes().stream()
                .map(theme -> LocaleBuilder.of("theme.menu.option").placeholder("theme", theme).placeholder("color", Config.getString("localizations.color-themes." + theme + ".primary")).build()).toList()));
            return;
        }
        String theme = args[0].toLowerCase();
        if (!themes().contains(theme)) {
            help(sender);
            return;
        }
        sender.bread().setData("theme", theme.equals("default") ? null : theme);
        sender.send(LocaleBuilder.of("theme.choice.saved").placeholder("theme", theme));
    }

    private static Set<String> themes() {
        return Config.getConfigurationSection("localizations.color-themes").keySet();
    }
}
