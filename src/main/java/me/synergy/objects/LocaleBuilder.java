package me.synergy.objects;

import java.util.LinkedHashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;

public class LocaleBuilder {
    
    private final String key;
    private final Map<String, String> arguments;
    
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
    
    public String build() {
        StringBuilder sb = new StringBuilder("<locale:");
        sb.append(key);
        
        for (Map.Entry<String, String> entry : arguments.entrySet()) {
            sb.append(" ");
            sb.append(entry.getKey());
            sb.append("=");
            
            String value = entry.getValue();
            if (needsQuotes(value)) {
                sb.append("\"").append(escapeValue(value)).append("\"");
            } else {
                sb.append(value);
            }
        }
        
        sb.append(">");
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
    
    private String escapeValue(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}