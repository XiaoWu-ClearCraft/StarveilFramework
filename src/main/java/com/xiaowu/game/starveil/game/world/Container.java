package com.xiaowu.game.starveil.game.world;

import java.util.ArrayList;
import java.util.List;

/**
 * 容器数据模型 - 地图上的容器（如箱子、柜子等），包含独立物品列表
 */
public class Container {
    private String id;
    private List<String> items;
    private boolean opened;

    public Container() {
        this.items = new ArrayList<>();
        this.opened = false;
    }

    public Container(String id, List<String> items) {
        this.id = id;
        this.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
        this.opened = false;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public List<String> getItems() { return items; }
    public void setItems(List<String> items) { this.items = items != null ? new ArrayList<>(items) : new ArrayList<>(); }

    public boolean isOpened() { return opened; }
    public void setOpened(boolean opened) { this.opened = opened; }

    /**
     * 取出容器中的物品（移除并返回）
     */
    public String removeItem(int index) {
        if (index < 0 || index >= items.size()) return null;
        return items.remove(index);
    }

    /**
     * 检查容器是否为空
     */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * 序列化容器数据（用于存档）
     */
    public List<String> serialize() {
        List<String> list = new ArrayList<>();
        list.add(id != null ? id : "");
        list.add(String.valueOf(opened));
        list.add(String.valueOf(items.size()));
        list.addAll(items);
        return list;
    }

    /**
     * 反序列化容器数据（用于读档）
     */
    public static Container deserialize(List<String> list) {
        if (list == null || list.size() < 3) return null;
        Container c = new Container();
        c.setId(list.get(0).isEmpty() ? null : list.get(0));
        c.setOpened(Boolean.parseBoolean(list.get(1)));
        int itemCount = Integer.parseInt(list.get(2));
        for (int i = 0; i < itemCount && i + 3 < list.size(); i++) {
            c.items.add(list.get(i + 3));
        }
        return c;
    }
}
