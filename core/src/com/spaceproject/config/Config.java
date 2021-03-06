package com.spaceproject.config;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.Json;

public abstract class Config {

	private final String fileName = "config/" +  this.getClass().getSimpleName() + ".json";

	/*public Config() {
		loadDefault();
	}*/
	
	public abstract void loadDefault();
	
	public void saveToJson() {		
		Json json = new Json();
		json.setUsePrototypes(false);
		
		System.out.println(json.toJson(this));			
		
		FileHandle keyFile = Gdx.files.local(fileName);		
		try {
			keyFile.writeString(json.toJson(this), false);
			System.out.println("Saved: " + fileName);
		} catch (GdxRuntimeException ex) {
			System.out.println("Could not save file: " + fileName + "\n" + ex.getMessage());
		}
	}
	
	public Config loadFromJson() {
		FileHandle keyFile = Gdx.files.local(fileName);
		if (keyFile.exists()) {
			Json json = new Json();
			json.setUsePrototypes(false);
			
			Config config = json.fromJson(this.getClass(), keyFile.readString());
			System.out.println("Loaded "+ this.getClass().getSimpleName() +" from json.");
			System.out.println(json.toJson(config));
			return config;			
		} else {
			System.out.println(fileName + " not found. Loading defaults.");
			loadDefault();
			saveToJson();
			return this;
		}
	}
}
