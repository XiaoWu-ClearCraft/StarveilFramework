package com.xiaowu.game.starveil.game.world;

import com.xiaowu.game.starveil.game.ecs.Component;

import java.util.UUID;

/**
 * 掉落物组件 —— 掉落物现在作为 ECS 实体管理。
 * 实体同时挂 {@link com.xiaowu.game.starveil.game.ecs.comp.Transform}（位置/拾取半径）
 * 与 {@link com.xiaowu.game.starveil.game.ecs.comp.Attributes}（starveil:item_name 等）。
 */
public final class DroppedItem implements Component {

    /** 掉落物实例 id（UUID，用于 UI 行区分）。 */
    public final String id;
    /** ItemRegistry 中的物品 id。 */
    public final String itemId;
    /** 掉落时间戳（可用于过期逻辑）。 */
    public long dropTime;

    public DroppedItem(String itemId) {
        this.id = UUID.randomUUID().toString();
        this.itemId = itemId;
        this.dropTime = System.currentTimeMillis();
    }
}
