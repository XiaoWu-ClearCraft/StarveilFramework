package com.xiaowu.game.starveil.config;

/**
 * 启动配置类
 * 用于存储启动参数处理后的配置
 */
public class LauncherConfig {
    private static LauncherConfig instance;

    private boolean debugMode = false;
    private boolean needAdmin = true;
    private boolean testCompatWarning = false;
    private boolean noChangeGpu = false;
    /** -overlay-test：遮罩层自检（降帧保持 → 花屏 → 恢复）。 */
    private boolean overlayTest = false;

    /**
     * -no-content：强制在「没有游戏内容」的情况下运行。
     *
     * <p>正常情况下框架发现 {@code com.xiaowu.game.starveil.content} 不存在会致命退出 ——
     * 框架本身不是可玩的游戏。这个开关用于开发/测试：强制跑起来检查缺资源时
     * 各处降级是否正常（纯黑背景、系统默认图标、无音频…），而不是崩在某处。
     */
    private boolean noContent = false;

    private LauncherConfig() {
    }

    public static LauncherConfig getInstance() {
        if (instance == null) {
            instance = new LauncherConfig();
        }
        return instance;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    public boolean isNeedAdmin() {
        return needAdmin;
    }

    public void setNeedAdmin(boolean needAdmin) {
        this.needAdmin = needAdmin;
    }

    public boolean isTestCompatWarning() {
        return testCompatWarning;
    }

    public void setTestCompatWarning(boolean testCompatWarning) {
        this.testCompatWarning = testCompatWarning;
    }

    public boolean isNoChangeGpu() {
        return noChangeGpu;
    }

    public void setNoChangeGpu(boolean noChangeGpu) {
        this.noChangeGpu = noChangeGpu;
    }

    public boolean isOverlayTest() {
        return overlayTest;
    }

    public void setOverlayTest(boolean overlayTest) {
        this.overlayTest = overlayTest;
    }

    public boolean isNoContent() {
        return noContent;
    }

    public void setNoContent(boolean noContent) {
        this.noContent = noContent;
    }

    /**
     * 重置配置（用于测试或重新启动）
     */
    public void reset() {
        this.debugMode = false;
        this.needAdmin = true;
        this.testCompatWarning = false;
        this.noChangeGpu = false;
        this.overlayTest = false;
        this.noContent = false;
    }
}
