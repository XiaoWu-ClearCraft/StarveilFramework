package com.xiaowu.game.starveil.platform.common;

import com.xiaowu.game.starveil.platform.api.PlatformInjectPoints;
import com.xiaowu.game.starveil.platform.windows.WindowsHardwareManager;
import com.xiaowu.game.starveil.plugin.InjectPoint;

/**
 * 硬件信息门面。
 *
 * <p>本版本只内置 Windows 采集实现（WMI / PowerShell）。每个方法都是一个注入点，
 * 其它平台的适配插件可以用 REPLACE 换成自己的采集逻辑（例如读取
 * {@code /proc}、{@code lscpu}、{@code lspci} 等）。
 *
 * <p>未被插件覆盖时，非 Windows 平台返回占位文本，不会抛异常。
 */
public class HardwareManager {

    private static final String UNSUPPORTED = "当前平台没有内置的硬件信息采集实现";

    private HardwareManager() {}

    @InjectPoint(PlatformInjectPoints.HARDWARE_SYSTEM_INFO)
    public static String getSystemInfo() {
        return SystemDetector.isWindows() ? WindowsHardwareManager.getSystemInfo() : UNSUPPORTED;
    }

    @InjectPoint(PlatformInjectPoints.HARDWARE_MOTHERBOARD_INFO)
    public static String getMotherboardInfo() {
        return SystemDetector.isWindows() ? WindowsHardwareManager.getMotherboardInfo() : UNSUPPORTED;
    }

    @InjectPoint(PlatformInjectPoints.HARDWARE_MEMORY_INFO)
    public static String getMemoryInfo() {
        return SystemDetector.isWindows() ? WindowsHardwareManager.getMemoryInfo() : UNSUPPORTED;
    }

    @InjectPoint(PlatformInjectPoints.HARDWARE_CPU_INFO)
    public static String getCPUInfo() {
        return SystemDetector.isWindows() ? WindowsHardwareManager.getCPUInfo() : UNSUPPORTED;
    }

    @InjectPoint(PlatformInjectPoints.HARDWARE_GPU_INFO)
    public static String getGPUInfo() {
        return SystemDetector.isWindows() ? WindowsHardwareManager.getGPUInfo() : UNSUPPORTED;
    }

    @InjectPoint(PlatformInjectPoints.HARDWARE_SOUND_CARD_INFO)
    public static String getSoundCardInfo() {
        return SystemDetector.isWindows() ? WindowsHardwareManager.getSoundCardInfo() : UNSUPPORTED;
    }

    @InjectPoint(PlatformInjectPoints.HARDWARE_NETWORK_INFO)
    public static String getNetworkInfo() {
        return SystemDetector.isWindows() ? WindowsHardwareManager.getNetworkInfo() : UNSUPPORTED;
    }

    @InjectPoint(PlatformInjectPoints.HARDWARE_ALL_INFO)
    public static String getAllHardwareInfo() {
        return SystemDetector.isWindows() ? WindowsHardwareManager.getAllHardwareInfo() : UNSUPPORTED;
    }

    /**
     * 检测系统中是否存在独立显卡。
     */
    @InjectPoint(PlatformInjectPoints.HARDWARE_HAS_DEDICATED_GPU)
    public static boolean hasDedicatedGPU() {
        return SystemDetector.isWindows() && WindowsHardwareManager.hasDedicatedGPU();
    }
}
