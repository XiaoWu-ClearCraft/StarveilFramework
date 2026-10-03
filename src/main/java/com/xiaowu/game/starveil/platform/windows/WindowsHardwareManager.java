package com.xiaowu.game.starveil.platform.windows;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class WindowsHardwareManager {

    private static String executePowerShellCommand(String powerShellCommand) {
        try {
            String command = "powershell -Command \"[Console]::OutputEncoding = [System.Text.Encoding]::UTF8; " + powerShellCommand + "\"";
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    result.append(line).append("\n");
                }
            }

            StringBuilder error = new StringBuilder();
            while ((line = errorReader.readLine()) != null) {
                error.append(line).append("\n");
            }

            reader.close();
            errorReader.close();
            process.waitFor();

            return result.length() > 0 ? result.toString() : "无法获取信息: " + error.toString();

        } catch (Exception e) {
            return "无法获取信息: " + e.getMessage();
        }
    }

    public static String getSystemInfo() {
        StringBuilder info = new StringBuilder();

        String osName = System.getProperty("os.name");
        String osVersion = System.getProperty("os.version");
        String osArch = System.getProperty("os.arch");
        info.append("系统:\n")
                .append(osName).append(" ").append(osVersion).append(" ").append(osArch)
                .append("\n\n");

        String systemInfo = executePowerShellCommand("Get-CimInstance Win32_OperatingSystem | Select-Object Caption,Version,BuildNumber | Format-List");
        info.append("系统详情:\n").append(systemInfo).append("\n");

        return info.toString();
    }

    public static String getMotherboardInfo() {
        String baseboard = executePowerShellCommand("Get-CimInstance Win32_BaseBoard | Select-Object Product,Manufacturer,Version | Format-List");
        String computerSystem = executePowerShellCommand("Get-CimInstance Win32_ComputerSystem | Select-Object Model | Format-List");

        StringBuilder info = new StringBuilder();
        info.append("主板:\n").append(computerSystem).append(baseboard).append("\n");
        return info.toString();
    }

    public static String getMemoryInfo() {
        try {
            String memoryInfo = executePowerShellCommand(
                    "$totalMem = [math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB, 2); " +
                            "$freeMem = [math]::Round((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1MB, 2); " +
                            "Write-Output \"总内存: $totalMem GB\"; " +
                            "Write-Output \"可用内存: $freeMem GB\""
            );

            return "内存:\n" + memoryInfo + "\n\n";

        } catch (Exception e) {
            return "内存:\n无法获取内存信息\n\n";
        }
    }

    public static String getCPUInfo() {
        String cpuInfo = executePowerShellCommand("Get-CimInstance Win32_Processor | Select-Object Name,Manufacturer,MaxClockSpeed,NumberOfCores,NumberOfLogicalProcessors | Format-List");
        return "CPU:\n" + cpuInfo + "\n";
    }

    /**
     * 检测系统中是否存在独立显卡（非基本显示适配器且显存 > 1GB）
     */
    public static boolean hasDedicatedGPU() {
        String result = executePowerShellCommand(
            "Get-CimInstance Win32_VideoController | Where-Object {$_.Name -notlike '*Basic*' -and $_.AdapterRAM -gt 1073741824} | Select-Object Name | Format-Table -AutoSize"
        );
        return !result.startsWith("无法获取信息") && !result.trim().isEmpty();
    }

    public static String getGPUInfo() {
        String gpuInfo = executePowerShellCommand("Get-CimInstance Win32_VideoController | Where-Object {$_.Name -notlike '*Basic*'} | Select-Object Name,AdapterRAM,DriverVersion | Format-Table -AutoSize");

        StringBuilder info = new StringBuilder();
        info.append("GPU:\n").append(gpuInfo).append("\n");
        return info.toString();
    }

    public static String getSoundCardInfo() {
        String soundInfo = executePowerShellCommand("Get-CimInstance Win32_SoundDevice | Where-Object {$_.Manufacturer -ne 'Microsoft'} | Select-Object Name,Manufacturer | Format-Table -AutoSize");

        StringBuilder info = new StringBuilder();
        info.append("声卡:\n").append(soundInfo).append("\n");
        return info.toString();
    }

    public static String getNetworkInfo() {
        String networkInfo = executePowerShellCommand(
                "Get-CimInstance Win32_NetworkAdapter | Where-Object {$_.NetEnabled -eq $true -and $_.MACAddress -ne $null} | " +
                        "Select-Object @{Name='Index';Expression={$_.Index}}, MACAddress, Manufacturer, Name | " +
                        "Format-Table -AutoSize"
        );

        StringBuilder info = new StringBuilder();
        info.append("网卡:\n").append(networkInfo).append("\n");
        return info.toString();
    }

    public static String getAllHardwareInfo() {
        StringBuilder allInfo = new StringBuilder();
        allInfo.append(getSystemInfo());
        allInfo.append(getMotherboardInfo());
        allInfo.append(getMemoryInfo());
        allInfo.append(getCPUInfo());
        allInfo.append(getGPUInfo());
        allInfo.append(getSoundCardInfo());
        allInfo.append(getNetworkInfo());

        return allInfo.toString();
    }
}