package me.synergy.utils;

import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Endings {

    public enum Pronoun {
        HE, SHE
    }
    
    public static Pronoun getPronoun(String pronoun) {
        try {
            return Pronoun.valueOf(pronoun.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Pronoun.HE;
        }
    }
    
    public static Set<Pronoun> getPronouns() {
        return EnumSet.allOf(Pronoun.class);
    }
    
    public static Set<String> getPronounsAsStringSet() {
        Set<Pronoun> pronouns = getPronouns();
        return pronouns.stream()
                .map(Pronoun::name)
                .collect(Collectors.toSet());
    }
    
    public static String processEndings(String input, Pronoun pronoun) {
    	
        Matcher pronounMatcher = Pattern.compile("<pronoun>(?i)(he|she)</pronoun>").matcher(input);
        if (pronounMatcher.find()) {
            String detectedPronoun = pronounMatcher.group(1);
            pronoun = "he".equalsIgnoreCase(detectedPronoun) ? Pronoun.HE : Pronoun.SHE;
        }
        
        input = removePronounTags(input);
    	
        Pattern pattern = Pattern.compile("<ending>(.*?)</ending>");
        Matcher matcher = pattern.matcher(input);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String replacement = "";
            String groupContent = matcher.group(1);
            if (pronoun == Pronoun.HE) {
                Matcher heMatcher = Pattern.compile("<he>(.*?)</he>").matcher(groupContent);
                if (heMatcher.find()) {
                    replacement = heMatcher.group(1);
                }
            } else if (pronoun == Pronoun.SHE) {
                Matcher sheMatcher = Pattern.compile("<she>(.*?)</she>").matcher(groupContent);
                if (sheMatcher.find()) {
                    replacement = sheMatcher.group(1);
                } else {
                    Matcher heMatcher = Pattern.compile("<he>(.*?)</he>").matcher(groupContent);
                    if (heMatcher.find()) {
                        replacement = heMatcher.group(1);
                    }
                }
            }
            matcher.appendReplacement(result, replacement);
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public static String removeEndingTags(String string) {
    	return string.replaceAll("<ending>(.*?)</ending>", "");
    }
    
    public static String removePronounTags(String string) {
    	return string.replaceAll("<pronoun>(.*?)</pronoun>", "");
    }

}