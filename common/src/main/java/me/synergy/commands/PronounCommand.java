package me.synergy.commands;

import java.util.List;

import me.synergy.modules.Locales;
import me.synergy.objects.LocaleBuilder;
import me.synergy.text.Gendered;

public class PronounCommand extends Command {

    public PronounCommand() {
        super("pronoun|gender <pronoun>");
    }

    @Override public String toggle() { return "localizations.genders"; }

    @Override
    protected List<String> values(Sender sender, String name, String[] input) {
        return Gendered.getGendersAsStringSet().stream().map(String::toLowerCase).filter(g -> sender.hasPermission("synergy.pronoun." + g)).toList();
    }

    @Override
    public void defaults() {
        Locales.addDefault("command.pronoun", "set your pronouns");
        Locales.addDefault("pronoun.choice.saved", "<success>Pronouns set: <primary><male:he/him><female:she/her><nonbinary:they/them>");
        Locales.addDefault("pronoun.choice.invalid", "<danger>Unknown pronouns. Use male, female or nonbinary.");
    }

    @Override
    protected void execute(Sender sender, String path, String[] args) {
        String gender = args[0].toLowerCase();
        if (!Gendered.getGendersAsStringSet().contains(gender.toUpperCase())) sender.send("<locale:pronoun.choice.invalid>");
        else if (!sender.hasPermission("synergy.pronoun." + gender)) sender.send("<locale:player.permission.missing>");
        else {
            sender.bread().setData("gender", gender.toUpperCase());
            sender.send(LocaleBuilder.of("pronoun.choice.saved").placeholder("gender", gender));
        }
    }
}
