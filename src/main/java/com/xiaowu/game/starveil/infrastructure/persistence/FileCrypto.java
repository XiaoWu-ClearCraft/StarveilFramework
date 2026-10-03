package com.xiaowu.game.starveil.infrastructure.persistence;

import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec;

/**
 * 文件加密/解密工具类
 * 用于统一管理游戏数据的加密存储
 */
public class FileCrypto {
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final byte[] KEY = "XiaoLove_DataKey".getBytes();
    private static final byte[] IV = "1234567890123456".getBytes();

    /**
     * 加密字符串数据
     * @param data 要加密的字符串
     * @return 加密后的字节数组（Base64编码）
     * @throws Exception 加密失败时抛出异常
     */
    public static byte[] encrypt(String data) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        SecretKey secretKey = new SecretKeySpec(KEY, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(IV);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);

        byte[] encrypted = cipher.doFinal(data.getBytes());
        return Base64.getEncoder().encode(encrypted);
    }

    /**
     * 解密字节数据
     * @param encryptedData 加密后的字节数组（Base64编码）
     * @return 解密后的字符串
     * @throws Exception 解密失败时抛出异常
     */
    public static String decrypt(byte[] encryptedData) throws Exception {
        byte[] decodedData = Base64.getDecoder().decode(encryptedData);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        SecretKey secretKey = new SecretKeySpec(KEY, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(IV);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);

        byte[] decrypted = cipher.doFinal(decodedData);
        return new String(decrypted);
    }

    /**
     * 加密并保存到文件
     * @param filePath 文件路径
     * @param data 要加密保存的数据
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
     * @param filePath 文件路径
     * @return 解密后的字符串
     * @throws Exception 读取或解密失败时抛出异常
     */
    public static String loadAndDecrypt(String filePath) throws Exception {
        byte[] encryptedData = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(filePath));
        return decrypt(encryptedData);
    }
}