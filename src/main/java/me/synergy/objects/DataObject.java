package me.synergy.objects;

import java.util.UUID;

import me.synergy.utils.Endings.Pronoun;

public class DataObject {

	private Object data;

	public DataObject(Object data) {
		this.data = data;
	}

	public String getAsString() {
		return data != null ? (String) data : null;
	}

	public Integer getAsInteger() {
		return data != null ? (Integer) data : null;
	}

	public Boolean getAsBoolean() {
		return data != null && (data.equals("true") || data.equals("false")) ? Boolean.parseBoolean((String) data) : null;
	}

	public UUID getAsUUID() {
		return data != null ? UUID.fromString(getAsString()) : null;
	}

	public Pronoun getAsPronoun() {
		return data != null ? Pronoun.valueOf(data.toString().toUpperCase()) : null;
	}
	
	public boolean isSet() {
		return data != null;
	}

}
