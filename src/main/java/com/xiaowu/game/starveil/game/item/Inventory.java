package com.xiaowu.game.starveil.game.item;

import com.xiaowu.game.starveil.infrastructure.logging.LoggerManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 背包 - 9格背包数组 + 1个手上格子
 */
public class Inventory {
    private static final int BACKPACK_SIZE = 9;
    private String[] backpackSlots = new String[BACKPACK_SIZE]; // 存储物品ID
    private String handSlot; // 手上物品ID

    public Inventory() {
        backpackSlots = new String[BACKPACK_SIZE];
        handSlot = null;
    }

    /**
     * 获取背包格子
     */
    public String getBackpackSlot(int index) {
        if (index < 0 || index >= BACKPACK_SIZE) return null;
        return backpackSlots[index];
    }

    /**
     * 设置背包格子
     */
    public void setBackpackSlot(int index, String itemId) {
        if (index < 0 || index >= BACKPACK_SIZE) return;
        backpackSlots[index] = itemId;
    }

    /**
     * 获取手上物品ID
     */
    public String getHandSlot() {
        return handSlot;
    }

    /**
     * 设置手上物品
     */
    public void setHandSlot(String itemId) {
        this.handSlot = itemId;
    }

    /**
     * 背包格与手上物品交互：手上空则拾取，手上有则放下，都有则交换
     */
    public void swapWithHand(int backpackIndex) {
        if (backpackIndex < 0 || backpackIndex >= BACKPACK_SIZE) return;
        String backpackItem = backpackSlots[backpackIndex];

        if (handSlot == null && backpackItem != null) {
            handSlot = backpackItem;
            backpackSlots[backpackIndex] = null;
        } else if (handSlot != null && backpackItem == null) {
            backpackSlots[backpackIndex] = handSlot;
            handSlot = null;
        } else if (handSlot != null && backpackItem != null) {
            backpackSlots[backpackIndex] = handSlot;
            handSlot = backpackItem;
        }
    }

    /**
     * 交换两个背包格
     */
    public void swapBackpackSlots(int from, int to) {
        if (from < 0 || from >= BACKPACK_SIZE || to < 0 || to >= BACKPACK_SIZE) return;
        String temp = backpackSlots[from];
        backpackSlots[from] = backpackSlots[to];
        backpackSlots[to] = temp;
    }

    /**
     * 手上物品放入第一个空格
     */
    public boolean moveHandToFirstEmpty() {
        if (handSlot == null) return false;
        for (int i = 0; i < BACKPACK_SIZE; i++) {
            if (backpackSlots[i] == null) {
                backpackSlots[i] = handSlot;
                handSlot = null;
                return true;
            }
        }
        return false;
    }

    /**
     * 序列化背包数据（用于存档）
     */
    public List<String> serializeBackpack() {
        List<String> list = new ArrayList<>();
        for (String id : backpackSlots) {
            list.add(id != null ? id : "");
        }
        return list;
    }

    /**
     * 反序列化背包数据（用于读档）
     */
    public void deserializeBackpack(List<String> list) {
        if (list == null) return;
        for (int i = 0; i < BACKPACK_SIZE && i < list.size(); i++) {
            String id = list.get(i);
            backpackSlots[i] = (id != null && !id.isEmpty()) ? id : null;
        }
    }

    /**
     * 获取背包大小
     */
    public int getBackpackSize() {
        return BACKPACK_SIZE;
    }

    /**
     * 清空背包
     */
    public void clear() {
        for (int i = 0; i < BACKPACK_SIZE; i++) {
            backpackSlots[i] = null;
        }
        handSlot = null;
    }
}
