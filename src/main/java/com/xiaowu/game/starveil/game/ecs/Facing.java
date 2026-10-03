package com.xiaowu.game.starveil.game.ecs;

/**
 * 统一朝向枚举 —— 替代原先 Player.Facing / NPC.Facing 两套并列枚举。
 * 名称保持不变以兼容存档（按 name() 序列化）。
 */
public enum Facing {
    LEFT, RIGHT, UP, DOWN;

    public boolean isHorizontal() {
        return this == LEFT || this == RIGHT;
    }
}
