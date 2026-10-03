package com.xiaowu.game.starveil.platform.windows;

import com.sun.jna.platform.win32.Advapi32;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.W32APIOptions;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * SYSTEM 权限任务执行器
 * 
 * 用于在管理员权限的主进程中，临时以 SYSTEM 权限执行特定操作。
 * 这样主进程可以保持在管理员权限（保证主题监听、输入法等功能正常），
 * 而只有真正需要 SYSTEM 权限的操作才临时提权。
 * 
 * 使用方式：
 *   String result = SystemTaskRunner.runAsSystem("reg query HKLM\\SAM\\SAM");
 */
public class SystemTaskRunner {

    private static final int SYSTEM_TASK_TIMEOUT_SECONDS = 30;

    /**
     * 以 SYSTEM 权限执行命令
     * 
     * @param command 要执行的命令
     * @return 命令输出，null 表示执行失败
     */
    public static String runAsSystem(String command) {
        if (command == null || command.trim().isEmpty()) {
            Logger("ERROR", "SYSTEM 任务命令为空");
            return null;
        }

        // 首先检查是否已经是 SYSTEM
        if (WindowsPermissionHandler.isSystem()) {
            Logger("DEBUG", "当前已是 SYSTEM，直接执行命令");
            return executeCommand(command);
        }

        // 需要管理员权限才能提权到 SYSTEM
        if (!WindowsPermissionHandler.isAdmin()) {
            Logger("ERROR", "当前非管理员权限，无法执行 SYSTEM 任务");
            return null;
        }

        Logger("DEBUG", "尝试以 SYSTEM 权限执行: " + command);

        // 使用 NtObjectManager 模块提权到 SYSTEM 并执行命令
        return elevateAndExecute(command);
    }

    /**
     * 以 SYSTEM 权限执行注册表查询
     */
    public static String regQuerySystem(String key, String valueName) {
        String cmd = "reg query \"" + key + "\"";
        if (valueName != null && !valueName.isEmpty()) {
            cmd += " /v \"" + valueName + "\"";
        }
        return runAsSystem(cmd);
    }

    /**
     * 以 SYSTEM 权限执行注册表设置
     */
    public static boolean regSetSystem(String key, String valueName, String value, String type) {
        String cmd = "reg add \"" + key + "\" /v \"" + valueName + "\" /t " + type + " /d \"" + value + "\" /f";
        String result = runAsSystem(cmd);
        return result != null && result.contains("操作成功");
    }

    /**
     * 以 SYSTEM 权限执行注册表删除
     */
    public static boolean regDeleteSystem(String key, String valueName) {
        String cmd = "reg delete \"" + key + "\"";
        if (valueName != null && !valueName.isEmpty()) {
            cmd += " /v \"" + valueName + "\"";
        } else {
            cmd += " /f";
        }
        String result = runAsSystem(cmd);
        return result != null && result.contains("操作成功");
    }

    /**
     * 检查当前进程是否以 SYSTEM 权限运行
     */
    public static boolean isCurrentProcessSystem() {
        return WindowsPermissionHandler.isSystem();
    }

    /**
     * 使用 NtObjectManager 提权到 SYSTEM 并执行命令
     */
    private static String elevateAndExecute(String command) {
        if (!WindowsPermissionHandler.isRunningFromExe()) {
            Logger("ERROR", "非 exe 运行，无法使用 NtObjectManager 提权");
            return null;
        }

        String exePath = WindowsPermissionHandler.getExePath();
        if (!exePath.toLowerCase().endsWith(".exe")) {
            Logger("ERROR", "程序路径非 exe 后缀: " + exePath);
            return null;
        }

        String existingArgs = WindowsPermissionHandler.getCommandLineArgs(
            WindowsPermissionHandler.hasIllegalShutdownFlag());
        
        // 构建 PowerShell 命令来执行 SYSTEM 任务
        String psCommand = buildSystemTaskCommand(command);

        try {
            ProcessBuilder pb = new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-Command", psCommand
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();

            String output;
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                output = sb.toString();
            }

            boolean success = p.waitFor(SYSTEM_TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (success) {
                Logger("DEBUG", "SYSTEM 任务执行完成");
                return output.trim();
            } else {
                Logger("WARNING", "SYSTEM 任务执行超时 (" + SYSTEM_TASK_TIMEOUT_SECONDS + "s)");
                p.destroy();
                return null;
            }
        } catch (Exception e) {
            Logger("ERROR", "SYSTEM 任务执行异常: " + e.getMessage());
            return null;
        }
    }

