package com.xiaowu.game.starveil.game.ecs.comp;

/**
 * 实体死亡事件（由死亡系统发布）。
 */
public final class NpcDiedEvent {
    public final int entity;

    public NpcDiedEvent(int entity) {
        this.entity = entity;
    }
}
