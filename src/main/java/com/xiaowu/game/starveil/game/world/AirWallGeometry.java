package com.xiaowu.game.starveil.game.world;

/**
 * 空气墙矩形的归一化。
 *
 * <p>地图 JSON 用「两个对角点」描述空气墙（{@code x,y} 与 {@code x_to,y_to}），
 * 书写顺序本来不该有约束。但历史上 {@code WorldMap} 直接用
 * {@code Math.max(1, x_to - x)} 算宽高 —— 反向书写时这个表达式会静默退化成
 * <b>1 像素</b>，空气墙形同虚设，而且不报任何错。
 *
 * <p>{@code wu-home.json} 就踩过这个坑：
 * <pre>
 * { "x": 599, "y": 810, "x_to": 834, "y_to": 736 }
 * </pre>
 * {@code y_to - y = -74} → {@code Math.max(1, -74) = 1}，这条「斜墙」实际上只是
 * y=810 处的一条 1 像素横线。
 *
 * <p>这里把归一化抽出来，既修掉问题，也让它可以被单元测试覆盖 ——
 * 这类静默失效的 bug 最容易在重构时重新长回来。
 */
public final class AirWallGeometry {

    /** 归一化后的矩形。宽高保证 &ge; 1。 */
    public record Rect(int x, int y, int width, int height) {
    }

    /**
     * 由<strong>任意顺序</strong>的两个对角点得到归一化矩形。
     *
     * @param x  第一个点的 x
     * @param y  第一个点的 y
     * @param xTo 第二个点的 x
     * @param yTo 第二个点的 y
     */
    public static Rect of(int x, int y, int xTo, int yTo) {
        return new Rect(
                Math.min(x, xTo),
                Math.min(y, yTo),
                Math.max(1, Math.abs(xTo - x)),
                Math.max(1, Math.abs(yTo - y)));
    }

    private AirWallGeometry() {
    }
}
