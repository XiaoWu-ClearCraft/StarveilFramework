package com.xiaowu.game.starveil.platform.windows;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

public class WindowsUserManager {

    private static String lastCreatedUsername = null;
    private static final Map<String, String> shortcutPaths = new HashMap<>();

    public static String getLastCreatedUsername() {
        return lastCreatedUsername;
    }

    public static void setLastCreatedUsername(String username) {
        lastCreatedUsername = username;
        if (username != null) {
            initializeShortcutPaths(username);
        }
    }

    private static void initializeShortcutPaths(String username) {
        String userProfilePath = "C:\\Users\\" + username;

        shortcutPaths.clear();
        shortcutPaths.put("Desktop", userProfilePath + "\\Desktop");
        shortcutPaths.put("Documents", userProfilePath + "\\Documents");
        shortcutPaths.put("Downloads", userProfilePath + "\\Downloads");
        shortcutPaths.put("Music", userProfilePath + "\\Music");
        shortcutPaths.put("Pictures", userProfilePath + "\\Pictures");
        shortcutPaths.put("Videos", userProfilePath + "\\Videos");
        shortcutPaths.put("AppData", userProfilePath + "\\AppData");
        shortcutPaths.put("LocalAppData", userProfilePath + "\\AppData\\Local");
        shortcutPaths.put("RoamingAppData", userProfilePath + "\\AppData\\Roaming");
        shortcutPaths.put("Temp", userProfilePath + "\\AppData\\Local\\Temp");
        shortcutPaths.put("RecycleBin", "C:\\$Recycle.Bin\\" + getRecycleBinSid(username));

        Logger("INFO", "快捷路径初始化完成，用户: " + username);
    }

    public interface Netapi32 extends Library {
        Netapi32 INSTANCE = Native.load("netapi32", Netapi32.class);

        int NetUserAdd(
                WString servername,
                int level,
                USER_INFO_1 userInfo,
                IntByReference parm_err
        );

        int NetUserDel(
                WString servername,
                WString username
        );
    }

    public interface Advapi32 extends Library {
        Advapi32 INSTANCE = Native.load("advapi32", Advapi32.class);

        boolean LogonUserW(
                WString lpszUsername,
                WString lpszDomain,
                WString lpszPassword,
                int dwLogonType,
                int dwLogonProvider,
                PointerByReference phToken
        );

        boolean ImpersonateLoggedOnUser(Pointer hToken);
        boolean RevertToSelf();
    }

    public interface Kernel32 extends Library {
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);

