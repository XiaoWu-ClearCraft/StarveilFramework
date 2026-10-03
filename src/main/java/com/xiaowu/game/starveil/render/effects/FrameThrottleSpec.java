package com.xiaowu.game.starveil.render.effects;

/**
 * 「降帧保持」参数解析。
 *
 * <p>遮罩层只改变自己显示的内容——它把下层画面捕获下来并保持一小段时间，
 * 于是观众看到的刷新率被压到目标帧率。<b>只允许降低，不允许提升</b>。
 *
 * <p>支持的写法：
 * <ul>
 *   <li>{@code "50%"} —— 原帧率的百分之多少</li>
 *   <li>{@code "30"} / {@code "10"} —— 一个具体帧率</li>
 *   <li>空 / {@code "off"} / {@code "none"} —— 不启用（调用方应输出 WARNING 并忽略）</li>
 * </ul>
 *
 * <p>解析结果与「实测帧率」结合后得到最终生效帧率，见 {@link #resolve(double)}。
 */
public final class FrameThrottleSpec {

    /** 低于该帧率就没有意义了。 */
    public static final double MIN_FPS = 1.0;

    private final boolean enabled;
    private final boolean percent;
    private final double value;
    private final String problem;

    private FrameThrottleSpec(boolean enabled, boolean percent, double value, String problem) {
        this.enabled = enabled;
        this.percent = percent;
        this.value = value;
        this.problem = problem;
    }

    /** 未启用/非法时携带的原因，正常时为 null。 */
    public String problem() {
        return problem;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isPercent() {
        return percent;
    }

    /** 解析出的原始数值：百分比时为 50（代表 50%），绝对帧率时为 30。 */
    public double rawValue() {
        return value;
    }

    /**
     * 解析参数。
     *
     * @param spec 形如 {@code "50%"} / {@code "30"} 的字符串，可为 null
     * @return 解析结果；未启用时 {@link #isEnabled()} 为 false 且 {@link #problem()} 说明原因
     */
    public static FrameThrottleSpec parse(String spec) {
        if (spec == null || spec.isBlank()) {
            return disabled("未提供帧率参数");
        }

        String text = spec.trim().toLowerCase();
        if (text.equals("off") || text.equals("none") || text.equals("false") || text.equals("disable")) {
            return disabled("参数显式关闭了降帧");
        }

        boolean percent = text.endsWith("%");
        String number = percent ? text.substring(0, text.length() - 1).trim() : text;
        if (number.isEmpty()) {
            return disabled("参数缺少数值: " + spec);
        }

        double parsed;
        try {
            parsed = Double.parseDouble(number);
        } catch (NumberFormatException e) {
            return disabled("无法解析帧率参数: " + spec);
        }

        if (Double.isNaN(parsed) || Double.isInfinite(parsed) || parsed <= 0) {
            return disabled("帧率必须为正数: " + spec);
        }
        if (percent && parsed >= 100) {
            // 100% 等于不提速也不降速，超过 100% 属于「提升帧数」，一律拒绝
            return disabled("百分比必须小于 100%: " + spec);
        }
        if (!percent && parsed < MIN_FPS) {
            return disabled("具体帧率不能低于 " + (int) MIN_FPS + ": " + spec);
        }

        return new FrameThrottleSpec(true, percent, parsed, null);
    }

    /**
     * 结合游戏实测帧率算出最终生效帧率。
     *
     * <p>若目标帧率不低于实测帧率，说明该请求会「提升帧数」——返回 -1 表示拒绝。
     *
     * @param measuredFps 游戏当前实测帧率
     * @return 生效帧率（&gt; 0），或 -1 表示应忽略该请求
     */
    public double resolve(double measuredFps) {
        if (!enabled) {
            return -1;
        }
        double measured = measuredFps > 0 ? measuredFps : 60.0;
        double target = percent ? measured * value / 100.0 : value;
        if (target >= measured) {
            return -1;
        }
        return Math.max(MIN_FPS, target);
    }

    private static FrameThrottleSpec disabled(String problem) {
        return new FrameThrottleSpec(false, false, 0, problem);
    }

    @Override
    public String toString() {
        if (!enabled) {
            return "FrameThrottleSpec(disabled: " + problem + ")";
        }
        return percent ? (int) value + "%" : ((int) value) + "fps";
    }
}
