package com.xiaowu.game.starveil.infrastructure;

import javafx.scene.text.Font;

import java.io.InputStream;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * 字体加载工具。
 *
 * <p>{@code Font.loadFont(stream, size)} 在<b>流为 null 或内容不是合法字体</b>时返回 null。
 * 把它直接赋给控件（{@code label.setFont(...)}）后果很隐蔽：JavaFX 要到布局阶段才解引用
 * 字体，于是表现为「窗口一显示就崩」：
 * <pre>
 * java.lang.NullPointerException:
 *   Cannot invoke "javafx.scene.text.Font.getNativeFont()" because "&lt;parameter1&gt;" is null
 *     at javafx.scene.text.Font$1.getNativeFont
 *     at com.sun.javafx.scene.control.skin.Utils.computeTextWidth
 *     at javafx.scene.control.skin.LabeledSkinBase.computeMinLabeledPartWidth
 * </pre>
 *
 * <p>框架<b>默认不带任何字体</b>（字体由内容项目提供），所以「加载失败」是常态而非异常。
 * 所有字体加载都必须走这里 —— 它保证<b>绝不返回 null</b>，失败时回退系统字体。
 *
 * <p>这个 bug 是靠 {@code -no-content} 强制空内容运行才暴露出来的：
 * 只读代码时「文件缺失」这条分支看着都有兜底，但真正出问题的是
 * 「把 null 直接塞给控件」这一支。
 */
public final class Fonts {

    private Fonts() {
    }

    /**
     * 安全加载字体。
     *
     * @param resourcePath 资源路径（如 {@code starveil:fonts/xxx.ttf}），可为 null
     * @param size         字号
     * @return 字体；加载失败时返回系统默认字体，<b>绝不返回 null</b>
     */
    public static Font safeFont(String resourcePath, double size) {
        if (resourcePath == null || resourcePath.isEmpty()) {
            return Font.font(size);
        }
        try (InputStream is = ResourceResolver.getResourceAsStream(resourcePath)) {
            if (is != null) {
                Font f = Font.loadFont(is, size);
                if (f != null) {
                    return f;
                }
            }
            Logger("DEBUG", "字体不可用，回退系统字体: " + resourcePath);
        } catch (Exception e) {
            Logger("DEBUG", "字体加载失败，回退系统字体: " + resourcePath + " - " + e.getMessage());
        }
        return Font.font(size);
    }
}