    /**
     * 构建 PowerShell 命令来以 SYSTEM 权限执行任务
     * 使用 PsExec 或 NtObjectManager 来实现 SYSTEM 提权
     */
    private static String buildSystemTaskCommand(String userCommand) {
        // 方法 1：使用 PsExec（如果可用）- 更轻量，不需要安装模块
        // 方法 2：使用 NtObjectManager - 功能更强但需要安装

        // 这里使用 PsExec 方法，因为它更简单可靠
        String psExecPath = findPsExec();
        
        if (psExecPath != null) {
            // 使用 PsExec 执行
            return String.join("\r\n",
                "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8",
                "$ErrorActionPreference = 'Stop'",
                "$psexec = '" + psExecPath.replace("'", "''") + "'",
                "$process = Start-Process -FilePath $psexec -ArgumentList '-accepteula','-s','cmd','/c','" + 
                    userCommand.replace("'", "''").replace("\"", "\\\"") + 
                    "' -Wait -NoNewWindow -PassThru -RedirectStandardOutput ([System.IO.Path]::GetTempFileName())",
                "$outputFile = $process.StandardOutput",
                "if (Test-Path $outputFile) {",
                "    Get-Content $outputFile -Raw",
                "    Remove-Item $outputFile -Force",
                "}"
            );
        } else {
            // 使用 NtObjectManager 作为备选方案
            return buildNtObjectManagerCommand(userCommand);
        }
    }

    /**
     * 查找 PsExec 工具路径
     */
    private static String findPsExec() {
        String[] paths = {
            System.getenv("ProgramFiles") + "\\SysinternalsSuite\\PsExec.exe",
            System.getenv("ProgramFiles(x86)") + "\\SysinternalsSuite\\PsExec.exe",
            System.getenv("USERPROFILE") + "\\Downloads\\PsExec.exe",
            System.getenv("USERPROFILE") + "\\Desktop\\PsExec.exe"
        };

        for (String path : paths) {
            if (path != null && new File(path).exists()) {
                return path;
            }
        }

        // 尝试在 PATH 中查找
        try {
            Process p = Runtime.getRuntime().exec("where psexec.exe");
            p.waitFor(2, TimeUnit.SECONDS);
            if (p.exitValue() == 0) {
                BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
                String line = br.readLine();
                if (line != null && !line.isEmpty()) {
                    return line.trim();
                }
            }
        } catch (Exception e) {
            // 忽略
        }

        return null;
    }

    /**
     * 使用 NtObjectManager 构建 SYSTEM 提权命令
     */
    private static String buildNtObjectManagerCommand(String userCommand) {
        return String.join("\r\n",
            "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8",
            "$ErrorActionPreference = 'Stop'",
            
            // 确保 NtObjectManager 可用
            "$mod = Get-Module -ListAvailable -Name NtObjectManager",
            "if (-not $mod) {",
            "    $pp = Get-PackageProvider -ListAvailable | Where-Object {$_.Name -eq 'NuGet'}",
            "    if (-not $pp) { Install-PackageProvider -Name NuGet -Force -Scope CurrentUser }",
            "    $repo = Get-PSRepository -Name PSGallery -ErrorAction SilentlyContinue",
            "    if ($repo -and $repo.InstallationPolicy -ne 'Trusted') {",
            "        Set-PSRepository -Name PSGallery -InstallationPolicy Trusted",
            "    }",
            "    Install-Module -Name NtObjectManager -Force -Scope CurrentUser",
            "    if(-not $?) { Write-Error 'NtObjectManager 安装失败'; exit 1 }",
            "}",
            "Import-Module NtObjectManager",
            
            // 创建 SYSTEM 进程执行命令
            "$tempOut = [System.IO.Path]::GetTempFileName()",
            "$tempErr = [System.IO.Path]::GetTempFileName()",
            "try {",
            "    $proc = Start-Win32ChildProcess 'cmd.exe' -Argument '/c \"" + 
                userCommand.replace("\"", "\\\"").replace("'", "''") + 
                " > \"\"'\"'\"'\"$tempOut\"'\"'\"' 2>\"'\"'\"'\"$tempErr\"'\"'\"'\"' -Token (Get-NtToken -System)",
            "    Wait-Process -Id $proc.Id -Timeout " + SYSTEM_TASK_TIMEOUT_SECONDS,
            "    Get-Content $tempOut -Raw",
            "} catch {",
            "    Write-Warning \"SYSTEM 任务执行失败: $_\"",
            "    if (Test-Path $tempErr) { Get-Content $tempErr -Raw }",
            "} finally {",
            "    if (Test-Path $tempOut) { Remove-Item $tempOut -Force }",
            "    if (Test-Path $tempErr) { Remove-Item $tempErr -Force }",
            "}"
        );
    }

    /**
     * 直接执行命令（当前权限）
     */
    private static String executeCommand(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "cmd.exe", "/c", command
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();

            String output;
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                output = sb.toString();
            }

            p.waitFor(SYSTEM_TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return output.trim();
        } catch (Exception e) {
            Logger("ERROR", "命令执行异常: " + e.getMessage());
            return null;
        }
    }
}
