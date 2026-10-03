package com.xiaowu.game.starveil.game.item;
import com.xiaowu.game.starveil.infrastructure.ResourceResolver;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.xiaowu.game.starveil.infrastructure.logging.LoggerManager;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 物品注册表 - 单例，从 JSON 加载所有物品定义
 */
public class ItemRegistry {
    private static ItemRegistry instance;
    private final Map<String, Item> items = new HashMap<>();

    private ItemRegistry() {
        loadItems();
    }

    public static ItemRegistry getInstance() {
        if (instance == null) {
            instance = new ItemRegistry();
        }
        return instance;
    }

    private void loadItems() {
        try (InputStream is = ResourceResolver.getResourceAsStream("starveil:data/items/items.json")) {
            if (is == null) {
                LoggerManager.Logger("WARN", "物品配置文件不存在，使用空注册表");
                return;
            }

            InputStreamReader reader = new InputStreamReader(is);
            Gson gson = new Gson();
            Type type = new TypeToken<List<Item>>() {}.getType();
            List<Item> itemList = gson.fromJson(reader, type);

            if (itemList != null) {
                for (Item item : itemList) {
                    items.put(item.getId(), item);
                }
                LoggerManager.Logger("INFO", "已加载 " + items.size() + " 个物品定义");
            }
        } catch (Exception e) {
            LoggerManager.Logger("ERROR", "加载物品配置失败: " + e.getMessage());
        }
    }

    public Item getItem(String id) {
        return items.get(id);
    }

    public boolean hasItem(String id) {
        return items.containsKey(id);
    }

    public void reload() {
        items.clear();
        loadItems();
    }
}
