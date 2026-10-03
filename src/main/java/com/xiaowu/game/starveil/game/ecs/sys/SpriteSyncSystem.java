package com.xiaowu.game.starveil.game.ecs.sys;

import com.xiaowu.game.starveil.game.ecs.AnimState;
import com.xiaowu.game.starveil.game.ecs.Facing;
import com.xiaowu.game.starveil.game.ecs.EcsSystem;
import com.xiaowu.game.starveil.game.ecs.World;
import com.xiaowu.game.starveil.game.ecs.comp.Sprite;
import com.xiaowu.game.starveil.game.ecs.comp.Transform;
import com.xiaowu.game.starveil.game.world.WorldMap;
import com.xiaowu.game.starveil.render.AnimationSheet;

import java.util.Map;

/**
 * 精灵同步系统：把组件里的 Transform / 朝向 / 移动状态写回 JavaFX 视图。
 * - 视图缺失时自动挂到世界 Pane（支持切图后重挂）
 * - 选择当前状态与朝向的动画表并播放
 * - 根据朝向计算水平翻转
 */
public final class SpriteSyncSystem implements EcsSystem {
    private static final Facing[] FACINGS = Facing.values();

    /**
     * 纯表现层系统：剧情暂停期间也要继续跑。
     *
     * <p>否则被单独豁免的系统（比如 {@link NpcSystem}）让 NPC 动了，
     * 视图却停在原地 —— 看起来像 NPC 瞬移。
     */
    @Override
    public boolean runsDuringStoryPause() {
        return true;
    }

    @Override
    public void update(World world, double deltaTime) {
        WorldMap map = world.getResource(WorldMap.class);
        if (map == null) {
            return;
        }
        javafx.scene.layout.Pane pane = map.getWorld();
        if (pane == null) {
            return;
        }
        for (int e : world.view(Sprite.class, Transform.class)) {
            Sprite spr = world.get(e, Sprite.class);
            Transform t = world.get(e, Transform.class);
            // 死亡动画期间由 NpcDeathSystem 控制视图缩放/透明度，跳过常规同步
            com.xiaowu.game.starveil.game.ecs.comp.Health h = world.get(e, com.xiaowu.game.starveil.game.ecs.comp.Health.class);
            if (h != null && h.dead) {
                continue;
            }
            // 模型变体由玩法模式统一决定（重力模式全局使用 LENGTHWAYS）。
            // 放在这里而不是实体创建时，是为了模式切换后立刻生效，
            // 且实体自身完全不需要知道当前处于哪个模式。
            spr.modelVariant = world.gameplayMode().modelVariant();
            sync(spr, t, pane, (int) Math.round(world.gravityAngleDegrees()));
        }
    }

    private static void sync(Sprite spr, Transform t, javafx.scene.layout.Pane pane, int gravityAngle) {
        if (!pane.getChildren().contains(spr.view)) {
            pane.getChildren().add(spr.view);
        }

        // 贴图解析统一交给 Sprite：新动画键体系（支持变体与角度）优先，
        // 内部会回退到旧的 textureMap 结构，并处理上下朝向 → 水平朝向的退化。
        Sprite.SheetChoice choice = spr.resolveSheet(spr.displayFacing, gravityAngle);
        if (choice == null) {
            AnimationSheet fallback = spr.anySheet();
            if (fallback != null) {
                // 兜底贴图没有角度概念，按重力角度旋转
                choice = new Sprite.SheetChoice(fallback, gravityAngle);
            }
        }
        AnimationSheet sheet = choice == null ? null : choice.sheet();
        // 重力方向倾斜时，没有角度专用贴图就整体旋转
        spr.view.setRotate(choice == null ? 0 : choice.rotateDegrees());

        if (sheet != null) {
            spr.view.playSheet(sheet);
            spr.view.setFitWidth(t.width);
            int fw = sheet.getFrameWidth();
            int fh = sheet.getFrameHeight();
            if (spr.playerLike && fw > 0 && fh > 0) {
                t.height = t.width * ((double) fh / fw);
            } else if (!spr.playerLike) {
                spr.view.setFitHeight(t.height);
            }
        }

        if (spr.enableFacing) {
            if (spr.displayFacing == Facing.LEFT) {
                spr.scaleX = (spr.textureFacing == Facing.LEFT) ? 1 : -1;
            } else if (spr.displayFacing == Facing.RIGHT) {
                spr.scaleX = (spr.textureFacing == Facing.RIGHT) ? 1 : -1;
            } else {
                spr.scaleX = (spr.textureFacing == spr.lastHorizontalFacing) ? 1 : -1;
            }
        } else {
            spr.scaleX = 1;
        }
        spr.view.setScaleX(spr.scaleX);

        spr.view.setLayoutX(t.x);
        spr.view.setLayoutY(t.y);
    }

    /** 地图重载后把缺失的精灵视图全部挂回世界（外部调用）。 */
    public static void reattachAll(World world) {
        WorldMap map = world.getResource(WorldMap.class);
        if (map == null) {
            return;
        }
        javafx.scene.layout.Pane pane = map.getWorld();
        int gravityAngle = (int) Math.round(world.gravityAngleDegrees());
        for (int e : world.view(Sprite.class, Transform.class)) {
            Sprite spr = world.get(e, Sprite.class);
            Transform t = world.get(e, Transform.class);
            spr.modelVariant = world.gameplayMode().modelVariant();
            sync(spr, t, pane, gravityAngle);
        }
    }
}
