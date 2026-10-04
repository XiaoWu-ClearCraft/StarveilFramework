package com.xiaowu.game.starveil.game.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.xiaowu.game.starveil.render.TextureNodeFactory;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * 空气墙 —— 一种<b>阻挡形状</b>，与它的贴图无关。
 *
 * <h2>为什么不是矩形</h2>
 * 原本空气墙只能是轴对齐矩形（地图 JSON 里的 {@code x/y/x_to/y_to}）。
 * 做平台游戏时这立刻不够用：斜坡、三角台阶、洞穴、六边形平台都不是矩形，
 * 而「用一堆小矩形拼出斜坡」既难写又会让碰撞在接缝处抖。
 *
 * <p>所以碰撞形状改成<b>任意多边形</b>，矩形保留为它的特例 ——
 * 老地图一个字都不用改。
 *
 * <h2>JSON 两种写法</h2>
 * <pre>
 * // 1) 矩形（老写法，仍然支持）
 * { "x": 0, "y": 860, "x_to": 450, "y_to": 884 }
 *
 * // 2) 多边形（任意顶点，凸凹都行）—— 斜坡、台阶、八边形台子都用它
 * { "polygon": [ [0,860], [200,700], [400,860] ] }
 *
 * // 3) 任意一种写法都可以加 oneWay —— 变成单向平台（从下面能跳上去，上面按 S 能穿下来）
 * { "x": 100, "y": 600, "x_to": 400, "y_to": 620, "oneWay": true }
 * </pre>
 *
 * <p>任何形状都可以带 {@code "texture": "starveil:textures/tiles/x.png"} 与
 * {@code "textureSize": 50}，把贴图平铺在形状内部（用裁剪，不会溢出到形状外）。
 * <b>贴图纯视觉，不影响碰撞</b> —— 没有贴图时形状是隐形的，
 * 这正是「空气墙」这个名字的由来。
 *
 * <p>加 {@code "oneWay": true} 则变成<b>单向平台</b>：只有从上面落下来才接得住，
 * 从下面可以跳穿上去、从侧面可以走过去，站在上面按「下」还会穿下去。
 * 详见 {@link OneWay}。
 *
 * <h2>为什么没有圆形</h2>
 * 圆看着好用，实际做地形时基本用不上：地面、台阶、斜坡、洞穴口都是<b>有边的</b>形状，
 * 而圆既拼不出边，判定又比多边形多一套（最近点距离），还让「站立面」这个概念
 * 对一半形状失效（圆没有水平的顶面）。用多边形画一个近似的圆只要多写几个顶点，
 * 却能把「只有一种形状」这条简化一路带到碰撞、贴图、调试显示和单向平台里。
 */
public sealed interface AirWall {

    /**
     * 遮挡盒（玩家/实体的轴对齐包围盒）是否与本形状相交。
     *
     * <p>这是<b>不带方向</b>的纯空间判据，双向都挡 —— 旧代码与
     * 「不关心方向」的调用（NPC、剧情脚本、边界检查）继续用它。
     * 关于单向平台的判据见 {@link #blocksFall}。
     *
     * @param x 包围盒左上角 x
     * @param y 包围盒左上角 y
     * @param w 宽
     * @param h 高
     */
    boolean intersects(double x, double y, double w, double h);

    /**
     * <b>下落</b>时的阻挡判定 —— 重力与跳跃该用的就是它。
     *
     * <p>实心形状等同于 {@link #intersects}；单向平台多一个前提：
     * 「开始下落之前脚底就已经在台面之上」。
     *
     * <p>为什么必须传<b>下落前</b>的脚底高度、而不是只看候选位置：
     * 快速下落时一帧要走十几个像素，候选位置早就在台面内部，
     * 光看候选位置会把「从下面往上穿」和「从上面落下来」混为一谈。
     * 起点信息是这一整步的常量，候选位置只是它移动的终点，
     * 两者一起才构成「从上面落到了台面上」这件事。
     *
     * @param startFeetY 本次下落<b>开始前</b>的脚底高度（即 {@code y + height}）
     * @param x 候选位置的包围盒左上角 x
     * @param y 候选位置的包围盒左上角 y
     * @param w 宽
     * @param h 高
     */
    boolean blocksFall(double startFeetY, double x, double y, double w, double h);

