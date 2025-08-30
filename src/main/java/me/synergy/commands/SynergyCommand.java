package me.synergy.commands;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import me.synergy.brains.Synergy;
import me.synergy.integrations.DeepLTranslator;
import me.synergy.modules.LocalesManager;
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
                    sender.sendMessage("<lang>no-permission</lang>");
                    return false;
                }
                Synergy.getSpigot().reloadConfig();
                Synergy.getLocalesManager().reload();
                Synergy.getDataManager().initialize();
                Synergy.getConfig().initialize();
                sender.sendMessage("<lang>reloaded</lang>");
                return true;

            case "timings":
                if (!sender.hasPermission("synergy.timings")) {
                    sender.sendMessage("<lang>no-permission</lang>");
                    return false;
                }
                Map<String, Double> averages = new Timings().getAllAverages();
                if (averages.isEmpty()) {
                    sender.sendMessage(ChatColor.RED + "No timings recorded yet.");
                    return true;
                }
                sender.sendMessage(ChatColor.YELLOW + "=== Timings Report ===");
                averages.forEach((id, avg) ->
                    sender.sendMessage(ChatColor.YELLOW + id + ": " + getTimingColor(avg) + String.format("%.2f ms", avg))
                );
                return true;

            case "createlocale":
                if (!sender.hasPermission("synergy.createlocale")) {
                    sender.sendMessage("<lang>no-permission</lang>");
                    return false;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /synergy createlocale <language_tag>");
                    sender.sendMessage(ChatColor.GRAY + "Example: /synergy createlocale uk");
                    return true;
                }
                
                String targetLang = args[1].toLowerCase();
                sender.sendMessage(ChatColor.YELLOW + "Starting translation process for language: " + targetLang);

                Synergy.getSpigot().getServer().getScheduler().runTaskAsynchronously(
                    Synergy.getSpigot(), 
                    () -> createLocaleAsync(sender, targetLang)
                );
                return true;

            case "info":
                if (!sender.hasPermission("synergy.info")) {
                    sender.sendMessage("<lang>no-permission</lang>");
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
                sender.sendMessage(ChatColor.RED + "Language '" + targetLang + "' is not supported by DeepL API");
                sender.sendMessage(ChatColor.GRAY + "Supported languages: " + String.join(", ", translator.getSupportedLanguages()));
                return;
            }

            // Get all English translations
            if (!Synergy.getLocalesManager().getLanguages().contains("en")) {
                sender.sendMessage(ChatColor.RED + "No English translations found to translate from!");
                return;
            }

            Synergy.getLocalesManager();
			Map<String, HashMap<String, String>> allLocales = LocalesManager.getLocales();
            HashMap<String, String> englishTranslations = allLocales.get("en");
            
            if (englishTranslations == null || englishTranslations.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "No English translations found to translate from!");
                return;
            }

            // Convert to array for indexed processing
            String[] keys = englishTranslations.keySet().toArray(new String[0]);
            int totalKeys = keys.length;

            sender.sendMessage(ChatColor.YELLOW + "Translating " + totalKeys + " keys...");

            // Process translations with delays using Bukkit scheduler
            processTranslationWithDelay(sender, translator, englishTranslations, keys, targetLang, 0, 0, 0);

        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Translation process failed: " + e.getMessage());
            Synergy.getLogger().error("Translation process failed " + e);
        }
    }

    private void processTranslationWithDelay(CommandSender sender, DeepLTranslator translator, 
                                           HashMap<String, String> englishTranslations, String[] keys, 
                                           String targetLang, int currentIndex, int translated, int failed) {
        
        // Check if we're done
        if (currentIndex >= keys.length) {
            // Final report
            sender.sendMessage(ChatColor.GREEN + "=== Translation Complete ===");
            sender.sendMessage(ChatColor.GREEN + "Successfully translated: " + translated);
            sender.sendMessage(ChatColor.RED + "Failed: " + failed);
            sender.sendMessage(ChatColor.YELLOW + "Language '" + targetLang + "' has been created!");
            return;
        }

        String key = keys[currentIndex];
        String englishText = englishTranslations.get(key);
        
        if (Synergy.getLocalesManager().hasTranslation(key, targetLang)) {
            sender.sendMessage(ChatColor.GRAY + "Skipping " + key + " (already exists)");
            
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
                            sender.sendMessage(ChatColor.RED + "Error saving array translation for '" + key + "': " + e.getMessage());
                        }
                    });
                    
                    newTranslated = translated + 1;
                    newFailed = failed;
                    sender.sendMessage(ChatColor.GREEN + "Translated array '" + key + "' (" + englishLines.length + " lines)");
                } else {
                    sender.sendMessage(ChatColor.RED + "Partially failed to translate array key: " + key);
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
                            sender.sendMessage(ChatColor.RED + "Error saving translation for '" + key + "': " + e.getMessage());
                        }
                    });
                    
                    newTranslated = translated + 1;
                    newFailed = failed;
                } else {
                    sender.sendMessage(ChatColor.RED + "Failed to translate key: " + key);
                    newTranslated = translated;
                    newFailed = failed + 1;
                }
            }

            if (newTranslated % 10 == 0) {
                sender.sendMessage(ChatColor.GREEN + "Progress: " + newTranslated + "/" + keys.length);
            }

            long delayTicks = Synergy.getConfig().getInt("deepl.request-delay-ms", 200) / 50; // Convert ms to ticks
            Synergy.getSpigot().getServer().getScheduler().runTaskLaterAsynchronously(
                Synergy.getSpigot(),
                () -> processTranslationWithDelay(sender, translator, englishTranslations, keys, 
                                                targetLang, currentIndex + 1, newTranslated, newFailed),
                delayTicks
            );

        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error translating '" + key + "': " + e.getMessage());
            
            long delayTicks = Synergy.getConfig().getInt("deepl.request-delay-ms", 200) / 50; // Convert ms to ticks
            Synergy.getSpigot().getServer().getScheduler().runTaskLaterAsynchronously(
                Synergy.getSpigot(),
                () -> processTranslationWithDelay(sender, translator, englishTranslations, keys, 
                                                targetLang, currentIndex + 1, translated, failed + 1),
                delayTicks
            );
        }
    }

    private String getTimingColor(double avg) {
        return avg < 5 ? ChatColor.GREEN.toString() :
               avg < 20 ? ChatColor.YELLOW.toString() :
               ChatColor.RED.toString();
    }
}