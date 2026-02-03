package me.synergy.objects;

import java.util.LinkedHashMap;
import java.util.Map;

import me.synergy.brains.Synergy;
import net.kyori.adventure.text.Component;

public class LocaleBuilder {
    
    private final String key;
    private final Map<String, String> arguments;
    private String fallback;
    
    private LocaleBuilder(String key) {
        this.key = key;
        this.arguments = new LinkedHashMap<>();
    }
    
    public static LocaleBuilder of(String key) {
        return new LocaleBuilder(key);
    }
    
    public LocaleBuilder placeholder(String name, Object value) {
        if (value != null) {
            arguments.put(name, String.valueOf(value));
        }
        return this;
    }
    
    public LocaleBuilder fallback(String fallback) {
        this.fallback = fallback;
        return this;
    }
    
    public String build() {
        StringBuilder sb = new StringBuilder("<locale:");
        sb.append(key);
        
        for (Map.Entry<String, String> entry : arguments.entrySet()) {
            sb.append(" ");
            sb.append(entry.getKey());
            sb.append("=");
            
            String value = entry.getValue();
            if (needsQuotes(value)) {
                sb.append("\"").append(escapeQuotes(value)).append("\"");
            } else {
                sb.append(value);
            }
        }
        
        if (fallback != null) {
            sb.append(" fallback=\"").append(escapeQuotes(fallback)).append("\"");
        }
        
        sb.append(">");
        Synergy.getLogger().warning(sb.toString());
        return sb.toString();
    }
    
    public Component component() {
    	return Component.text(build());
    }
    
    @Override
    public String toString() {
        return build();
    }
    
    private boolean needsQuotes(String value) {
        return value.contains(" ") || value.contains(">") || value.contains("\"");
    }
    
    private String escapeQuotes(String value) {
        return value.replace("\"", "\\\"");
    }
}