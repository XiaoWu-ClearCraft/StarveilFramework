package com.xiaowu.game.starveil.game.ecs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 轻量 ECS 世界容器。
 *
 * <p>实体以 int id 表示，id 在 World 生命周期内不回收（新游戏/读档时销毁整个 World，
 * 避免“陈旧实体”风险）。每类组件用数组按实体 id 索引存储。</p>
 *
 * <p>World 不是线程安全的，调用方需保证在 JavaFX Application 线程上驱动。</p>
 */
public final class World {
    private final Map<Class<? extends Component>, Object[]> store = new IdentityHashMap<>();
    private final List<EcsSystem> systems = new ArrayList<>();
    private final Map<Class<?>, Object> resources = new HashMap<>();
    private final EventBus eventBus = new EventBus();

    private boolean[] alive = new boolean[64];
    private int entityCount = 0;

    public int newEntity() {
        ensureCapacity(entityCount + 1);
        alive[entityCount] = true;
        return entityCount++;
    }

    public boolean isAlive(int entity) {
        return entity >= 0 && entity < entityCount && alive[entity];
    }

    public void destroy(int entity) {
        if (!isAlive(entity)) {
            return;
        }
        alive[entity] = false;
        for (Object[] data : store.values()) {
            if (entity < data.length) {
                data[entity] = null;
            }
        }
    }

    // ============ 组件 ============

    public <T extends Component> void add(int entity, T component) {
        if (!isAlive(entity)) {
            throw new IllegalStateException("entity " + entity + " 已销毁，不能添加组件");
        }
        Object[] data = store.computeIfAbsent(component.getClass(),
                k -> new Object[alive.length]);
        data[entity] = component;
    }

    @SuppressWarnings("unchecked")
    public <T extends Component> T get(int entity, Class<T> type) {
        if (!isAlive(entity)) {
            return null;
        }
        Object[] data = store.get(type);
        if (data == null || entity >= data.length) {
            return null;
        }
        return (T) data[entity];
    }

    @SuppressWarnings("unchecked")
    public <T extends Component> T remove(int entity, Class<T> type) {
        if (!isAlive(entity)) {
            return null;
        }
        Object[] data = store.get(type);
        if (data == null || entity >= data.length) {
            return null;
        }
        T removed = (T) data[entity];
        data[entity] = null;
        return removed;
    }

    public boolean has(int entity, Class<? extends Component> type) {
        return get(entity, type) != null;
    }

    /** 返回同时拥有全部给定组件的存活实体数组。 */
    public int[] view(Class<? extends Component>... all) {
        int[] result = new int[aliveCount()];
        int w = 0;
        for (int e = 0; e < entityCount; e++) {
            if (!alive[e]) {
                continue;
            }
            boolean ok = true;
            for (Class<? extends Component> type : all) {
                Object[] data = store.get(type);
                if (data == null || e >= data.length || data[e] == null) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                result[w++] = e;
            }
        }
        int[] trimmed = new int[w];
        System.arraycopy(result, 0, trimmed, 0, w);
        return trimmed;
    }

    /** 返回所有存活实体。 */
    public int[] entities() {
        return view();
    }

    public int entityCount() {
        return entityCount;
    }

    public int aliveCount() {
        int n = 0;
        for (int e = 0; e < entityCount; e++) {
            if (alive[e]) {
                n++;
            }
        }
        return n;
    }

    private void ensureCapacity(int required) {
        if (required <= alive.length) {
            return;
        }
        int newCap = alive.length;
        while (newCap < required) {
            newCap <<= 1;
        }
        boolean[] newAlive = new boolean[newCap];
        System.arraycopy(alive, 0, newAlive, 0, entityCount);
        alive = newAlive;
        if (!store.isEmpty()) {
            for (Map.Entry<Class<? extends Component>, Object[]> entry
                    : store.entrySet()) {
                Object[] data = entry.getValue();
                Object[] grown = new Object[newCap];
                System.arraycopy(data, 0, grown, 0, data.length);
                entry.setValue(grown);
            }
        }
    }

    // ============ 系统 ============

