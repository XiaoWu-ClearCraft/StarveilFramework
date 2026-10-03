package com.xiaowu.game.starveil.platform.console;

/**
 * 控制台进度条类
 * 平台无关，通过 ConsoleProvider 接口输出
 * 支持自定义边框、内容、速度控制（缓动函数）、进度显示等
 */
public class ProgressBar {
    private final ConsoleProvider console;

    // 进度条配置
    private String leftBorder = "[";
    private String rightBorder = "]";
    private String fillChar = "#";
    private String emptyChar = " ";
    private int width = 50;
    private boolean showProgress = true;
    private ProgressPosition progressPosition = ProgressPosition.MIDDLE;
    private EasingFunction easingFunction = EasingFunction.LINEAR;
    private int animationDelay = 50;

    // 当前状态
    private double currentProgress = 0.0;
    private double targetProgress = 0.0;
    private boolean animated = false;
    private Thread animationThread = null;

    /**
     * 进度显示位置
     */
    public enum ProgressPosition {
        LEFT,
        MIDDLE,
        RIGHT
    }

    /**
     * 缓动函数类型
     */
    public enum EasingFunction {
        LINEAR,
        EASE_IN,
        EASE_OUT,
        EASE_IN_OUT,
        EASE_OUT_BOUNCE,
        EASE_ELASTIC
    }

    /**
     * 构造函数
     * @param console ConsoleProvider 实例
     */
    public ProgressBar(ConsoleProvider console) {
        this.console = console;
    }

    public ProgressBar setBorders(String left, String right) {
        this.leftBorder = left;
        this.rightBorder = right;
        return this;
    }

    public ProgressBar setFillChar(String fill) {
        this.fillChar = fill;
        return this;
    }

    public ProgressBar setEmptyChar(String empty) {
        this.emptyChar = empty;
        return this;
    }

    public ProgressBar setWidth(int width) {
        this.width = Math.max(10, width);
        return this;
    }

    public ProgressBar setShowProgress(boolean show) {
        this.showProgress = show;
        return this;
    }

    public ProgressBar setProgressPosition(ProgressPosition position) {
        this.progressPosition = position;
        return this;
    }

    public ProgressBar setEasingFunction(EasingFunction function) {
        this.easingFunction = function;
        return this;
    }

    public ProgressBar setAnimationDelay(int delay) {
        this.animationDelay = Math.max(10, delay);
        return this;
    }

    private double applyEasing(double progress) {
        return switch (easingFunction) {
            case EASE_IN -> progress * progress;
            case EASE_OUT -> 1 - (1 - progress) * (1 - progress);
            case EASE_IN_OUT -> progress < 0.5
                ? 2 * progress * progress
                : 1 - Math.pow(-2 * progress + 2, 2) / 2;
            case EASE_OUT_BOUNCE -> {
                double p = progress;
                if (p < 1 / 2.75) {
                    yield 7.5625 * p * p;
                } else if (p < 2 / 2.75) {
                    yield 7.5625 * (p -= 1.5 / 2.75) * p + 0.75;
                } else if (p < 2.5 / 2.75) {
                    yield 7.5625 * (p -= 2.25 / 2.75) * p + 0.9375;
                } else {
                    yield 7.5625 * (p -= 2.625 / 2.75) * p + 0.984375;
                }
            }
            case EASE_ELASTIC -> {
                if (progress == 0 || progress == 1) yield progress;
                yield Math.pow(2, -10 * progress) * Math.sin((progress * 10 - 0.75) * (2 * Math.PI) / 3) + 1;
            }
            case LINEAR -> progress;
        };
    }

