package com.xiaowu.game.starveil.infrastructure;

import com.google.gson.JsonObject;
import com.xiaowu.game.starveil.infrastructure.logging.LoggerManager;

/**
 * 资源同名 meta 数据（内容为 JSON）。
 *
 * <p>meta 文件路径与资源文件同名，追加 {@code .meta} 后缀（内容为 JSON），
 * 例如 {@code starveil:textures/tiles/test.png} 的 meta 为
 * {@code starveil:textures/tiles/test.png.meta}。
 *
 * <p>支持的属性：
 * <pre>
 * {
 *   "animation": {
 *     "frameWidth": 32,     // 每帧宽度（像素）
 *     "frameHeight": 32,    // 每帧高度（像素）
 *     "frames": 8,          // 动画帧数
 *     "frameRate": 12       // 可选，播放帧率
 *   }
 * }
 * </pre>
 * 帧在贴图中自左上角 (0,0) 起按行序（从上到下、从左到右）排列。
 */
public class AssetMeta {

    private final JsonObject root;

    private AssetMeta(JsonObject root) {
        this.root = root != null ? root : new JsonObject();
    }

    /**
     * 加载资源的同名 meta 文件；不存在时返回 null。
     */
    public static AssetMeta load(String resourcePath) {
        JsonObject json = StarveilResourceResolver.loadMeta(resourcePath);
        return json == null ? null : new AssetMeta(json);
    }

    /**
     * 从指定资源路径加载 meta；加载失败或缺失时返回包含空配置的实例，
     * 便于调用方统一使用。
     */
    public static AssetMeta loadOrEmpty(String resourcePath) {
        JsonObject json = StarveilResourceResolver.loadMeta(resourcePath);
        return new AssetMeta(json);
    }

    public boolean hasAnimation() {
        return root.has("animation") && root.get("animation").isJsonObject();
    }

    public AnimationMeta getAnimation() {
        if (!hasAnimation()) {
            return null;
        }
        try {
            JsonObject anim = root.getAsJsonObject("animation");
            AnimationMeta meta = new AnimationMeta();
            if (anim.has("frameWidth")) meta.frameWidth = anim.get("frameWidth").getAsInt();
            if (anim.has("frameHeight")) meta.frameHeight = anim.get("frameHeight").getAsInt();
            if (anim.has("frames")) meta.frames = anim.get("frames").getAsInt();
            if (anim.has("frameRate")) meta.frameRate = anim.get("frameRate").getAsInt();
            return meta;
        } catch (Exception e) {
            LoggerManager.Logger("WARNING", "解析动画 meta 失败: " + e.getMessage());
            return null;
        }
    }

    public JsonObject getRoot() {
        return root;
    }

    /**
     * 动画帧元数据。
     */
    public static class AnimationMeta {
        /** 每帧宽度（像素），0 表示未知（将按整张图计算） */
        public int frameWidth;
        /** 每帧高度（像素），0 表示未知 */
        public int frameHeight;
        /** 动画帧数，0 表示由贴图尺寸自动计算 */
        public int frames;
        /** 播放帧率（帧/秒），0 表示未指定 */
        public int frameRate;

        public boolean isValid() {
            return frameWidth > 0 && frameHeight > 0;
        }
    }
}
