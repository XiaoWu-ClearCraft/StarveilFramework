package com.xiaowu.game.starveil.game.ecs;

/**
 * 实体动画状态（键语法里的「效果」段）。
 *
 * <p>这些名字是<b>内置保留名</b>：第三方插件不得用它们作为变体名，
 * 否则 {@code LEFT:WALK} 就无法判断 WALK 是「效果」还是「变体」——
 * 键语法的无歧义性正建立在「变体名不与内置效果名冲突」之上。
 */
public enum AnimState {
    /** 待机。 */
    IDLE,
    /** 行走。 */
    WALK,
    /** 急停 —— 从疾跑减速到静止期间播放，可被其他动作打断。 */
    SKID;

    /** 解析内置状态名（忽略大小写），不认识返回 null。 */
    public static AnimState fromName(String name) {
        if (name == null) return null;
        for (AnimState s : values()) {
            if (s.name().equalsIgnoreCase(name.trim())) {
                return s;
            }
        }
        return null;
    }

    /** 是否是内置保留名。 */
    public static boolean isBuiltIn(String name) {
        return fromName(name) != null;
    }

    /** 所有内置名（大写），用于报错信息与文档。 */
    public static String[] builtInNames() {
        AnimState[] all = values();
        String[] out = new String[all.length];
        for (int i = 0; i < all.length; i++) {
            out[i] = all[i].name();
        }
        return out;
    }
}