    private String generateProgressBar(double progress) {
        double easedProgress = applyEasing(Math.max(0, Math.min(1, progress)));

        String progressText = "";
        int progressTextLength = 0;
        if (showProgress) {
            int percentage = (int) (easedProgress * 100);
            progressText = percentage + "%/100%";
            progressTextLength = progressText.length();
        }

        StringBuilder sb = new StringBuilder();

        switch (progressPosition) {
            case LEFT -> {
                int leftWidth = width;
                int leftFill = (int) (leftWidth * easedProgress);
                int leftEmpty = leftWidth - leftFill;
                if (showProgress) sb.append(progressText).append("  ");
                sb.append(leftBorder);
                sb.append(fillChar.repeat(leftFill));
                sb.append(emptyChar.repeat(leftEmpty));
                sb.append(rightBorder);
            }
            case MIDDLE -> {
                int middleTotalWidth = width - progressTextLength - 2;
                int halfWidth = middleTotalWidth / 2;
                int middleLeftFill, middleRightFill, middleLeftEmpty, middleRightEmpty;

                if (easedProgress <= 0.5) {
                    middleLeftFill = (int) (middleTotalWidth * easedProgress);
                    middleRightFill = 0;
                    middleLeftEmpty = halfWidth - middleLeftFill;
                    middleRightEmpty = halfWidth;
                } else {
                    middleLeftFill = halfWidth;
                    middleRightFill = (int) ((easedProgress - 0.5) * middleTotalWidth);
                    middleLeftEmpty = 0;
                    middleRightEmpty = halfWidth - middleRightFill;
                }

                middleLeftFill = Math.max(0, middleLeftFill);
                middleRightFill = Math.max(0, middleRightFill);
                middleLeftEmpty = Math.max(0, middleLeftEmpty);
                middleRightEmpty = Math.max(0, middleRightEmpty);

                sb.append(leftBorder);
                sb.append(fillChar.repeat(middleLeftFill));
                sb.append(emptyChar.repeat(middleLeftEmpty));
                if (showProgress) sb.append(" ").append(progressText).append(" ");
                sb.append(fillChar.repeat(middleRightFill));
                sb.append(emptyChar.repeat(middleRightEmpty));
                sb.append(rightBorder);
            }
            case RIGHT -> {
                int rightWidth = width;
                int rightFill = (int) (rightWidth * easedProgress);
                int rightEmpty = rightWidth - rightFill;
                sb.append(leftBorder);
                sb.append(fillChar.repeat(rightFill));
                sb.append(emptyChar.repeat(rightEmpty));
                sb.append(rightBorder);
                if (showProgress) sb.append("  ").append(progressText);
            }
        }

        return sb.toString();
    }

    public void show(double progress) {
        String progressBar = generateProgressBar(progress);
        console.print("\r" + progressBar);
    }

    public void show(double progress, String message) {
        String progressBar = generateProgressBar(progress);
        console.print("\r" + progressBar + " " + message);
    }

    public void animate(double startProgress, double endProgress, long duration) {
        stopAnimation();

        animated = true;
        currentProgress = startProgress;
        targetProgress = endProgress;

        animationThread = new Thread(() -> {
            long totalSteps = duration / animationDelay;

            for (int step = 0; step <= totalSteps && animated; step++) {
                double progress = startProgress + (endProgress - startProgress) * ((double) step / totalSteps);
                currentProgress = progress;

                String progressBar = generateProgressBar(progress);
                console.print("\r" + progressBar);

                try {
                    Thread.sleep(animationDelay);
                } catch (InterruptedException e) {
                    break;
                }
            }

            if (animated) {
                String progressBar = generateProgressBar(endProgress);
                console.print("\r" + progressBar);
            }
        });

        animationThread.start();
    }

    public void animate(double startProgress, double endProgress, long duration, String message) {
        stopAnimation();

        animated = true;
        currentProgress = startProgress;
        targetProgress = endProgress;

        animationThread = new Thread(() -> {
            long totalSteps = duration / animationDelay;

            for (int step = 0; step <= totalSteps && animated; step++) {
                double progress = startProgress + (endProgress - startProgress) * ((double) step / totalSteps);
                currentProgress = progress;

                String progressBar = generateProgressBar(progress);
                console.print("\r" + progressBar + " " + message);

                try {
                    Thread.sleep(animationDelay);
                } catch (InterruptedException e) {
                    break;
                }
            }

            if (animated) {
                String progressBar = generateProgressBar(endProgress);
                console.print("\r" + progressBar + " " + message);
            }
        });

        animationThread.start();
    }

    public void stopAnimation() {
        animated = false;
        if (animationThread != null && animationThread.isAlive()) {
            animationThread.interrupt();
            try {
                animationThread.join(100);
            } catch (InterruptedException e) {
                // 忽略
            }
        }
    }

    public void waitForCompletion() {
        if (animationThread != null && animationThread.isAlive()) {
            try {
                animationThread.join();
            } catch (InterruptedException e) {
                // 忽略
            }
        }
    }

    public double getCurrentProgress() {
        return currentProgress;
    }

    public void clear() {
        console.clearLine();
    }

    public static Builder builder(ConsoleProvider console) {
        return new Builder(console);
    }

    /**
     * 进度条构建器
     */
    public static class Builder {
        private final ProgressBar progressBar;

        private Builder(ConsoleProvider console) {
            this.progressBar = new ProgressBar(console);
        }

        public Builder borders(String left, String right) { progressBar.setBorders(left, right); return this; }
        public Builder fillChar(String fill) { progressBar.setFillChar(fill); return this; }
        public Builder emptyChar(String empty) { progressBar.setEmptyChar(empty); return this; }
        public Builder width(int width) { progressBar.setWidth(width); return this; }
        public Builder showProgress(boolean show) { progressBar.setShowProgress(show); return this; }
        public Builder progressPosition(ProgressPosition position) { progressBar.setProgressPosition(position); return this; }
        public Builder easingFunction(EasingFunction function) { progressBar.setEasingFunction(function); return this; }
        public Builder animationDelay(int delay) { progressBar.setAnimationDelay(delay); return this; }
        public ProgressBar build() { return progressBar; }
    }
}
