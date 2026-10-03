package com.xiaowu.game.starveil.game.world;

/**
 * 玩法模式 —— NORMAL 的衍生变体。
 *
 * <p>与 {@code ChapterMode}（视觉小说 / 普通）是不同层面的东西：
 * {@code ChapterMode} 决定「要不要加载世界」，本枚举决定「世界怎么玩」。
 *
 * <p>每个模式可以声明一个<b>模型变体</b>，它会全局应用到所有精灵 ——
 * 实体本身不需要知道自己处在哪个模式，贴图解析时会自动按变体查找，
 * 找不到就回退到普通模型集。
 */
public enum GameplayMode {

    /** 原有的俯视玩法：无重力。 */
    NORMAL(null, false),

    /**
     * 重力玩法：把原有的 {@code y} 轴当作 {@code z}（高度），
     * 实体在竖直方向自动下落，直到被空气墙或地图边界挡住。
     *
     * <p>模型变体为 {@code LENGTHWAYS} —— 配置里写
     * {@code "LEFT:LENGTHWAYS:WALK"} 即可为该模式单独指定贴图。
     */
    GRAVITY("LENGTHWAYS", true);

    private final String modelVariant;
    private final boolean gravity;

    GameplayMode(String modelVariant, boolean gravity) {
        this.modelVariant = modelVariant;
        this.gravity = gravity;
    }

    /** 该模式使用的模型变体；null = 普通模型集。 */
    public String modelVariant() {
        return modelVariant;
    }

    /** 是否施加竖直方向的自动下落。 */
    public boolean hasGravity() {
        return gravity;
    }

    /** 解析模式名（忽略大小写），不认识返回 null。 */
    public static GameplayMode fromName(String name) {
        if (name == null) return null;
        String n = name.trim();
        for (GameplayMode m : values()) {
            if (m.name().equalsIgnoreCase(n)) {
                return m;
            }
        }
        return null;
    }

    /** 解析模式名，失败退回 {@link #NORMAL}。 */
    public static GameplayMode fromNameOrNormal(String name) {
        GameplayMode m = fromName(name);
        return m != null ? m : NORMAL;
    }
}