        int GetLastError();
        boolean CloseHandle(Pointer handle);
    }

    public interface Userenv extends Library {
        Userenv INSTANCE = Native.load("userenv", Userenv.class);

        boolean CreateProfile(
                WString pszUserSid,
                WString pszUserName,
                WString pszProfilePath,
                int cchProfilePath
        );
    }

    public interface Shell32 extends Library {
        Shell32 INSTANCE = Native.load("shell32", Shell32.class);

        int SHFileOperationW(SHFILEOPSTRUCTW lpFileOpStruct);
    }

    public static class SHFILEOPSTRUCTW extends com.sun.jna.Structure {
        public Pointer hwnd;
        public int wFunc;
        public WString pFrom;
        public WString pTo;
        public short fFlags;
        public boolean fAnyOperationsAborted;
        public Pointer hNameMappings;
        public WString lpszProgressTitle;

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList(
                    "hwnd", "wFunc", "pFrom", "pTo", "fFlags",
                    "fAnyOperationsAborted", "hNameMappings", "lpszProgressTitle"
            );
        }
    }

    public static class USER_INFO_1 extends com.sun.jna.Structure {
        public WString usri1_name;
        public WString usri1_password;
        public int usri1_password_age;
        public int usri1_priv;
        public WString usri1_home_dir;
        public WString usri1_comment;
        public int usri1_flags;
        public WString usri1_script_path;

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.Arrays.asList(
                    "usri1_name", "usri1_password", "usri1_password_age",
                    "usri1_priv", "usri1_home_dir", "usri1_comment",
                    "usri1_flags", "usri1_script_path"
            );
        }
    }

    private static final int USER_PRIV_USER = 1;
    private static final int UF_SCRIPT = 0x0001;
    private static final int UF_NORMAL_ACCOUNT = 0x0200;
    private static final int UF_DONT_EXPIRE_PASSWD = 0x10000;
    private static final int LOGON32_LOGON_INTERACTIVE = 2;
    private static final int LOGON32_LOGON_BATCH = 4;
    private static final int LOGON32_PROVIDER_DEFAULT = 0;

    private static final int FO_COPY = 2;
    private static final int FO_DELETE = 3;
    private static final int FO_MOVE = 1;
    private static final int FO_RENAME = 4;
    private static final int FOF_ALLOWUNDO = 0x40;
    private static final int FOF_SILENT = 0x4;
    private static final int FOF_NOCONFIRMATION = 0x10;

    private static final int NERR_Success = 0;

    public static boolean createAndInitializeUser(String username, String fullName, String password) {
        try {
            Logger("INFO", "开始创建用户并让系统自动初始化 - 用户名: " + username + ", 全名: " + fullName);

            lastCreatedUsername = username;

            if (!createUserAccountOnly(username, fullName, password)) {
                Logger("ERROR", "用户账户创建失败");
                return false;
            }

            Thread.sleep(2000);

            if (!triggerSystemProfileCreation(username, password)) {
                Logger("ERROR", "系统配置文件创建失败");
                return false;
            }

            initializeShortcutPaths(username);

            Logger("INFO", "用户创建和系统初始化完成 - 用户名: " + username + ", 全名: " + fullName);
            return true;

        } catch (Exception e) {
            Logger("ERROR", "用户创建流程异常: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private static String getRecycleBinSid(String username) {
        try {
            String command = String.format(
                    "powershell -Command \"$user = New-Object System.Security.Principal.NTAccount('%s'); " +
                            "$sid = $user.Translate([System.Security.Principal.SecurityIdentifier]).Value; " +
                            "Write-Output $sid\"",
                    username
            );

            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String sid = reader.readLine().trim();
            process.waitFor();

            return sid;
        } catch (Exception e) {
            Logger("ERROR", "获取用户SID失败: " + e.getMessage());
            return null;
        }
    }

    public static boolean createFileInUserFolder(String location, String fileName, File fileData) {
        return createFileInUserFolder(lastCreatedUsername, location, fileName, fileData);
    }

    public static boolean createFileInUserFolder(String username, String location, String fileName, File fileData) {
        try {
            if (username == null) {
                username = lastCreatedUsername;
                if (username == null) {
                    throw new IllegalStateException("未指定用户名且无最近创建的用户");
                }
            }

            String targetPath = resolvePath(username, location, fileName);
            Logger("INFO", "创建文件: " + targetPath);

            Path targetDir = Paths.get(targetPath).getParent();
            if (targetDir != null) {
                Files.createDirectories(targetDir);
            }

            Files.copy(fileData.toPath(), Paths.get(targetPath), StandardCopyOption.REPLACE_EXISTING);

            setFilePermissions(targetPath, username);

            Logger("INFO", "文件创建成功: " + targetPath);
            return true;

        } catch (Exception e) {
            Logger("ERROR", "创建文件失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static boolean createDirectoryInUserFolder(String location, String dirName) {
        return createDirectoryInUserFolder(lastCreatedUsername, location, dirName);
    }

    public static boolean createDirectoryInUserFolder(String username, String location, String dirName) {
        try {
            if (username == null) {
                username = lastCreatedUsername;
                if (username == null) {
                    throw new IllegalStateException("未指定用户名且无最近创建的用户");
                }
            }

            String targetPath = resolvePath(username, location, dirName);
            Logger("INFO", "创建目录: " + targetPath);

            Files.createDirectories(Paths.get(targetPath));
            setFilePermissions(targetPath, username);

            Logger("INFO", "目录创建成功: " + targetPath);
            return true;

        } catch (Exception e) {
            Logger("ERROR", "创建目录失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static boolean createFileInRecycleBin(String fileName, File fileData) {
        return createFileInRecycleBin(lastCreatedUsername, fileName, fileData);
    }

    public static boolean createFileInRecycleBin(String username, String fileName, File fileData) {
        try {
            if (username == null) {
                username = lastCreatedUsername;
                if (username == null) {
                    throw new IllegalStateException("未指定用户名且无最近创建的用户");
                }
            }

            String tempPath = "C:\\Users\\" + username + "\\AppData\\Local\\Temp\\" + fileName;
            Files.copy(fileData.toPath(), Paths.get(tempPath), StandardCopyOption.REPLACE_EXISTING);

            boolean movedToBin = moveFileToRecycleBin(tempPath);
            if (movedToBin) {
                Logger("INFO", "文件已成功移动到回收站: " + fileName);
                return true;
            } else {
                Files.deleteIfExists(Paths.get(tempPath));
                return false;
            }

        } catch (Exception e) {
            Logger("ERROR", "在回收站创建文件失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static boolean deleteFileFromUserFolder(String filePath) {
        return deleteFileFromUserFolder(lastCreatedUsername, filePath, false);
    }

    public static boolean deleteFileFromUserFolder(String username, String filePath, boolean moveToRecycleBin) {
        try {
            if (username == null) {
                username = lastCreatedUsername;
                if (username == null) {
                    throw new IllegalStateException("未指定用户名且无最近创建的用户");
                }
            }

            String targetPath = resolvePath(username, filePath, "");
            Logger("INFO", "删除文件: " + targetPath);

            if (moveToRecycleBin) {
                return moveFileToRecycleBin(targetPath);
            } else {
                Files.deleteIfExists(Paths.get(targetPath));
                Logger("INFO", "文件删除成功: " + targetPath);
                return true;
            }

        } catch (Exception e) {
            Logger("ERROR", "删除文件失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static boolean deleteDirectoryFromUserFolder(String dirPath) {
        return deleteDirectoryFromUserFolder(lastCreatedUsername, dirPath, false);
    }

    public static boolean deleteDirectoryFromUserFolder(String username, String dirPath, boolean moveToRecycleBin) {
        try {
            if (username == null) {
                username = lastCreatedUsername;
                if (username == null) {
                    throw new IllegalStateException("未指定用户名且无最近创建的用户");
                }
            }

            String targetPath = resolvePath(username, dirPath, "");
            Logger("INFO", "删除目录: " + targetPath);

            if (moveToRecycleBin) {
                return moveFileToRecycleBin(targetPath);
            } else {
                Files.walkFileTree(Paths.get(targetPath), new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
                Logger("INFO", "目录删除成功: " + targetPath);
                return true;
            }

        } catch (Exception e) {
            Logger("ERROR", "删除目录失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private static boolean moveFileToRecycleBin(String filePath) {
        try {
            SHFILEOPSTRUCTW fileOp = new SHFILEOPSTRUCTW();
            fileOp.wFunc = FO_DELETE;

            String doubleNullTerminatedPath = filePath + "\0\0";
            fileOp.pFrom = new WString(doubleNullTerminatedPath);
            fileOp.pTo = null;
            fileOp.fFlags = FOF_ALLOWUNDO | FOF_SILENT | FOF_NOCONFIRMATION;
            fileOp.fAnyOperationsAborted = false;
            fileOp.hNameMappings = null;
            fileOp.lpszProgressTitle = null;

            int result = Shell32.INSTANCE.SHFileOperationW(fileOp);
            boolean success = (result == 0) && !fileOp.fAnyOperationsAborted;

            if (success) {
                Logger("INFO", "文件已移动到回收站: " + filePath);
            } else {
                Logger("ERROR", "移动到回收站失败，错误代码: " + result);
            }

            return success;

        } catch (Exception e) {
            Logger("ERROR", "移动到回收站异常: " + e.getMessage());
            return false;
        }
    }

    private static String resolvePath(String username, String location, String fileName) {
        if (location.contains(":") || location.startsWith("\\\\")) {
            return Paths.get(location, fileName).toString();
        }

        String basePath = shortcutPaths.get(location);
        if (basePath != null) {
            return Paths.get(basePath, fileName).toString();
        }

        String userProfile = "C:\\Users\\" + username;
        return Paths.get(userProfile, location, fileName).toString();
    }

    private static void setFilePermissions(String filePath, String username) {
        try {
            String command = String.format("icacls \"%s\" /grant %s:F /T", filePath, username);
            Process process = Runtime.getRuntime().exec(command);
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                Logger("INFO", "文件权限设置成功: " + filePath);
            } else {
                Logger("ERROR", "文件权限设置失败，退出代码: " + exitCode);
            }
        } catch (Exception e) {
            Logger("ERROR", "设置文件权限异常: " + e.getMessage());
        }
    }

    public static String listUserFolderContents(String location) {
        return listUserFolderContents(lastCreatedUsername, location);
    }

    public static String listUserFolderContents(String username, String location) {
        try {
            if (username == null) {
                username = lastCreatedUsername;
                if (username == null) {
                    throw new IllegalStateException("未指定用户名且无最近创建的用户");
                }
            }

            String targetPath = resolvePath(username, location, "");
            Path dirPath = Paths.get(targetPath);

            if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
                return "目录不存在: " + targetPath;
            }

            StringBuilder result = new StringBuilder();
            result.append("目录内容: ").append(targetPath).append("\n\n");

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath)) {
                for (Path file : stream) {
                    BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                    String type = attrs.isDirectory() ? "[目录]" : "[文件]";
                    String size = attrs.isDirectory() ? "" : String.format(" (%,d 字节)", attrs.size());
                    result.append(String.format("%s %s%s\n", type, file.getFileName(), size));
                }
            }

            return result.toString();

        } catch (Exception e) {
            return "列出目录内容失败: " + e.getMessage();
        }
    }

    public static boolean existsInUserFolder(String path) {
        return existsInUserFolder(lastCreatedUsername, path);
    }

    public static boolean existsInUserFolder(String username, String path) {
        try {
            if (username == null) {
                username = lastCreatedUsername;
                if (username == null) {
                    throw new IllegalStateException("未指定用户名且无最近创建的用户");
                }
            }

            String targetPath = resolvePath(username, path, "");
            return Files.exists(Paths.get(targetPath));

        } catch (Exception e) {
            Logger("ERROR", "检查文件存在性失败: " + e.getMessage());
            return false;
        }
    }

    private static boolean createUserAccountOnly(String username, String fullName, String password) {
        try {
            Logger("INFO", "创建用户账户 - 用户名: " + username + ", 全名: " + fullName);

            String command = String.format(
                    "powershell -Command \"" +
                            "$password = ConvertTo-SecureString '%s' -AsPlainText -Force; " +
                            "New-LocalUser -Name '%s' -Password $password -Description 'Created by ClearCraft Game' -FullName '%s'; " +
                            "Set-LocalUser -Name '%s' -AccountNeverExpires $true -PasswordNeverExpires $true; " +
                            "Enable-LocalUser -Name '%s'; " +
                            "Add-LocalGroupMember -Group 'Users' -Member '%s'; " +
                            "Add-LocalGroupMember -Group 'Administrators' -Member '%s'; " +
                            "Write-Output '用户账户创建完成，已添加到Administrators组'\"",
                    password, username, fullName, username, username, username, username
            );

            Process process = Runtime.getRuntime().exec(command);
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                Logger("INFO", "用户账户创建成功 - 用户名: " + username + ", 全名: " + fullName);
                return true;
            } else {
                Logger("ERROR", "用户账户创建失败，退出代码: " + exitCode);
                try {
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        Logger("DEBUG", "命令的错误输出: " + line);
                    }
                } catch (Exception ex) {
                    Logger("DEBUG", "读取命令错误输出失败: " + ex.getMessage());
                }
                return false;
            }

        } catch (Exception e) {
            Logger("ERROR", "创建用户账户异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 依次尝试三种方式触发系统创建用户配置文件。
     *
     * <p>每一种方式<b>单独失败是正常的</b>（不同 Windows 版本可用的手段不同），
     * 所以它们的失败记 WARNING；只有三种全失败才算真正的 ERROR ——
     * 否则一个成功的降级流程会在日志里留下三条 ERROR，把真问题淹掉。
     */
    private static boolean triggerSystemProfileCreation(String username, String password) {
        try {
            Logger("INFO", "触发系统自动创建用户配置文件: " + username);

            if (createProfileWithWin32(username)) {
                Logger("INFO", "Win32 API配置文件创建成功");
                return true;
            }

            if (triggerProfileWithPowerShell(username, password)) {
                Logger("INFO", "PowerShell触发配置文件创建成功");
                return true;
            }

            if (triggerProfileWithSystemCommand(username, password)) {
                Logger("INFO", "系统命令触发配置文件创建成功");
                return true;
            }

            Logger("ERROR", "所有配置文件创建方法都失败");
            return false;

        } catch (Exception e) {
            Logger("ERROR", "触发系统配置文件创建异常: " + e.getMessage());
            return false;
        }
    }

    private static boolean createProfileWithWin32(String username) {
        try {
            Logger("INFO", "使用Win32 API创建配置文件: " + username);

            String userSid = getUserSID(username);
            if (userSid == null) {
                Logger("WARNING", "取不到用户 SID，Win32 方式不可用，交给下一种方式");
                return false;
            }

            String profilePath = "C:\\Users\\" + username;
            boolean profileCreated = Userenv.INSTANCE.CreateProfile(
                    new WString(userSid),
                    new WString(username),
                    new WString(profilePath),
                    profilePath.length()
            );

            if (profileCreated) {
                Logger("INFO", "系统已自动创建完整的用户配置文件");
                return true;
            } else {
                Logger("WARNING", "CreateProfile 返回失败，交给下一种方式");
                return false;
            }

        } catch (Exception e) {
            Logger("WARNING", "Win32 API 创建配置文件出错，交给下一种方式: " + e.getMessage());
            return false;
        }
    }

    private static boolean triggerProfileWithPowerShell(String username, String password) {
        try {
            Logger("INFO", "使用PowerShell触发系统初始化: " + username);

            String command = String.format(
                    "powershell -Command \"" +
                            "$cred = New-Object System.Management.Automation.PSCredential('%s', (ConvertTo-SecureString '%s' -AsPlainText -Force)); " +
                            "$process = Start-Process cmd.exe -Credential $cred -WindowStyle Hidden -ArgumentList '/c', 'exit' -PassThru; " +
                            "$process.WaitForExit(); " +
                            "Write-Output '系统配置文件初始化触发完成'\"",
                    username, password
            );

            Process process = Runtime.getRuntime().exec(command);
            int exitCode = process.waitFor();

            Thread.sleep(3000);
            return exitCode == 0;

        } catch (Exception e) {
            Logger("WARNING", "PowerShell 方式出错，交给下一种方式: " + e.getMessage());
            return false;
        }
    }

    private static boolean triggerProfileWithSystemCommand(String username, String password) {
        try {
            Logger("INFO", "使用系统命令触发配置文件创建: " + username);

            String command = String.format(
                    "cmd /c echo %s | runas /user:%s \"cmd /c exit\"",
                    password, username
            );

            Process process = Runtime.getRuntime().exec(command);
            Thread.sleep(5000);

            String profilePath = "C:\\Users\\" + username;
            File userDir = new File(profilePath);

            if (userDir.exists() && userDir.isDirectory()) {
                Logger("INFO", "系统已自动创建用户文件夹: " + profilePath);
                return true;
            }

            return false;

        } catch (Exception e) {
            Logger("WARNING", "系统命令方式出错，交给调用方判断: " + e.getMessage());
            return false;
        }
    }

    private static String getUserSID(String username) {
        try {
            String command = String.format(
                    "powershell -Command \"$user = New-Object System.Security.Principal.NTAccount('%s'); " +
                            "$sid = $user.Translate([System.Security.Principal.SecurityIdentifier]).Value; " +
                            "Write-Output $sid\"",
                    username
            );

            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String sid = reader.readLine();
            int exitCode = process.waitFor();

            if (exitCode == 0 && sid != null && !sid.trim().isEmpty()) {
                Logger("DEBUG", "获取用户 SID 成功: " + sid);
                return sid;
            } else {
                // 取不到 SID 只是一种方式不可用，由上层的多种降级继续尝试
                Logger("WARNING", "获取用户 SID 失败（退出码 " + exitCode + "）");
                return null;
            }

        } catch (Exception e) {
            Logger("WARNING", "获取用户 SID 出错: " + e.getMessage());
            return null;
        }
    }

    public static boolean updateUserFullName(String username, String fullName) {
        try {
            Logger("INFO", "更新用户全名 - 用户名: " + username + ", 新全名: " + fullName);

            String command = String.format(
                    "powershell -Command \"Set-LocalUser -Name '%s' -FullName '%s'\"",
                    username, fullName
            );

            Process process = Runtime.getRuntime().exec(command);
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                Logger("INFO", "用户全名更新成功");
                return true;
            } else {
                Logger("ERROR", "用户全名更新失败");
                return false;
            }

        } catch (Exception e) {
            Logger("ERROR", "更新用户全名异常: " + e.getMessage());
            return false;
        }
    }

    public static String getUserInfo(String username) {
        try {
            String command = String.format(
                    "powershell -Command \"$user = Get-LocalUser -Name '%s'; " +
                            "Write-Output ('用户名: ' + $user.Name); " +
                            "Write-Output ('全名: ' + $user.FullName); " +
                            "Write-Output ('描述: ' + $user.Description); " +
                            "Write-Output ('启用状态: ' + $user.Enabled)\"",
                    username
            );

            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            StringBuilder info = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                info.append(line).append("\n");
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                return info.toString();
            } else {
                return "获取用户信息失败";
            }

        } catch (Exception e) {
            return "获取用户信息异常: " + e.getMessage();
        }
    }

    public static boolean isUserProfileReady(String username) {
        try {
            String profilePath = "C:\\Users\\" + username;
            File userDir = new File(profilePath);

            if (!userDir.exists() || !userDir.isDirectory()) {
                return false;
            }

            File desktopDir = new File(userDir, "Desktop");
            File documentsDir = new File(userDir, "Documents");
            File appDataDir = new File(userDir, "AppData");

            return desktopDir.exists() && documentsDir.exists() && appDataDir.exists();

        } catch (Exception e) {
            Logger("ERROR", "检查用户配置文件状态异常: " + e.getMessage());
            return false;
        }
    }

    public static boolean deleteUser(String username) {
        try {
            String command = String.format(
                    "powershell -Command \"Remove-LocalUser -Name '%s'\"",
                    username
            );

            Process process = Runtime.getRuntime().exec(command);
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                Logger("INFO", "用户删除成功: " + username);
                return true;
            } else {
                Logger("ERROR", "用户删除失败: " + username);
                return false;
            }

        } catch (Exception e) {
            Logger("ERROR", "删除用户异常: " + e.getMessage());
            return false;
        }
    }
}
