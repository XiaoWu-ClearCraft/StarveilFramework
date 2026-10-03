package com.xiaowu.game.starveil.game.story;

/**
 * 「必须持续按住足够长时间才算通过」的进度记录器。
 *
 * <p>为什么需要它：新手教程里「按一下就算学会移动」是没意义的 ——
 * 玩家可能只是手滑碰了一下键。教程应当要求玩家<b>真的按住并持续一段时间</b>，
 * 才算掌握了这个操作。
 *
 * <p>本类刻意做成零依赖的纯逻辑（不碰 JavaFX、不碰输入系统），
 * 这样它可以直接被单元测试覆盖 —— 时长累加这类「差一点就对不上」的逻辑
 * 最容易在改动时悄悄坏掉。
 *
 * <p>典型用法：
 * <pre>
 * HoldProgressTracker t = new HoldProgressTracker(4, 1.5);   // 4 个方向，各 1.5 秒
 * // 每帧：
 * t.tick(0, keyUpPressed, deltaTime);
 * if (t.allDone()) { ... }
 * </pre>
 */
public final class HoldProgressTracker {

    private final double requiredSeconds;
    private final double[] held;
    private final boolean[] done;

    /**
     * @param slots           槽位数量（例如方向数）
     * @param requiredSeconds 每个槽位需要累计的秒数，必须 &gt; 0
     */
    public HoldProgressTracker(int slots, double requiredSeconds) {
        if (slots <= 0) {
            throw new IllegalArgumentException("slots 必须 > 0，实际 " + slots);
        }
        if (!(requiredSeconds > 0)) {
            throw new IllegalArgumentException("requiredSeconds 必须 > 0，实际 " + requiredSeconds);
        }
        this.requiredSeconds = requiredSeconds;
        this.held = new double[slots];
        this.done = new boolean[slots];
    }

    /**
     * 累加某个槽位的按住时长。
     *
     * <p>已经达标的槽位不再累加（避免进度超过 100%），
     * 未按住或 deltaTime 非正时也不累加。
     *
     * @param slot      槽位下标；越界会被忽略而不是抛异常（便于防御性调用）
     * @param active    本帧是否处于「按住且有效」状态
     * @param deltaTime 本帧时长（秒）
     */
    public void tick(int slot, boolean active, double deltaTime) {
        if (slot < 0 || slot >= held.length) {
            return;
        }
        if (done[slot] || !active || deltaTime <= 0) {
            return;
        }
        held[slot] += deltaTime;
        if (held[slot] >= requiredSeconds) {
            held[slot] = requiredSeconds;
            done[slot] = true;
        }
    }

    /** 该槽位是否已达标。 */
    public boolean isDone(int slot) {
        return slot >= 0 && slot < done.length && done[slot];
    }

    /** 该槽位已累计的秒数（达标后固定为 requiredSeconds）。 */
    public double heldSeconds(int slot) {
        return slot >= 0 && slot < held.length ? held[slot] : 0;
    }

    /** 该槽位的完成度 0.0~1.0，用于给玩家显示单格进度条。 */
    public double progress(int slot) {
        if (slot < 0 || slot >= held.length) {
            return 0;
        }
        return Math.min(1.0, held[slot] / requiredSeconds);
    }

    /**
     * 所有槽位的<b>合计</b>完成度 0.0~1.0，用于「只显示一根总进度条」。
     *
     * <p>每格最多贡献 {@code 1/slots}（因为单格时长在 {@link #tick} 里已被钳到
     * requiredSeconds），所以<b>必须所有槽位都练满才会到 100%</b> ——
     * 只按一个方向猛练是刷不满的。
     */
    public double totalProgress() {
        double sum = 0;
        for (double h : held) {
            sum += h;
        }
        return Math.min(1.0, sum / (requiredSeconds * held.length));
    }

    /** 合计已累计的秒数。 */
    public double totalHeldSeconds() {
        double sum = 0;
        for (double h : held) {
            sum += h;
        }
        return sum;
    }

    /** 合计需要的秒数（slots × requiredSeconds）。 */
    public double totalRequiredSeconds() {
        return requiredSeconds * held.length;
    }

    /** 是否所有槽位都已达标。 */
    public boolean allDone() {
        for (boolean d : done) {
            if (!d) {
                return false;
            }
        }
        return true;
    }

    /** 已达标槽位数量。 */
    public int doneCount() {
        int n = 0;
        for (boolean d : done) {
            if (d) {
                n++;
            }
        }
        return n;
    }

    public int slots() {
        return held.length;
    }

    public double requiredSeconds() {
        return requiredSeconds;
    }

    /** 清空全部进度。 */
    public void reset() {
        java.util.Arrays.fill(held, 0);
        java.util.Arrays.fill(done, false);
    }
}
