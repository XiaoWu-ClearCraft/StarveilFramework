package com.xiaowu.game.starveil.game.ecs.comp;

import com.xiaowu.game.starveil.game.ecs.AnimKey;
import com.xiaowu.game.starveil.game.ecs.AnimKeyRegistry;
import com.xiaowu.game.starveil.game.ecs.AnimState;
import com.xiaowu.game.starveil.game.ecs.Component;
import com.xiaowu.game.starveil.game.ecs.Facing;
import com.xiaowu.game.starveil.render.AnimationSheet;
import com.xiaowu.game.starveil.render.AnimationSheetView;

import java.util.HashMap;
import java.util.Map;

/**
 * 精灵组件 —— 持有场景图节点与「朝向/状态 → 动画表」映射。
 * 渲染相关决策（选帧/翻转/坐标写回）集中在 Sprite 系统。
 *
 * <p>贴图有两个来源，查找时新结构优先：
 * <ol>
 *   <li>{@link #keyedSheets} —— 新的动画键体系，支持变体与任意效果
 *       （{@code LEFT}、{@code LEFT:LENGTHWAYS}、{@code LEFT:LENGTHWAYS:WALK} …）；</li>
 *   <li>{@link #textureMap} —— 旧结构（{@code AnimState × Facing}），
 *       所有既有角色配置都走它，作为无变体时的回退。</li>
 * </ol>
 */
public final class Sprite implements Component {
    public final AnimationSheetView view;
    public final Map<AnimState, Map<Facing, AnimationSheet>> textureMap = new HashMap<>();

    /** 新动画键体系的贴图表；变体与效果都可以在这里自由组合。 */
    public final Map<AnimKey, AnimationSheet> keyedSheets = new HashMap<>();

    /**
     * 当前模型变体（如 {@code LENGTHWAYS}）。
     * null = 普通（NORMAL）模型集。由游玩模式统一决定。
     */
    public String modelVariant = null;

    public boolean enableFacing = true;
    public Facing textureFacing = Facing.LEFT;
    public Facing initialFacing = Facing.LEFT;
    public Facing facing = Facing.LEFT;
    public Facing lastHorizontalFacing = Facing.LEFT;
    public Facing displayFacing = Facing.LEFT;
    public boolean moving;
    public AnimState currentState = AnimState.IDLE;
    public double scaleX = 1.0;
    /** true = 玩家（尺寸随贴图比例推导）；false = NPC（固定宽高）。 */
    public boolean playerLike = false;

    /**
     * 是否正在急停（疾跑减速到静止期间）。
     *
     * <p>独立于 {@link #moving}：急停期间 {@code moving} 已是 false，
     * 但姿态要显示急停而不是待机。会被其他动作打断 —— 取消该标志即可。
     */
    public boolean skidding;

    public Sprite() {
        view = new AnimationSheetView();
        view.setPreserveRatio(true);
    }

    public void setMoving(boolean value) {
        this.moving = value;
        if (skidding) {
            this.currentState = AnimState.SKID;
            return;
        }
        this.currentState = value ? AnimState.WALK : AnimState.IDLE;
    }

    /** 直接指定动画状态（优先级高于 {@link #setMoving}）。 */
    public void setState(AnimState state) {
        this.currentState = state == null ? AnimState.IDLE : state;
        this.moving = state == AnimState.WALK;
    }

    // ==================== 贴图查找 ====================

    /** 某个动画键是否有贴图（新结构或旧结构任一命中）。 */
    public boolean hasSheet(AnimKey key) {
        return sheetFor(key) != null;
    }

    /**
     * 按动画键取贴图：先查新结构，再回退旧结构。
     *
     * <p>旧结构没有变体概念，因此带变体的键只在 {@link #keyedSheets} 里找。
     */
    public AnimationSheet sheetFor(AnimKey key) {
        if (key == null) {
            return null;
        }
        AnimationSheet direct = keyedSheets.get(key);
        if (direct != null) {
            return direct;
        }
        if (key.variant() != null || key.effect() == null) {
            return null;
        }
        Map<Facing, AnimationSheet> byFacing = textureMap.get(key.effect());
        return byFacing == null ? null : byFacing.get(key.facing());
    }

    /**
     * 解析结果：用哪张贴图，以及需要额外施加多少度旋转。
     *
     * <p>命中的是角度专用贴图时 {@code rotateDegrees = 0}（图本身就是那个角度的）；
     * 命中普通贴图时按重力角度旋转。
     */
    public record SheetChoice(AnimationSheet sheet, int rotateDegrees) {
    }

    /**
     * 为「当前状态 + 给定朝向」解析最合适的贴图。
     *
     * <p>回退顺序：变体 × 效果 × 角度（见 {@link AnimKeyRegistry#resolve}），
     * 若该朝向无贴图则退回最近的水平朝向（上下没有专门贴图时的惯例）。
     *
     * @return 命中的贴图；都没有则 null（调用方自行兜底）
     */
    public AnimationSheet resolveSheet(Facing wantFacing) {
        SheetChoice choice = resolveSheet(wantFacing, 0);
        return choice == null ? null : choice.sheet();
    }

    /**
     * 带重力角度的解析。
     *
     * @param gravityAngle 重力方向角度；0 表示不需要旋转
     */
    public SheetChoice resolveSheet(Facing wantFacing, int gravityAngle) {
        SheetChoice choice = resolveStrict(wantFacing, gravityAngle);
        if (choice == null && (wantFacing == Facing.UP || wantFacing == Facing.DOWN)) {
            choice = resolveStrict(lastHorizontalFacing, gravityAngle);
        }
        if (choice == null && wantFacing != facing) {
            choice = resolveStrict(facing, gravityAngle);
        }
        return choice;
    }

    private SheetChoice resolveStrict(Facing wantFacing, int gravityAngle) {
        AnimKey key = AnimKeyRegistry.resolve(
                this::hasSheet, wantFacing, modelVariant, currentState, gravityAngle);
        if (key == null) {
            return null;
        }
        AnimationSheet sheet = sheetFor(key);
        if (sheet == null) {
            return null;
        }
        // 角度专用贴图原样绘制；普通贴图按重力角度旋转
        return new SheetChoice(sheet, key.hasAngle() ? 0 : gravityAngle);
    }

    /** 任取一张已有贴图（最后的兜底，避免实体整帧无图）。 */
    public AnimationSheet anySheet() {
        for (AnimationSheet s : keyedSheets.values()) {
            if (s != null) return s;
        }
        for (Map<Facing, AnimationSheet> byFacing : textureMap.values()) {
            if (byFacing == null) continue;
            for (AnimationSheet s : byFacing.values()) {
                if (s != null) return s;
            }
        }
        return null;
    }
}
