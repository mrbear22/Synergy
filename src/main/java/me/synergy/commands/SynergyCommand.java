package me.synergy.commands;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import me.synergy.brains.Synergy;
import me.synergy.integrations.DeepLTranslator;
import me.synergy.modules.LocalesManager;
import me.synergy.objects.LocaleBuilder;
import me.synergy.utils.Timings;

public class SynergyCommand implements CommandExecutor {
    
    public void initialize() {
        Synergy.getSpigot().getCommand("synergy").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            return false;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                if (!sender.hasPermission("synergy.reload")) {
                    sender.sendMessage(LocaleBuilder.of("no-permission").build());
                    return true;
                }
                Synergy.getSpigot().reloadConfig();
                Synergy.getLocalesManager().reload();
                Synergy.getDataManager().initialize();
                Synergy.getConfig().initialize();

                new DynamicCommands().shutdown();
                new DynamicCommands().initialize();
                sender.sendMessage(LocaleBuilder.of("reloaded").build());
                return true;

            case "timings":
                if (!sender.hasPermission("synergy.timings")) {
                    sender.sendMessage(LocaleBuilder.of("no-permission").build());
                    return false;
                }
                Map<String, Double> averages = new Timings().getAllAverages();
                if (averages.isEmpty()) {
                    sender.sendMessage(LocaleBuilder.of("timings-no-data")
                        .fallback("<red>No timings recorded yet.")
                        .build());
                    return true;
                }
                sender.sendMessage(LocaleBuilder.of("timings-header")
                    .fallback("<yellow>=== Timings Report ===")
                    .build());
                averages.forEach((id, avg) ->
                    sender.sendMessage(LocaleBuilder.of("timings-entry")
                        .placeholder("id", id)
                        .placeholder("time", String.format("%.2f", avg))
                        .placeholder("color", getTimingColor(avg))
                        .fallback("<yellow>%id%: <%color%>%time% ms")
                        .build())
                );
                return true;

            case "createlocale":
                if (!sender.hasPermission("synergy.createlocale")) {
                    sender.sendMessage(LocaleBuilder.of("no-permission").build());
                    return false;
                }
                if (args.length < 2) {
                    sender.sendMessage(LocaleBuilder.of("createlocale-usage")
                        .fallback("<red>Usage: /synergy createlocale <language_tag>")
                        .build());
                    sender.sendMessage(LocaleBuilder.of("createlocale-example")
                        .fallback("<gray>Example: /synergy createlocale uk")
                        .build());
                    return true;
                }
                
                String targetLang = args[1].toLowerCase();
                sender.sendMessage(LocaleBuilder.of("createlocale-starting")
                    .placeholder("language", targetLang)
                    .fallback("<yellow>Starting translation process for language: %language%")
                    .build());

                Synergy.getSpigot().getServer().getScheduler().runTaskAsynchronously(
                    Synergy.getSpigot(), 
                    () -> createLocaleAsync(sender, targetLang)
                );
                return true;

            case "info":
                if (!sender.hasPermission("synergy.info")) {
                    sender.sendMessage(LocaleBuilder.of("no-permission").build());
                    return false;
                }
                return true;

            case "help":
                return false;

            default:
                return false;
        }
    }

    private void createLocaleAsync(CommandSender sender, String targetLang) {
        try {
            DeepLTranslator translator = new DeepLTranslator();
            
            if (!translator.isLanguageSupported(targetLang)) {
                sender.sendMessage(LocaleBuilder.of("createlocale-unsupported")
                    .placeholder("language", targetLang)
                    .fallback("<red>Language '%language%' is not supported by DeepL API")
                    .build());
                sender.sendMessage(LocaleBuilder.of("createlocale-supported-list")
                    .placeholder("languages", String.join(", ", translator.getSupportedLanguages()))
                    .fallback("<gray>Supported languages: %languages%")
                    .build());
                return;
            }

            // Get all English translations
            if (!Synergy.getLocalesManager().getLanguages().contains("en")) {
                sender.sendMessage(LocaleBuilder.of("createlocale-no-english")
                    .fallback("<red>No English translations found to translate from!")
                    .build());
                return;
            }

            Synergy.getLocalesManager();
            Map<String, HashMap<String, String>> allLocales = LocalesManager.getLocales();
            HashMap<String, String> englishTranslations = allLocales.get("en");
            
            if (englishTranslations == null || englishTranslations.isEmpty()) {
                sender.sendMessage(LocaleBuilder.of("createlocale-no-english")
                    .fallback("<red>No English translations found to translate from!")
                    .build());
                return;
            }

            // Convert to array for indexed processing
            String[] keys = englishTranslations.keySet().toArray(new String[0]);
            int totalKeys = keys.length;

            sender.sendMessage(LocaleBuilder.of("createlocale-translating")
                .placeholder("count", String.valueOf(totalKeys))
                .fallback("<yellow>Translating %count% keys...")
                .build());

            // Process translations with delays using Bukkit scheduler
            processTranslationWithDelay(sender, translator, englishTranslations, keys, targetLang, 0, 0, 0);

        } catch (Exception e) {
            sender.sendMessage(LocaleBuilder.of("createlocale-failed")
                .placeholder("error", e.getMessage())
                .fallback("<red>Translation process failed: %error%")
                .build());
            Synergy.getLogger().error("Translation process failed " + e);
        }
    }

    private void processTranslationWithDelay(CommandSender sender, DeepLTranslator translator, 
                                           HashMap<String, String> englishTranslations, String[] keys, 
                                           String targetLang, int currentIndex, int translated, int failed) {
        
        // Check if we're done
        if (currentIndex >= keys.length) {
            // Final report
            sender.sendMessage(LocaleBuilder.of("createlocale-complete-header")
                .fallback("<green>=== Translation Complete ===")
                .build());
            sender.sendMessage(LocaleBuilder.of("createlocale-complete-success")
                .placeholder("count", String.valueOf(translated))
                .fallback("<green>Successfully translated: %count%")
                .build());
            sender.sendMessage(LocaleBuilder.of("createlocale-complete-failed")
                .placeholder("count", String.valueOf(failed))
                .fallback("<red>Failed: %count%")
                .build());
            sender.sendMessage(LocaleBuilder.of("createlocale-complete-message")
                .placeholder("language", targetLang)
                .fallback("<yellow>Language '%language%' has been created!")
                .build());
            return;
        }

        String key = keys[currentIndex];
        String englishText = englishTranslations.get(key);
        
        if (Synergy.getLocalesManager().hasTranslation(key, targetLang)) {
            sender.sendMessage(LocaleBuilder.of("createlocale-skipping")
                .placeholder("key", key)
                .fallback("<gray>Skipping %key% (already exists)")
                .build());
            
            Synergy.getSpigot().getServer().getScheduler().runTaskAsynchronously(
                Synergy.getSpigot(),
                () -> processTranslationWithDelay(sender, translator, englishTranslations, keys, 
                                                targetLang, currentIndex + 1, translated, failed)
            );
            return;
        }

        try {
            final int newTranslated;
            final int newFailed;
            
            Map<String, Set<String>> arrayKeys = Synergy.getLocalesManager().getArrayKeys();
            boolean isArrayKey = arrayKeys.containsKey("en") && arrayKeys.get("en").contains(key);
            
            if (isArrayKey) {
                String[] englishLines = englishText.split(System.lineSeparator());
                String[] translatedLines = new String[englishLines.length];
                boolean allTranslated = true;
                
                for (int i = 0; i < englishLines.length; i++) {
                    if (englishLines[i].trim().isEmpty()) {
                        translatedLines[i] = englishLines[i];
                        continue;
                    }
                    
                    String translatedLine = translator.translate(englishLines[i].trim(), "en", targetLang);
                    if (translatedLine != null && !translatedLine.trim().isEmpty()) {
                        translatedLines[i] = translatedLine;
                    } else {
                        translatedLines[i] = englishLines[i];
                        allTranslated = false;
                    }
                }
                
                if (allTranslated) {
                    Synergy.getSpigot().getServer().getScheduler().runTask(Synergy.getSpigot(), () -> {
                        try {
                            Synergy.getLocalesManager().addOrUpdate(key, targetLang, translatedLines);
                        } catch (Exception e) {
                            sender.sendMessage(LocaleBuilder.of("createlocale-save-error")
                                .placeholder("key", key)
                                .placeholder("error", e.getMessage())
                                .fallback("<red>Error saving array translation for '%key%': %error%")
                                .build());
                        }
                    });
                    
                    newTranslated = translated + 1;
                    newFailed = failed;
                    sender.sendMessage(LocaleBuilder.of("createlocale-array-success")
                        .placeholder("key", key)
                        .placeholder("lines", String.valueOf(englishLines.length))
                        .fallback("<green>Translated array '%key%' (%lines% lines)")
                        .build());
                } else {
                    sender.sendMessage(LocaleBuilder.of("createlocale-array-partial-fail")
                        .placeholder("key", key)
                        .fallback("<red>Partially failed to translate array key: %key%")
                        .build());
                    newTranslated = translated;
                    newFailed = failed + 1;
                }
                
            } else {
                String translatedText = translator.translate(englishText, "en", targetLang);
                
                if (translatedText != null && !translatedText.trim().isEmpty()) {
                    Synergy.getSpigot().getServer().getScheduler().runTask(Synergy.getSpigot(), () -> {
                        try {
                            Synergy.getLocalesManager().addOrUpdate(key, targetLang, translatedText);
                        } catch (Exception e) {
                            sender.sendMessage(LocaleBuilder.of("createlocale-save-error")
                                .placeholder("key", key)
                                .placeholder("error", e.getMessage())
                                .fallback("<red>Error saving translation for '%key%': %error%")
                                .build());
                        }
                    });
                    
                    newTranslated = translated + 1;
                    newFailed = failed;
                } else {
                    sender.sendMessage(LocaleBuilder.of("createlocale-translate-fail")
                        .placeholder("key", key)
                        .fallback("<red>Failed to translate key: %key%")
                        .build());
                    newTranslated = translated;
                    newFailed = failed + 1;
                }
            }

            if (newTranslated % 10 == 0) {
                sender.sendMessage(LocaleBuilder.of("createlocale-progress")
                    .placeholder("current", String.valueOf(newTranslated))
                    .placeholder("total", String.valueOf(keys.length))
                    .fallback("<green>Progress: %current%/%total%")
                    .build());
            }

            long delayTicks = Synergy.getConfig().getInt("deepl.request-delay-ms", 200) / 50;
            Synergy.getSpigot().getServer().getScheduler().runTaskLaterAsynchronously(
                Synergy.getSpigot(),
                () -> processTranslationWithDelay(sender, translator, englishTranslations, keys, 
                                                targetLang, currentIndex + 1, newTranslated, newFailed),
                delayTicks
            );

        } catch (Exception e) {
            sender.sendMessage(LocaleBuilder.of("createlocale-error")
                .placeholder("key", key)
                .placeholder("error", e.getMessage())
                .fallback("<red>Error translating '%key%': %error%")
                .build());
            
            long delayTicks = Synergy.getConfig().getInt("deepl.request-delay-ms", 200) / 50;
            Synergy.getSpigot().getServer().getScheduler().runTaskLaterAsynchronously(
                Synergy.getSpigot(),
                () -> processTranslationWithDelay(sender, translator, englishTranslations, keys, 
                                                targetLang, currentIndex + 1, translated, failed + 1),
                delayTicks
            );
        }
    }

    private String getTimingColor(double avg) {
        return avg < 5 ? "green" :
               avg < 20 ? "yellow" :
               "red";
    }
}