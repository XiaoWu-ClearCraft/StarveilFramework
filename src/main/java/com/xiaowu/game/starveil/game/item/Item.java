package com.xiaowu.game.starveil.game.item;

import java.util.HashMap;
import java.util.Map;

/**
 * 物品数据模型
 */
public class Item {
    private String id;
    private String name;
    private String description;
    private String iconPath;
    private String texturePath;
    private boolean changesCursor;
    private Map<String, String> events;

    public Item() {
        this.events = new HashMap<>();
    }

    public Item(String id, String name, String description, String iconPath, String texturePath, boolean changesCursor, Map<String, String> events) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.iconPath = iconPath;
        this.texturePath = texturePath;
        this.changesCursor = changesCursor;
        this.events = events != null ? events : new HashMap<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getIconPath() { return iconPath; }
    public void setIconPath(String iconPath) { this.iconPath = iconPath; }

    public String getTexturePath() { return texturePath; }
    public void setTexturePath(String texturePath) { this.texturePath = texturePath; }

    public boolean isChangesCursor() { return changesCursor; }
    public void setChangesCursor(boolean changesCursor) { this.changesCursor = changesCursor; }

    public Map<String, String> getEvents() { return events; }
    public void setEvents(Map<String, String> events) { this.events = events; }

    public String getEvent(String key) { return events.get(key); }
}
