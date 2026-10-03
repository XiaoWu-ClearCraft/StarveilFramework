package com.xiaowu.game.starveil.game.attack;

import com.xiaowu.game.starveil.game.ecs.World;
import com.xiaowu.game.starveil.game.ecs.comp.Charge;
import com.xiaowu.game.starveil.game.ecs.comp.Health;
import com.xiaowu.game.starveil.game.ecs.comp.Magic;
import com.xiaowu.game.starveil.game.ecs.comp.Npc;
import com.xiaowu.game.starveil.game.ecs.comp.Transform;
import com.xiaowu.game.starveil.game.world.WorldMap;
import com.xiaowu.game.starveil.infrastructure.audio.AudioManager;
import javafx.animation.AnimationTimer;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.List;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * 法阵攻击系统 — 管理蓄力、法阵渲染、警告圈、气波扩散和伤害判定
 */
public class MagicCircleAttack {

    private static final double MAGIC_CIRCLE_RADIUS = 180;
    private static final double WARNING_CIRCLE_RADIUS = 350;
    private static final int WARNING_FLASH_COUNT = 3;
    private static final double SHOCKWAVE_DURATION = 0.5;
    private static final long WARNING_FLASH_INTERVAL = 80_000_000L; // 80ms
    private static final double MAGIC_CIRCLE_APPEAR_DURATION = 0.3; // 法阵出现动画时长(秒)
    private static final double MAGIC_CIRCLE_DISAPPEAR_DURATION = 0.25; // 法阵消失动画时长(秒)

    // 状态
    private int state = 0; // 0=idle, 1=charging, 2=warning, 3=shockwave
    private double squareAngle = 0;
    private double circleAngle = 0;
    private long chargeStartTime = 0; // 蓄力开始时间（用于法阵出现动画）
    private long releaseTime = 0; // 释放时间（用于法阵消失动画）
    private boolean fadingOut = false; // 法阵是否正在淡出

    // 暂停支持
    private boolean paused = false;
    private long totalPausedNs = 0;
    private long pauseStartNs = 0;

    private long nowNs() {
        return System.nanoTime() - totalPausedNs;
    }

    public void setPaused(boolean p) {
        if (p == paused) return;
        paused = p;
        if (p) {
            pauseStartNs = System.nanoTime();
        } else {
            totalPausedNs += System.nanoTime() - pauseStartNs;
        }
    }

    // 渲染容器
    private Pane worldPane;
    private Group magicCircleGroup;
    private Group warningGroup;
    private Circle warningCircle;
    private Circle shockwaveRing;
    private Circle shockwaveFill;
    private javafx.scene.Node playerView; // 用于确定层级

    // 玩家（ECS 实体）
    private World ecsWorld;
    private int playerEntity = -1;
    private WorldMap worldMap;

    // 动画
    private AnimationTimer animTimer;

    // 警告闪烁
    private long lastFlashTime = 0;
    private int flashCount = 0;
    private boolean warningVisible = true;

    // 气波
    private double shockwaveRadius = 0;
    private long shockwaveStartTime = 0;
    private Group shockwaveGroup; // 气波容器
    private boolean shockwaveAdded = false; // 防止重复添加

    // 装饰小圆相关（保存引用用于旋转）
    private List<Text> decoTextsTop = new ArrayList<>();
    private List<Text> decoTextsBottom = new ArrayList<>();

    // 已受伤的 NPC（实体 id 缓存）
    private final List<Integer> damagedNPCs = new ArrayList<>();

    private double pcx() {
        Transform t = ecsWorld != null && playerEntity >= 0 ? ecsWorld.get(playerEntity, Transform.class) : null;
        return t != null ? t.centerX() : 0;
    }

    private double pcy() {
        Transform t = ecsWorld != null && playerEntity >= 0 ? ecsWorld.get(playerEntity, Transform.class) : null;
        return t != null ? t.centerY() : 0;
    }

    private Charge charge() {
        return ecsWorld != null && playerEntity >= 0 ? ecsWorld.get(playerEntity, Charge.class) : null;
    }

    private double chargeDamage() {
        Charge c = charge();
        return c != null ? c.calculateDamage() : 0;
    }