    /**
     * 站立面高度（脚底所在的 y）：实体落在这个高度上就算站住了。
     *
     * <p>取<b>最高顶点</b>的 y（对矩形台面就是台面高度；斜面没有唯一的
     * 「台面」，所以斜的单向平台不受支持）。目前只有单向平台会用到它 ——
     * 实心空气墙的碰撞完全由 {@link #intersects} 的几何决定，不需要这个概念。
     */
    double surfaceTop();

    /** 是否是单向平台（可从下方穿过、可按向下键落下）。 */
    boolean oneWay();

    /** 用于显示与调试的 JavaFX 节点（可能包含贴图子节点）。 */
    Node buildNode();

    /** 形状的顶点数（圆形固定返回 1，仅供调试信息使用）。 */
    int pointCount();

    // ==================== 多边形 ====================

    /**
     * 任意多边形（顶点按顺序给出，自动闭合）。
     *
     * <p>相交判定 = 「包围盒有顶点落在多边形内」<b>或</b>「多边形某条边穿过包围盒」。
     * 两条缺一不可：细长的斜坡可能整条穿过包围盒而顶点都在外面。
     */
    record Poly(double[] xs, double[] ys, String texture, double textureSize)
            implements AirWall {

        public Poly {
            if (xs == null || ys == null || xs.length != ys.length || xs.length < 3) {
                throw new IllegalArgumentException(
                        "多边形至少需要 3 个顶点，且 x/y 数量一致");
            }
        }

        /** 由 {@code [[x,y], ...]} 形式的数组构造。 */
        public static Poly fromJsonArray(JsonArray points, String texture, double textureSize) {
            if (points == null || points.size() < 3) {
                Logger("WARNING", "空气墙多边形的顶点少于 3 个，已忽略");
                return null;
            }
            double[] xs = new double[points.size()];
            double[] ys = new double[points.size()];
            for (int i = 0; i < points.size(); i++) {
                JsonElement p = points.get(i);
                if (!p.isJsonArray() || p.getAsJsonArray().size() < 2) {
                    Logger("WARNING", "空气墙多边形第 " + i + " 个顶点格式不对（应为 [x, y]），已忽略整条");
                    return null;
                }
                JsonArray pair = p.getAsJsonArray();
                try {
                    xs[i] = pair.get(0).getAsDouble();
                    ys[i] = pair.get(1).getAsDouble();
                } catch (NumberFormatException | UnsupportedOperationException e) {
                    // 坐标不是数字（例如写成 "x"）—— 报错并整条忽略，
                    // 而不是让异常冒到地图加载流程里
                    Logger("WARNING", "空气墙多边形第 " + i + " 个顶点不是数字: "
                            + pair + "，已忽略整条");
                    return null;
                }
            }
            return new Poly(xs, ys, texture, textureSize);
        }

        @Override
        public int pointCount() {
            return xs.length;
        }

        @Override
        public boolean blocksFall(double startFeetY, double x, double y, double w, double h) {
            return intersects(x, y, w, h);
        }

        @Override
        public boolean oneWay() {
            return false;
        }

        /** 顶边 y —— 也就是「站上去的脚底高度」。 */
        @Override
        public double surfaceTop() {
            double top = Double.MAX_VALUE;
            for (double val : ys) {
                top = Math.min(top, val);
            }
            return top;
        }

        @Override
        public boolean intersects(double x, double y, double w, double h) {
            double maxX = x + w;
            double maxY = y + h;

            // 1) 包围盒的任一角落在多边形内 → 相交
            if (contains(x, y) || contains(maxX, y) || contains(x, maxY) || contains(maxX, maxY)) {
                return true;
            }
            // 2) 多边形任一条边穿过包围盒 → 相交
            //    这一条不能省：细长的斜坡可能整条横穿包围盒，四个角却都在外面
            return anyEdgeCrossesBox(x, y, maxX, maxY);
        }

        /** 射线法：从点向右发射一条水平射线，数与多边形边的交点个数，奇数即在内部。 */
        boolean contains(double px, double py) {
            boolean inside = false;
            int n = xs.length;
            for (int i = 0, j = n - 1; i < n; j = i++) {
                double xi = xs[i];
                double yi = ys[i];
                double xj = xs[j];
                double yj = ys[j];
                // 只统计「跨越 py 高度」的边；半开区间避免顶点被算两次
                boolean straddles = (yi > py) != (yj > py);
                if (!straddles) {
                    continue;
                }
                double crossX = (xj - xi) * (py - yi) / (yj - yi) + xi;
                if (px < crossX) {
                    inside = !inside;
                }
            }
            return inside;
        }

        private boolean anyEdgeCrossesBox(double minX, double minY, double maxX, double maxY) {
            int n = xs.length;
            for (int i = 0, j = n - 1; i < n; j = i++) {
                if (segmentHitsBox(xs[j], ys[j], xs[i], ys[i], minX, minY, maxX, maxY)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Node buildNode() {
            double[] flat = new double[xs.length * 2];
            for (int i = 0; i < xs.length; i++) {
                flat[i * 2] = xs[i];
                flat[i * 2 + 1] = ys[i];
            }
            Polygon shape = new Polygon(flat);
            shape.setMouseTransparent(true);
            // 空气墙默认是隐形的（这正是名字的由来）；要看见它请按 F3，
            // 由 WorldMap.showAirWalls 统一样式（实心=青、单向=金）。
            shape.setFill(Color.TRANSPARENT);
            shape.setStroke(null);

            // 贴图作为形状的子节点，并用形状裁剪 —— 这样贴图不会溢出到多边形外，
            // 而形状本身仍然透明（空气墙是隐形的，只是视觉上铺了层皮）
            Node visual = buildTiledVisual(texture, textureSize, shape);
            if (visual == null) {
                return shape;
            }
            return buildWrapper(shape, visual);
        }

        /** 把贴图按 {@code textureSize} 铺满形状，并用形状裁剪。 */
        static Node buildTiledVisual(String texture, double textureSize, javafx.scene.shape.Shape clip) {
            if (texture == null || texture.isBlank()) {
                return null;
            }
            Image img = TextureNodeFactory.loadImage(texture);
            if (img == null) {
                Logger("WARNING", "空气墙贴图加载失败: " + texture);
                return null;
            }
            double tile = textureSize > 0 ? textureSize : Math.max(1, img.getWidth());

            javafx.geometry.Bounds b = clip.getBoundsInLocal();
            javafx.scene.layout.Pane holder = new javafx.scene.layout.Pane();
            holder.setMouseTransparent(true);
            holder.setLayoutX(b.getMinX());
            holder.setLayoutY(b.getMinY());
            holder.setPrefSize(b.getWidth(), b.getHeight());
            holder.setMinSize(b.getWidth(), b.getHeight());
            holder.setMaxSize(b.getWidth(), b.getHeight());

            // 裁剪用另一份形状，坐标平移到 holder 的局部坐标系
            javafx.scene.shape.Shape clipShape = copyShape(clip);
            clipShape.setLayoutX(0);
            clipShape.setLayoutY(0);
            holder.setClip(clipShape);

            int cols = (int) Math.ceil(b.getWidth() / tile);
            int rows = (int) Math.ceil(b.getHeight() / tile);
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    ImageView iv = new ImageView(img);
                    iv.setFitWidth(tile);
                    iv.setFitHeight(tile);
                    iv.setLayoutX(c * tile);
                    iv.setLayoutY(r * tile);
                    iv.setMouseTransparent(true);
                    holder.getChildren().add(iv);
                }
            }
            return holder;
        }

        /** 复制一份形状顶点，并平移到左上角为原点的坐标系（供裁剪用）。 */
        private static javafx.scene.shape.Shape copyShape(javafx.scene.shape.Shape src) {
            javafx.geometry.Bounds b = src.getBoundsInLocal();
            Polygon poly = (Polygon) src;
            Polygon copy = new Polygon();
            for (int i = 0; i + 1 < poly.getPoints().size(); i += 2) {
                copy.getPoints().add(poly.getPoints().get(i) - b.getMinX());
                copy.getPoints().add(poly.getPoints().get(i + 1) - b.getMinY());
            }
            return copy;
        }

        /**
         * 把「碰撞形状」与「贴图」合成一个节点：形状在最底层负责裁剪，
         * 贴图作为兄弟节点被它裁剪 —— 用 {@code Group} 而不是把贴图塞进形状，
         * 因为 {@code Polygon} 并不是容器。
         */
        static Node buildWrapper(javafx.scene.shape.Shape shape, Node visual) {
            javafx.scene.Group group = new javafx.scene.Group(shape, visual);
            group.setMouseTransparent(true);
            group.setUserData(shape);
            return group;
        }
    }

    // ==================== 单向平台 ====================

    /**
     * 单向平台：把任意形状包一层「只从上面挡」的语义。
     *
     * <p>做成装饰器而不是给每个形状加字段，是因为「单向」与形状本身无关：
     * 只要形状有水平台面，单向行为就完全一样（从下方可以穿过、从上方踩得住）。
     * 包一层之后 {@link Poly} 不需要知道单向这回事。
     *
     * <p><b>为什么判定要看「下落前的脚底位置」</b>：
     * 实体上升穿过台面时，某一帧它的包围盒必然与台面相交；如果只看相交就挡，
     * 就会在穿到一半时被弹回去。所以判据必须包含「这一步是从哪边过来的」。
     *
     * <p>单向语义只对<b>水平台面</b>成立，台面高度取
     * {@link #surfaceTop()}（多边形的最高顶点）。斜着写的单向平台不支持 ——
     * 那需要按顶点法线逐个面判定，真正的平台游戏也是这么区分的。
     */
    record OneWay(AirWall delegate) implements AirWall {

        /** 脚底与台面齐平时允许的误差：落在台面上时脚底恰好等于台面高度。 */
        static final double SURFACE_EPSILON = 0.5;

        @Override
        public boolean intersects(double x, double y, double w, double h) {
            return delegate.intersects(x, y, w, h);
        }

        @Override
        public boolean blocksFall(double startFeetY, double x, double y, double w, double h) {
            // 下落前脚底必须在台面之上；从下面往上穿的实体脚底在台面之下，直接放过
            if (startFeetY > delegate.surfaceTop() + SURFACE_EPSILON) {
                return false;
            }
            return delegate.intersects(x, y, w, h);
        }

        @Override
        public double surfaceTop() {
            return delegate.surfaceTop();
        }

        @Override
        public boolean oneWay() {
            return true;
        }

        @Override
        public Node buildNode() {
            return delegate.buildNode();
        }

        @Override
        public int pointCount() {
            return delegate.pointCount();
        }
    }

    // ==================== 解析 ====================

    /**
     * 解析地图 JSON 里的一条空气墙。
     *
     * <p>两种写法按优先级：{@code polygon} &gt; {@code x/y/x_to/y_to}。
     * 都不合法时返回 {@code null}（调用方跳过并记录）。
     *
     * <p>可附加 {@code "oneWay": true} 变成单向平台（见 {@link OneWay}）。
     */
    static AirWall fromJson(JsonObject o) {
        AirWall shape = parseShape(o);
        if (shape == null) {
            return null;
        }
        return isOneWayRequested(o) ? new OneWay(shape) : shape;
    }

    private static boolean isOneWayRequested(JsonObject o) {
        if (o == null || !o.has("oneWay") || !o.get("oneWay").isJsonPrimitive()) {
            return false;
        }
        try {
            return o.get("oneWay").getAsBoolean();
        } catch (UnsupportedOperationException | IllegalStateException e) {
            Logger("WARNING", "空气墙 oneWay 需要布尔值，已忽略该字段");
            return false;
        }
    }

    private static AirWall parseShape(JsonObject o) {
        if (o == null) {
            return null;
        }
        String texture = o.has("texture") ? o.get("texture").getAsString() : null;
        double textureSize = o.has("textureSize") ? o.get("textureSize").getAsDouble() : 0;

        if (o.has("polygon") && o.get("polygon").isJsonArray()) {
            AirWall wall = Poly.fromJsonArray(o.getAsJsonArray("polygon"), texture, textureSize);
            if (wall != null) {
                return wall;
            }
            Logger("WARNING", "空气墙 polygon 不合法，已跳过这条");
            return null;
        }

        // 老写法 "circle" 已经删掉：地形都是有边的形状，圆既拼不出边又让
        // 「站立面」对一半形状失效。这里明确告警而不是静默忽略 ——
        // 否则老地图会少一块碰撞，玩起来就是「这里怎么掉下去了」。
        if (o.has("circle")) {
            Logger("WARNING", "空气墙不再支持 circle，请改用 polygon 近似（例如八边形）：已跳过这条");
            return null;
        }

        // 老写法：两个对角点 → 矩形（转成 4 顶点多边形，走同一条碰撞路径）
        int x = o.has("x") ? o.get("x").getAsInt() : 0;
        int y = o.has("y") ? o.get("y").getAsInt() : 0;
        int xTo = o.has("x_to") ? o.get("x_to").getAsInt() : x;
        int yTo = o.has("y_to") ? o.get("y_to").getAsInt() : y;

        // 归一化两个对角点：允许任意顺序书写。
        // 旧写法 Math.max(1, x_to - x) 在反向书写时会静默退化成 1px 宽的线，
        // 空气墙形同虚设（wu-home.json 的斜墙条目就踩了这个坑）。
        // 逻辑与回归测试见 AirWallGeometry。
        AirWallGeometry.Rect box = AirWallGeometry.of(x, y, xTo, yTo);
        double[] xs = {box.x(), box.x() + box.width(), box.x() + box.width(), box.x()};
        double[] ys = {box.y(), box.y(), box.y() + box.height(), box.y() + box.height()};
        return new Poly(xs, ys, texture, textureSize);
    }

    /** 一组形状中只要有一个相交就算被挡住。 */
    static boolean anyIntersects(List<AirWall> walls, double x, double y, double w, double h) {
        if (walls == null || walls.isEmpty()) {
            return false;
        }
        for (AirWall wall : walls) {
            if (wall.intersects(x, y, w, h)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 横向 / 上升移动的批量判定：单向平台<b>一律不挡</b>。
     *
     * <p>这就是「能贴着单向平台侧面走过去」以及「能从下面跳上去」——
     * 单向平台只在落下时接住你，其余方向它等于不存在。
     */
    static boolean anyBlocksSideways(List<AirWall> walls, double x, double y, double w, double h) {
        if (walls == null || walls.isEmpty()) {
            return false;
        }
        for (AirWall wall : walls) {
            if (!wall.oneWay() && wall.intersects(x, y, w, h)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 下落的批量判定（带方向的判据，重力与跳跃都走这条路）。
     *
     * @param startFeetY 本次下落开始前的脚底高度
     * @param passOneWay 是否处于「按向下键穿下去」的窗口，为 true 时完全忽略单向平台
     */
    static boolean anyBlocksFall(List<AirWall> walls, double startFeetY,
                                 double x, double y, double w, double h, boolean passOneWay) {
        if (walls == null || walls.isEmpty()) {
            return false;
        }
        for (AirWall wall : walls) {
            if (passOneWay && wall.oneWay()) {
                continue;
            }
            if (wall.blocksFall(startFeetY, x, y, w, h)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 脚下是否踩着单向平台（用于判断按「下」能不能穿下去）。
     *
     * <p>做法是在脚底往下探一小段（{@code probe} 像素）再看有没有单向平台。
     * 站立时脚底正好贴在台面上，所以这个探测框一定与台面重叠。
     */
    static boolean anyOneWayBelow(List<AirWall> walls, double x, double y, double w, double h,
                                  double probe) {
        if (walls == null || walls.isEmpty()) {
            return false;
        }
        for (AirWall wall : walls) {
            if (wall.oneWay() && wall.intersects(x, y + h, w, probe)) {
                return true;
            }
        }
        return false;
    }

    /** 线段与轴对齐矩形是否相交（含「线段完全在内部」与「穿过」两种情形）。 */
    static boolean segmentHitsBox(double x1, double y1, double x2, double y2,
                                  double minX, double minY, double maxX, double maxY) {
        // 先在包围盒外做一次廉价排除
        double segMinX = Math.min(x1, x2);
        double segMaxX = Math.max(x1, x2);
        double segMinY = Math.min(y1, y2);
        double segMaxY = Math.max(y1, y2);
        if (segMaxX < minX || segMinX > maxX || segMaxY < minY || segMinY > maxY) {
            return false;
        }
        // 端点落在盒内
        if (pointInBox(x1, y1, minX, minY, maxX, maxY)
                || pointInBox(x2, y2, minX, minY, maxX, maxY)) {
            return true;
        }
        // 与四条边逐一判定
        return segmentsIntersect(x1, y1, x2, y2, minX, minY, maxX, minY)      // 上
                || segmentsIntersect(x1, y1, x2, y2, maxX, minY, maxX, maxY)  // 右
                || segmentsIntersect(x1, y1, x2, y2, minX, maxY, maxX, maxY)  // 下
                || segmentsIntersect(x1, y1, x2, y2, minX, minY, minX, maxY); // 左
    }

    private static boolean pointInBox(double px, double py,
                                      double minX, double minY, double maxX, double maxY) {
        return px >= minX && px <= maxX && py >= minY && py <= maxY;
    }

    /** 标准线段相交判定（含共线重叠）。 */
    private static boolean segmentsIntersect(double ax, double ay, double bx, double by,
                                             double cx, double cy, double dx, double dy) {
        double d1 = cross(cx, cy, dx, dy, ax, ay);
        double d2 = cross(cx, cy, dx, dy, bx, by);
        double d3 = cross(ax, ay, bx, by, cx, cy);
        double d4 = cross(ax, ay, bx, by, dx, dy);

        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0))
                && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) {
            return true;
        }
        // 共线且有重叠
        if (d1 == 0 && onSegment(cx, cy, dx, dy, ax, ay)) return true;
        if (d2 == 0 && onSegment(cx, cy, dx, dy, bx, by)) return true;
        if (d3 == 0 && onSegment(ax, ay, bx, by, cx, cy)) return true;
        if (d4 == 0 && onSegment(ax, ay, bx, by, dx, dy)) return true;
        return false;
    }

    private static double cross(double ax, double ay, double bx, double by,
                                double px, double py) {
        return (bx - ax) * (py - ay) - (by - ay) * (px - ax);
    }

    /** 已知点 p 与线段 ab 共线时，判断 p 是否落在线段上。 */
    private static boolean onSegment(double ax, double ay, double bx, double by,
                                     double px, double py) {
        return px >= Math.min(ax, bx) - 1e-9 && px <= Math.max(ax, bx) + 1e-9
                && py >= Math.min(ay, by) - 1e-9 && py <= Math.max(ay, by) + 1e-9;
    }
}
