package com.xiaowu.game.starveil.render;

import javafx.animation.AnimationTimer;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.util.Collections;
import java.util.List;

/**
 * 动画贴图视图 — 播放 {@link AnimationSheet} 的帧动画。
 *
 * <p>若 meta 声明了 {@code animation}（帧数 &gt; 1 且帧率 &gt; 0），则自动循环播放；
 * 否则退化为静态贴图（仅显示第一帧）。继承 {@link ImageView}，可无缝替换普通 ImageView。
 */
public class AnimationSheetView extends ImageView {

    private AnimationSheet sheet;
    private List<Image> frames = Collections.emptyList();
    private int frameIndex = 0;
    private double frameRate = 0;
    private long lastTick = 0;
    private boolean playing = false;

    private final AnimationTimer timer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            if (!playing || frames.size() < 2 || frameRate <= 0) {
                return;
            }
            if (lastTick == 0) {
                lastTick = now;
                return;
            }
            double frameNs = 1_000_000_000.0 / frameRate;
            long elapsed = now - lastTick;
            if (elapsed >= frameNs) {
                long steps = (long) (elapsed / frameNs);
                frameIndex = (int) ((frameIndex + steps) % frames.size());
                lastTick = now;
                setImage(frames.get(frameIndex));
            }
        }
    };

    public AnimationSheetView() {
        super();
    }

    /**
     * 绑定动画表并开始播放（静态表则显示第一帧）。
     */
    public void playSheet(AnimationSheet sheet) {
        this.sheet = sheet;
        this.frameIndex = 0;
        this.lastTick = 0;
        if (sheet == null) {
            this.frames = Collections.emptyList();
            this.frameRate = 0;
            stopAnimation();
            return;
        }
        this.frames = sheet.getAllFrames();
        this.frameRate = sheet.getFrameRate();
        if (!frames.isEmpty()) {
            setImage(frames.get(0));
        }
        if (frames.size() > 1 && frameRate > 0) {
            playing = true;
            timer.start();
        } else {
            playing = false;
            timer.stop();
        }
    }

    public void stopAnimation() {
        playing = false;
        timer.stop();
    }

    public boolean isPlaying() {
        return playing;
    }

    public AnimationSheet getAnimationSheet() {
        return sheet;
    }

    public int getCurrentFrameIndex() {
        return frameIndex;
    }

    public List<Image> getFrames() {
        return frames;
    }
}
