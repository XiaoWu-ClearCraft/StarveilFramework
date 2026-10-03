package com.xiaowu.game.starveil.infrastructure.persistence;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec;

import static com.xiaowu.game.starveil.infrastructure.logging.LoggerManager.Logger;

/**
 * 文件加密/解密工具类 —— 统一管理游戏数据的加密存储。
 *
 * <p><b>密钥可替换</b>：内容项目在 {@code com.xiaowu.game.starveil.content.init.init}
 * 里调用 {@link #configure(String, String)} 即可换成自己的一套密钥，
 * 这样同一台机器上不同游戏的数据文件互不可读 —— 否则任何人拿到本框架
 * 就能解开所有基于它的游戏存档。
 *
 * <p>未配置时使用框架默认值，因此「不装内容」也能正常工作。
 *
 * <p><b>为什么密钥长度必须正好 16</b>：算法是 AES-128（见 {@link #ALGORITHM}），
 * 密钥长度由算法决定而不是由代码偏好决定。短了或长了 JCE 会直接抛
 * {@code InvalidKeyException}，但那个异常会在第一次读存档时才冒出来，
 * 且信息晦涩。所以在配置入口就校验并给出明确提示。
 *
 * <p><b>换密钥 = 换存档格式</b>：密钥不同则老存档解不开。
 * 框架仍在测试阶段，不做历史数据兼容 —— 换密钥后老存档直接删掉即可。
 */
public final class FileCrypto {

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";

    /** AES-128：密钥长度固定 16 字节。 */
    public static final int KEY_LENGTH = 16;

    /** CBC 模式的初始化向量长度，同样固定 16 字节。 */
    public static final int IV_LENGTH = 16;

    /** 框架默认密钥（内容未配置时使用）。 */
    public static final String DEFAULT_KEY = "Starveil_DataKey";

    /** 框架默认 IV —— 保持历史值不变，避免「只换密钥」时把 IV 一起改动的意外。 */
    public static final String DEFAULT_IV = "1234567890123456";

    private static volatile byte[] key = DEFAULT_KEY.getBytes(StandardCharsets.UTF_8);
    private static volatile byte[] iv = DEFAULT_IV.getBytes(StandardCharsets.UTF_8);

    private FileCrypto() {
    }

    /**
     * 配置密钥与 IV。内容在 {@code init()} 里调用。
     *
     * <p>长度不合法时<b>保持原值并记录 ERROR</b>，而不是抛异常中断启动：
     * 密钥写错属于「配置失误」，让游戏以默认密钥继续跑（并明确报错）比
     * 直接崩在启动阶段更容易排查。
     *
     * @param newKey 16 字节密钥；{@code null} 或空串表示「不修改」
     * @param newIv  16 字节 IV；{@code null} 或空串表示「不修改」
     * @return 是否全部应用成功
     */
    public static boolean configure(String newKey, String newIv) {
        boolean ok = true;
        if (newKey != null && !newKey.isEmpty()) {
            byte[] bytes = newKey.getBytes(StandardCharsets.UTF_8);
            if (bytes.length == KEY_LENGTH) {
                key = bytes;
                Logger("INFO", "数据密钥已由内容指定（长度 " + bytes.length + "）");
            } else {
                ok = false;
                Logger("ERROR", "数据密钥长度必须是 " + KEY_LENGTH + " 字节（UTF-8 编码后），收到 "
                        + bytes.length + " 字节，已保持默认密钥");
            }
        }
        if (newIv != null && !newIv.isEmpty()) {
            byte[] bytes = newIv.getBytes(StandardCharsets.UTF_8);
            if (bytes.length == IV_LENGTH) {
                iv = bytes;
                Logger("INFO", "数据 IV 已由内容指定（长度 " + bytes.length + "）");
            } else {
                ok = false;
                Logger("ERROR", "数据 IV 长度必须是 " + IV_LENGTH + " 字节（UTF-8 编码后），收到 "
                        + bytes.length + " 字节，已保持默认 IV");
            }
        }
        return ok;
    }

    /** 当前密钥（仅供调试输出长度/指纹，不返回明文）。 */
    public static int keyLength() {
        return key.length;
    }

    public static int ivLength() {
        return iv.length;
    }

    /** 仅测试使用：恢复为框架默认密钥与 IV。 */
    static void resetForTest() {
        key = DEFAULT_KEY.getBytes(StandardCharsets.UTF_8);
        iv = DEFAULT_IV.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 加密字符串数据
     *
     * @param data 要加密的字符串
     * @return 加密后的字节数组（Base64 编码）
     * @throws Exception 加密失败时抛出异常
     */
    public static byte[] encrypt(String data) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        SecretKey secretKey = new SecretKeySpec(key, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);

        byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encode(encrypted);
    }

    /**
     * 解密字节数据
     *
     * @param encryptedData 加密后的字节数组（Base64 编码）
     * @return 解密后的字符串
     * @throws Exception 解密失败时抛出异常
     */
    public static String decrypt(byte[] encryptedData) throws Exception {
        byte[] decodedData = Base64.getDecoder().decode(encryptedData);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        SecretKey secretKey = new SecretKeySpec(key, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);

        byte[] decrypted = cipher.doFinal(decodedData);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    /**
     * 加密并保存到文件
     *
     * @param filePath 文件路径
     * @param data     要加密保存的数据
     * @throws Exception 保存失败时抛出异常
     */
    public static void encryptAndSave(String filePath, String data) throws Exception {
        byte[] encrypted = encrypt(data);
        java.nio.file.Path path = java.nio.file.Paths.get(filePath);

        // 确保父目录存在
        java.nio.file.Path parentDir = path.getParent();
        if (parentDir != null && !java.nio.file.Files.exists(parentDir)) {
            java.nio.file.Files.createDirectories(parentDir);
        }

        java.nio.file.Files.write(path, encrypted);
    }

    /**
     * 从文件读取并解密
     *
     * @param filePath 文件路径
     * @return 解密后的字符串
     * @throws Exception 读取或解密失败时抛出异常
     */
    public static String loadAndDecrypt(String filePath) throws Exception {
        byte[] encryptedData = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(filePath));
        return decrypt(encryptedData);
    }
}
