package com.xiaowu.game.starveil.game.ecs.sys;

import com.xiaowu.game.starveil.game.ecs.EcsSystem;
import com.xiaowu.game.starveil.game.ecs.World;
import com.xiaowu.game.starveil.game.ecs.comp.Health;
import com.xiaowu.game.starveil.game.ecs.comp.Npc;
import com.xiaowu.game.starveil.game.ecs.comp.Sprite;
import com.xiaowu.game.starveil.game.ecs.comp.Transform;
import com.xiaowu.game.starveil.game.world.WorldMap;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.animation.Transition;
import javafx.animation.TranslateTransition;
import javafx.animation.Animation;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

/**
 * NPC 死亡系统：Health.dead 时播放“暗影湮灭”特效，结束后销毁实体并记录 deadNpcIds。
 */
public final class NpcDeathSystem implements EcsSystem {
    private static final Random RANDOM = new Random();

    @Override
    public void update(World world, double deltaTime) {
        WorldMap map = world.getResource(WorldMap.class);
        if (map == null) {
            return;
        }
        for (int e : world.view(Npc.class, Health.class, Sprite.class, Transform.class)) {
            Health h = world.get(e, Health.class);
            if (h == null || !h.dead || h.deathAnimationStarted) {
                continue;
            }
            h.deathAnimationStarted = true;
            Npc npc = world.get(e, Npc.class);
            Sprite spr = world.get(e, Sprite.class);
            Transform t = world.get(e, Transform.class);
            startDeathAnimation(world, map, e, npc, spr, t);
        }
    }

    private static void startDeathAnimation(World world, WorldMap map, int entity,
                                            Npc npc, Sprite spr, Transform t) {
        Node view = spr.view;
        double cx = t.centerX();
        double cy = t.centerY();
        double x = t.x;
        double y = t.y;
        double w = t.width;
        double h = t.height;
        javafx.scene.layout.Pane worldPane = map.getWorld();

        Rectangle flashRed = new Rectangle(x, y, w, h);
        flashRed.setFill(Color.rgb(255, 50, 50, 0.7));
        flashRed.setMouseTransparent(true);

        Rectangle darkOverlay = new Rectangle(x, y, w, h);
        darkOverlay.setFill(Color.rgb(0, 0, 0, 0));
        darkOverlay.setMouseTransparent(true);

        Circle darkRing = new Circle(cx, cy, 5, Color.TRANSPARENT);
        darkRing.setStroke(Color.rgb(80, 0, 20, 0.8));
        darkRing.setStrokeWidth(3);
        darkRing.setMouseTransparent(true);

        int particleCount = 8;
        List<Circle> particles = new ArrayList<>();
        List<TranslateTransition> particleMoves = new ArrayList<>();
        List<FadeTransition> particleFades = new ArrayList<>();

        for (int i = 0; i < particleCount; i++) {
            Circle p = new Circle(cx, cy, 2.5 + RANDOM.nextDouble() * 2.5);
            p.setFill(Color.rgb(100 + RANDOM.nextInt(50), 0, 20 + RANDOM.nextInt(30), 0.8));
            p.setMouseTransparent(true);
            worldPane.getChildren().add(p);
            particles.add(p);

            double angle = RANDOM.nextDouble() * 2 * Math.PI;
            double distP = 30 + RANDOM.nextDouble() * 60;
            TranslateTransition tt = new TranslateTransition(Duration.seconds(0.5), p);
            tt.setByX(Math.cos(angle) * distP);
            tt.setByY(Math.sin(angle) * distP);
            tt.setInterpolator(Interpolator.EASE_OUT);
            particleMoves.add(tt);

            FadeTransition ft = new FadeTransition(Duration.seconds(0.45), p);
            ft.setDelay(Duration.seconds(0.05));
            ft.setFromValue(1);
            ft.setToValue(0);
            particleFades.add(ft);
        }

        worldPane.getChildren().addAll(flashRed, darkOverlay, darkRing);

        FadeTransition flashAnim = new FadeTransition(Duration.seconds(0.12), flashRed);
        flashAnim.setFromValue(1);
        flashAnim.setToValue(0);

        Timeline consumeAnim = new Timeline(
                new KeyFrame(Duration.seconds(0.12),
                        new KeyValue(darkOverlay.opacityProperty(), 0.25)),
                new KeyFrame(Duration.seconds(0.35),
                        new KeyValue(darkOverlay.opacityProperty(), 0.75),
                        new KeyValue(view.opacityProperty(), 0.4),
                        new KeyValue(view.scaleXProperty(), 0.4),
                        new KeyValue(view.scaleYProperty(), 0.4)),
                new KeyFrame(Duration.seconds(0.5),
                        new KeyValue(darkOverlay.opacityProperty(), 0.0),
                        new KeyValue(view.opacityProperty(), 0.0),
                        new KeyValue(view.scaleXProperty(), 0.0),
                        new KeyValue(view.scaleYProperty(), 0.0))
        );

        Timeline ringAnim = new Timeline(
                new KeyFrame(Duration.seconds(0.5),
                        new KeyValue(darkRing.radiusProperty(), 110),
                        new KeyValue(darkRing.strokeWidthProperty(), 0.5),
                        new KeyValue(darkRing.opacityProperty(), 0.0))
        );

        ParallelTransition particleAnim = new ParallelTransition(
                Stream.concat(particleMoves.stream(), particleFades.stream()).toArray(Transition[]::new)
        );

        ParallelTransition deathAnim = new ParallelTransition(flashAnim, consumeAnim, ringAnim, particleAnim);
        deathAnim.setOnFinished(e -> {
            map.getDeathAnimations().remove(deathAnim);
            worldPane.getChildren().removeAll(flashRed, darkOverlay, darkRing);
            worldPane.getChildren().removeAll(particles);
            worldPane.getChildren().remove(view);
            map.markNpcDead(npc.id);
            world.destroy(entity);
        });
        map.getDeathAnimations().add(deathAnim);
        if (map.isPaused()) {
            deathAnim.pause();
        }
        deathAnim.play();
    }
}
