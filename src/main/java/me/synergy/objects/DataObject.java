package me.synergy.objects;

import java.util.UUID;

import me.synergy.text.Gendered.Gender;

public class DataObject {

    private final Object data;

    public DataObject(Object data) {
        this.data = data;
    }

    public String getAsString() {
        return data != null ? data.toString() : null;
    }

    public Integer getAsInteger() {
        try {
            return data != null ? Integer.parseInt(data.toString()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Long getAsLong() {
        try {
            return data != null ? Long.parseLong(data.toString()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Boolean getAsBoolean() {
        String str = getAsString();
        return (str != null && (str.equalsIgnoreCase("true") || str.equalsIgnoreCase("false")))
                ? Boolean.parseBoolean(str)
                : null;
    }

    public UUID getAsUUID() {
        try {
            return data != null ? UUID.fromString(data.toString()) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public Gender getAsGender() {
        try {
            return data != null ? Gender.valueOf(data.toString().toUpperCase()) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean isSet() {
        return data != null;
    }
}