    public MagicCircleAttack(Pane worldPane, javafx.scene.Node playerView, World ecsWorld, int playerEntity, WorldMap worldMap) {
        this.worldPane = worldPane;
        this.playerView = playerView;
        this.ecsWorld = ecsWorld;
        this.playerEntity = playerEntity;
        this.worldMap = worldMap;

        magicCircleGroup = new Group();
        magicCircleGroup.setMouseTransparent(true);

        warningCircle = new Circle(0, 0, WARNING_CIRCLE_RADIUS);
        warningCircle.setFill(null);
        warningCircle.setStroke(Color.rgb(255, 60, 60));
        warningCircle.setStrokeWidth(3);
        warningCircle.setMouseTransparent(true);
        warningCircle.setVisible(false);

        shockwaveRing = new Circle(0, 0, 0);
        shockwaveRing.setFill(Color.TRANSPARENT);
        shockwaveRing.setStroke(Color.rgb(255, 100, 100, 0.8));
        shockwaveRing.setStrokeWidth(4);
        shockwaveRing.setMouseTransparent(true);
        shockwaveRing.setVisible(false);

        shockwaveFill = new Circle(0, 0, 0);
        shockwaveFill.setFill(Color.rgb(255, 80, 80, 0.15));
        shockwaveFill.setStroke(null);
        shockwaveFill.setMouseTransparent(true);
        shockwaveFill.setVisible(false);

        shockwaveGroup = new Group(shockwaveRing, shockwaveFill);
        shockwaveGroup.setMouseTransparent(true);

        animTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                tick();
            }
        };
        animTimer.start();
    }

    private void tick() {
        if (paused) return;
        long nowNs = nowNs();
        double nowSec = nowNs / 1_000_000_000.0;

        if (state == 1) {
            // 蓄力中 — 旋转动画 + 更新位置 + 出现/消失动画
            squareAngle += 15.0 / 60.0; // 15度/秒
            circleAngle -= 10.0 / 60.0;
            if (squareAngle >= 360) squareAngle -= 360;
            if (circleAngle <= -360) circleAngle += 360;

            double cx = pcx();
            double cy = pcy();
            magicCircleGroup.setLayoutX(cx);
            magicCircleGroup.setLayoutY(cy);

            if (fadingOut) {
                // 消失动画：淡出 + 微缩
                double elapsed = (nowNs - releaseTime) / 1_000_000_000.0;
                double progress = Math.min(elapsed / MAGIC_CIRCLE_DISAPPEAR_DURATION, 1.0);
                // ease-in 曲线
                double fadeScale = 1.0 - progress * progress * 0.15;
                double fadeOpacity = 1.0 - progress;
                magicCircleGroup.setScaleX(fadeScale);
                magicCircleGroup.setScaleY(fadeScale);
                magicCircleGroup.setOpacity(fadeOpacity);

                if (progress >= 1.0) {
                    // 消失动画完成，进入警告阶段
                    finishRelease();
                }
            } else {
                // 出现动画：从0放大到1
                double appearElapsed = (nowNs - chargeStartTime) / 1_000_000_000.0;
                double appearProgress = Math.min(appearElapsed / MAGIC_CIRCLE_APPEAR_DURATION, 1.0);
                double scale = 1.0 - Math.pow(1.0 - appearProgress, 3);
                magicCircleGroup.setScaleX(scale);
                magicCircleGroup.setScaleY(scale);
            }

            // 更新正方形旋转
            updateSquareRotation();
            // 更新装饰小圆旋转
            updateDecoCircleRotation();
        } else if (state == 2) {
            // 警告阶段 — 闪烁
            if (nowNs - lastFlashTime >= WARNING_FLASH_INTERVAL) { // 150ms
                lastFlashTime = nowNs;
                warningVisible = !warningVisible;
                warningCircle.setVisible(warningVisible);
                flashCount++;
                if (flashCount >= WARNING_FLASH_COUNT * 2) {
                    // 闪烁结束，进入气波
                    warningCircle.setVisible(false);
                    state = 3;
                    shockwaveStartTime = nowNs;
                    shockwaveRadius = 10;
                    shockwaveRing.setVisible(true);
                    shockwaveFill.setVisible(true);
                    shockwaveGroup.setVisible(true);

                    // 将气波加到场景（只添加一次）
                    if (!shockwaveAdded) {
                        shockwaveGroup.setLayoutX(pcx());
                        shockwaveGroup.setLayoutY(pcy());
                        int playerIdx = worldPane.getChildren().indexOf(playerView);
                        if (playerIdx >= 0) {
                            worldPane.getChildren().add(playerIdx, shockwaveGroup);
                        } else {
                            worldPane.getChildren().add(shockwaveGroup);
                        }
                        shockwaveAdded = true;

                        // 播放气波攻击音效
                        AudioManager.getInstance().playSound("starveil:sounds/sound/shockwave-attack.mp3");
                    }
                }
            }
        } else if (state == 3) {
            // 气波阶段
            double elapsed = (nowNs - shockwaveStartTime) / 1_000_000_000.0;
            double progress = Math.min(elapsed / SHOCKWAVE_DURATION, 1.0);
            shockwaveRadius = 10 + (WARNING_CIRCLE_RADIUS - 10) * progress;
            double opacity = 1.0 - progress;

            shockwaveRing.setRadius(shockwaveRadius);
            shockwaveRing.setStroke(Color.rgb(255, 100, 100, opacity * 0.8));
            shockwaveRing.setStrokeWidth(4 * Math.max(opacity, 0.1));

            shockwaveFill.setRadius(shockwaveRadius);
            shockwaveFill.setFill(Color.rgb(255, 80, 80, opacity * 0.15));

            applyDamageToNPCs(pcx(), pcy(), shockwaveRadius, chargeDamage());

            if (progress >= 1.0) {
                finishAttack();
            }
        }
    }

    public void startCharging() {
        if (!com.xiaowu.game.starveil.game.MagicSystem.isEnabled()) {
            return;
        }
        if (state != 0) return;
        Charge ch = charge();
        if (ch != null) {
            ch.start();
        }
        state = 1;
        squareAngle = 0;
        circleAngle = 0;
        damagedNPCs.clear();
        chargeStartTime = nowNs();

        // 构建法阵
        buildMagicCircle(magicCircleGroup, MAGIC_CIRCLE_RADIUS);

        // 将法阵放到玩家下面
        int playerIndex = worldPane.getChildren().indexOf(playerView);
        if (playerIndex >= 0) {
            worldPane.getChildren().add(playerIndex, magicCircleGroup);
        } else {
            worldPane.getChildren().add(magicCircleGroup);
        }

        double cx = pcx();
        double cy = pcy();
        magicCircleGroup.setLayoutX(cx);
        magicCircleGroup.setLayoutY(cy);
        magicCircleGroup.setScaleX(0);
        magicCircleGroup.setScaleY(0);
    }

    public boolean isActive() {
        return state != 0;
    }

    public void updateCharging() {
        if (state != 1) return;
        // tick() 中已处理
    }

    public void releaseAttack() {
        if (!com.xiaowu.game.starveil.game.MagicSystem.isEnabled()) {
            return;
        }
        if (state != 1 || fadingOut) return; // 仅在蓄力阶段响应，其他阶段忽略

        // 计算魔力消耗（基础15 + 蓄力加成，最多30）
        double duration = charge().duration();
        double magicCost = 15.0 + Math.min(duration, 3.0) * 5.0;

        Magic mg = ecsWorld.get(playerEntity, Magic.class);
        if (mg == null || !mg.consume(magicCost)) {
            // 魔力不足，取消攻击
            com.xiaowu.game.starveil.ui.core.GameUI.getInstance().addMessage("<red>魔力不足!</red>");
            cancel();
            return;
        }
        com.xiaowu.game.starveil.game.ecs.sys.VitalRegenSystem.pushMagicUI(mg);

        // 开始消失动画
        fadingOut = true;
        releaseTime = nowNs();
    }

    private void finishRelease() {
        // 移除法阵
        worldPane.getChildren().remove(magicCircleGroup);
        magicCircleGroup.getChildren().clear();
        magicCircleGroup.setOpacity(1);
        magicCircleGroup.setScaleX(1);
        magicCircleGroup.setScaleY(1);
        fadingOut = false;

        // 进入警告阶段
        state = 2;
        flashCount = 0;
        warningVisible = true;
        lastFlashTime = nowNs();

        double cx = pcx();
        double cy = pcy();
        warningCircle.setCenterX(0);
        warningCircle.setCenterY(0);
        warningCircle.setVisible(true);

        // 创建一个 Group 来放置警告圈
        warningGroup = new Group(warningCircle);
        warningGroup.setMouseTransparent(true);
        warningGroup.setLayoutX(cx);
        warningGroup.setLayoutY(cy);

        int playerIndex = worldPane.getChildren().indexOf(playerView);
        if (playerIndex >= 0) {
            worldPane.getChildren().add(playerIndex, warningGroup);
        } else {
            worldPane.getChildren().add(warningGroup);
        }
    }

    public void cancel() {
        stopCharge();
        cleanup();
    }

    private void finishAttack() {
        stopCharge();
        cleanup();
    }

    private void cleanup() {
        Logger("DEBUG", "CleanUP被执行");
        state = 0;
        shockwaveAdded = false;
        fadingOut = false;
        worldPane.getChildren().remove(magicCircleGroup);
        worldPane.getChildren().remove(warningGroup);
        worldPane.getChildren().remove(shockwaveGroup);
        magicCircleGroup.getChildren().clear();
        magicCircleGroup.setScaleX(1);
        magicCircleGroup.setScaleY(1);
        warningGroup = null;
        warningCircle.setVisible(false);
        shockwaveRing.setVisible(false);
        shockwaveFill.setVisible(false);
        damagedNPCs.clear();
    }

    // ==================== 法阵构建 ====================

    private Rectangle square1, square2;
    private List<Circle> decoCirclesOuter = new ArrayList<>();
    private List<Circle> decoCirclesInner = new ArrayList<>();

    private void buildMagicCircle(Group group, double radius) {
        group.getChildren().clear();
        decoCirclesOuter.clear();
        decoCirclesInner.clear();
        decoTextsTop.clear();
        decoTextsBottom.clear();

        double cx = 0, cy = 0;

        // 1. 背景
        RadialGradient gradient = new RadialGradient(
                0, 0, cx, cy, radius, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.rgb(180, 120, 230, 0.6)),
                new Stop(0.3, Color.rgb(120, 50, 180, 0.5)),
                new Stop(0.7, Color.rgb(60, 20, 100, 0.5)),
                new Stop(1.0, Color.rgb(40, 15, 70, 0.4))
        );
        group.getChildren().add(new Circle(cx, cy, radius) {{ setFill(gradient); }});

        Color lineColor = Color.rgb(240, 220, 255, 0.9);
        Color glowColor = Color.rgb(200, 160, 255, 0.3);

        // 2. 三根白线
        group.getChildren().add(new Circle(cx, cy, radius) {{ setFill(null); setStroke(lineColor); setStrokeWidth(2); }});
        group.getChildren().add(new Circle(cx, cy, radius * 0.95) {{ setFill(null); setStroke(lineColor); setStrokeWidth(0.5); }});
        group.getChildren().add(new Circle(cx, cy, radius * 0.90) {{ setFill(null); setStroke(lineColor); setStrokeWidth(1); }});

        // 3. 外圈文字
        String[] outerSymbols = {"π", "ω", "Ω", "Σ", "π", "ω", "Ω", "Σ"};
        double angleStep = 360.0 / outerSymbols.length;
        for (int i = 0; i < outerSymbols.length; i++) {
            double angle = Math.toRadians(i * angleStep - 90);
            double x = cx + radius * 0.78 * Math.cos(angle);
            double y = cy + radius * 0.78 * Math.sin(angle);
            Text t = new Text(outerSymbols[i]);
            t.setFont(Font.font("Serif", FontWeight.BOLD, 20));
            t.setFill(lineColor);
            t.setTextOrigin(javafx.geometry.VPos.CENTER);
            t.setLayoutX(x);
            t.setLayoutY(y);
            t.setRotate(Math.toDegrees(angle) + 90);
            group.getChildren().add(t);
        }

        // 4. 中层圆环
        double midRadius = radius * 0.7;
        group.getChildren().add(new Circle(cx, cy, midRadius) {{ setFill(null); setStroke(lineColor); setStrokeWidth(1.5); }});
        group.getChildren().add(new Circle(cx, cy, midRadius * 0.9) {{ setFill(null); setStroke(glowColor); setStrokeWidth(0.8); }});

        // 5. 内圈文字
        String[] innerSymbols = {"π", "ω", "Ω", "Σ", "π", "ω", "Ω", "Σ"};
        for (int i = 0; i < innerSymbols.length; i++) {
            double angle = Math.toRadians(i * angleStep - 90);
            double x = cx + radius * 0.55 * Math.cos(angle);
            double y = cy + radius * 0.55 * Math.sin(angle);
            Text t = new Text(innerSymbols[i]);
            t.setFont(Font.font("Serif", 14));
            t.setFill(Color.rgb(200, 180, 230, 0.7));
            t.setTextOrigin(javafx.geometry.VPos.CENTER);
            t.setLayoutX(x);
            t.setLayoutY(y);
            t.setRotate(Math.toDegrees(angle) + 90);
            group.getChildren().add(t);
        }

        // 6. 正方形（保存引用用于旋转）
        double squareSize = midRadius * 0.85 * 0.830;
        square1 = new Rectangle(cx - squareSize, cy - squareSize, squareSize * 2, squareSize * 2);
        square1.setFill(null);
        square1.setStroke(lineColor);
        square1.setStrokeWidth(1);
        group.getChildren().add(square1);

        double innerSquareSize = squareSize * 0.6;
        square2 = new Rectangle(cx - innerSquareSize, cy - innerSquareSize, innerSquareSize * 2, innerSquareSize * 2);
        square2.setFill(null);
        square2.setStroke(glowColor);
        square2.setStrokeWidth(0.5);
        group.getChildren().add(square2);

        // 7. 放射状线条
        int lineCount = 16;
        double radialOuterR = radius * 0.95;
        double radialInnerR = midRadius * 0.5;
        double radialAngleStep = 360.0 / lineCount;
        for (int i = 0; i < lineCount; i++) {
            double angle = Math.toRadians(i * radialAngleStep);
            Line line = new Line(
                    cx + radialInnerR * Math.cos(angle), cy + radialInnerR * Math.sin(angle),
                    cx + radialOuterR * Math.cos(angle), cy + radialOuterR * Math.sin(angle)
            );
            line.setStroke(Color.rgb(200, 180, 230, 0.4));
            line.setStrokeWidth(0.5);
            group.getChildren().add(line);
        }

        // 8. 中心圆
        double centerRadius = radius * 0.18;
        group.getChildren().add(new Circle(cx, cy, centerRadius) {{ setFill(null); setStroke(lineColor); setStrokeWidth(1.5); }});
        group.getChildren().add(new Circle(cx, cy, centerRadius * 0.7) {{ setFill(Color.rgb(60, 20, 100, 0.8)); setStroke(null); }});

        // 9. 装饰小圆
        double orbitRadius = radius * 0.95;
        double circleRadius = radius * 0.050;
        String[][] symbolPairs = {{">", "<"}, {"S", "V"}, {"<", ">"}, {"V", "S"}};
        int decoCount = 4;
        double decoAngleStep = 360.0 / decoCount;
        for (int i = 0; i < decoCount; i++) {
            double angle = Math.toRadians(i * decoAngleStep - 90);
            double x = cx + orbitRadius * Math.cos(angle);
            double y = cy + orbitRadius * Math.sin(angle);

            Circle outer = new Circle(x, y, circleRadius);
            outer.setFill(null);
            outer.setStroke(lineColor);
            outer.setStrokeWidth(1);
            group.getChildren().add(outer);
            decoCirclesOuter.add(outer);

            Circle inner = new Circle(x, y, circleRadius * 0.75);
            inner.setFill(null);
            inner.setStroke(glowColor);
            inner.setStrokeWidth(0.5);
            group.getChildren().add(inner);
            decoCirclesInner.add(inner);

            Text topText = new Text(symbolPairs[i][0]);
            topText.setFont(Font.font("Serif", FontWeight.BOLD, circleRadius * 0.9));
            topText.setFill(lineColor);
            topText.setTextOrigin(javafx.geometry.VPos.CENTER);
            topText.setTextAlignment(TextAlignment.CENTER);
            topText.setLayoutX(x);
            topText.setLayoutY(y - circleRadius * 0.35);
            group.getChildren().add(topText);
            decoTextsTop.add(topText);

            Text bottomText = new Text(symbolPairs[i][1]);
            bottomText.setFont(Font.font("Serif", FontWeight.BOLD, circleRadius * 0.9));
            bottomText.setFill(lineColor);
            bottomText.setTextOrigin(javafx.geometry.VPos.CENTER);
            bottomText.setTextAlignment(TextAlignment.CENTER);
            bottomText.setLayoutX(x);
            bottomText.setLayoutY(y + circleRadius * 0.35);
            group.getChildren().add(bottomText);
            decoTextsBottom.add(bottomText);
        }
    }

    private void updateSquareRotation() {
        if (square1 == null || square2 == null) return;
        double cx = 0, cy = 0;
        square1.getTransforms().clear();
        square1.getTransforms().add(new javafx.scene.transform.Rotate(squareAngle, square1.getX() + square1.getWidth() / 2, square1.getY() + square1.getHeight() / 2));
        square2.getTransforms().clear();
        square2.getTransforms().add(new javafx.scene.transform.Rotate(squareAngle, square2.getX() + square2.getWidth() / 2, square2.getY() + square2.getHeight() / 2));
    }

    private void updateDecoCircleRotation() {
        double radius = MAGIC_CIRCLE_RADIUS;
        double orbitRadius = radius * 0.95;
        double circleRadius = radius * 0.050;
        int decoCount = 4;
        double decoAngleStep = 360.0 / decoCount;
        for (int i = 0; i < decoCount; i++) {
            double angle = Math.toRadians(i * decoAngleStep - 90 + circleAngle);
            double x = orbitRadius * Math.cos(angle);
            double y = orbitRadius * Math.sin(angle);
            if (i < decoCirclesOuter.size()) {
                decoCirclesOuter.get(i).setCenterX(x);
                decoCirclesOuter.get(i).setCenterY(y);
            }
            if (i < decoCirclesInner.size()) {
                decoCirclesInner.get(i).setCenterX(x);
                decoCirclesInner.get(i).setCenterY(y);
            }
            if (i < decoTextsTop.size()) {
                decoTextsTop.get(i).setLayoutX(x);
                decoTextsTop.get(i).setLayoutY(y - circleRadius * 0.35);
            }
            if (i < decoTextsBottom.size()) {
                decoTextsBottom.get(i).setLayoutX(x);
                decoTextsBottom.get(i).setLayoutY(y + circleRadius * 0.35);
            }
        }
    }

    // ==================== 伤害判定 ====================

    private void applyDamageToNPCs(double cx, double cy, double waveRadius, double baseDamage) {
        int[] npcIds = ecsWorld != null ? ecsWorld.view(Npc.class) : new int[0];
        if (npcIds.length == 0) return;
        Logger("DEBUG", "waveRadius=" + waveRadius + " npcs=" + npcIds.length);
        for (int e : npcIds) {
            if (damagedNPCs.contains(e)) continue;

            Transform t = ecsWorld.get(e, Transform.class);
            if (t == null) continue;
            double dx = t.centerX() - cx;
            double dy = t.centerY() - cy;
            double dist = Math.sqrt(dx * dx + dy * dy);
            Logger("DEBUG", "CHECK: npcEntity=" + e + " dist=" + dist + " cx=" + cx + " cy=" + cy + " nx=" + t.x + " ny=" + t.y);
            if (dist <= waveRadius + 20 && dist >= waveRadius - 40) {
                Logger("DEBUG", "HIT: npcEntity=" + e + " dist=" + dist + " waveRadius=" + waveRadius);
                double distanceRatio = dist / WARNING_CIRCLE_RADIUS;
                double actualDamage = baseDamage * (1.0 - distanceRatio * 0.5);
                actualDamage = Math.max(1, actualDamage);

                Health h = ecsWorld.get(e, Health.class);
                if (h != null) {
                    h.takeDamage(actualDamage);
                }
                damagedNPCs.add(e);
            }
        }
    }

    private void stopCharge() {
        Charge c = charge();
        if (c != null) {
            c.stop();
        }
    }
}