    public void addSystem(EcsSystem system) {
        systems.add(system);
        system.addedTo(this);
    }

    public void removeSystem(EcsSystem system) {
        if (systems.remove(system)) {
            system.removedFrom(this);
        }
    }

    /**
     * 移除全部系统。
     *
     * <p>卸下世界后再次挂载时必须先清空，否则同一个系统会被注册两份、
     * 每帧跑两次 —— 表现为「玩家和 NPC 移速翻倍」。
     */
    public void clearSystems() {
        for (EcsSystem system : new ArrayList<>(systems)) {
            removeSystem(system);
        }
    }

    public int systemCount() {
        return systems.size();
    }

    // ============ 剧情暂停 ============

    /** 剧情（视觉小说）期间是否冻结世界模拟。 */
    private boolean storyPaused = false;

    /**
     * 玩法模式 —— NORMAL（无重力）或 GRAVITY（把 y 当 z，竖直自动下落）。
     *
     * <p>模式还决定全局模型变体；实体不需要知道当前模式，
     * 贴图解析时会自动按变体查找并在缺失时回退。
     */
    private com.xiaowu.game.starveil.game.world.GameplayMode gameplayMode =
            com.xiaowu.game.starveil.game.world.GameplayMode.NORMAL;

    public com.xiaowu.game.starveil.game.world.GameplayMode gameplayMode() {
        return gameplayMode;
    }

    public void setGameplayMode(com.xiaowu.game.starveil.game.world.GameplayMode mode) {
        this.gameplayMode = mode == null
                ? com.xiaowu.game.starveil.game.world.GameplayMode.NORMAL
                : mode;
    }

    /**
     * 重力方向，精确到角度。约定 {@code 0° = 向下（+y）}，顺时针为正：
     * 90° = 右、180° = 上、270° = 左。
     *
     * <p>存在 ECS 世界上而不是 {@code Gravity} 组件里，因为它是全局的；
     * 方向改变时不需要重建任何实体或组件。
     */
    private double gravityAngleDegrees = 0;

    public double gravityAngleDegrees() {
        return gravityAngleDegrees;
    }

    public void setGravityAngleDegrees(double degrees) {
        this.gravityAngleDegrees = degrees;
    }

    /** 剧情暂停期间被单独放行的系统类型。 */
    private final java.util.Set<Class<? extends EcsSystem>> storyPauseExempt = new java.util.HashSet<>();

    /**
     * 剧情暂停开关。
     *
     * <p>与「暂停菜单」不同：这里只冻结世界模拟，不弹任何界面。
     * 由 {@code ChatManager} 在进入/退出对话层时驱动，章节可以用
     * {@link #exemptFromStoryPause(Class)} 让个别系统继续跑（例如 NPC 走动）。
     */
    public void setStoryPaused(boolean paused) {
        this.storyPaused = paused;
    }

    public boolean isStoryPaused() {
        return storyPaused;
    }

    /**
     * 让某个系统在剧情暂停期间继续运行，例如
     * {@code world.exemptFromStoryPause(NpcSystem.class)}。
     */
    public void exemptFromStoryPause(Class<? extends EcsSystem> systemType) {
        if (systemType != null) {
            storyPauseExempt.add(systemType);
        }
    }

    public void clearStoryPauseExemptions() {
        storyPauseExempt.clear();
    }

    /** 该系统此刻是否应当被驱动。 */
    public boolean shouldUpdate(EcsSystem system) {
        if (!storyPaused || system.runsDuringStoryPause()) {
            return true;
        }
        return storyPauseExempt.contains(system.getClass());
    }

    /** 每帧驱动：按注册顺序执行所有系统（剧情暂停时跳过未豁免的）。 */
    public void update(double deltaTime) {
        for (EcsSystem system : systems) {
            if (!shouldUpdate(system)) {
                continue;
            }
            system.update(this, deltaTime);
        }
    }

    // ============ 资源 ============

    public <T> void putResource(Class<T> key, T value) {
        resources.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getResource(Class<T> key) {
        return (T) resources.get(key);
    }

    // ============ 事件 ============

    public EventBus events() {
        return eventBus;
    }
}
