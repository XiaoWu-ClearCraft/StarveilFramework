package com.xiaowu.game.starveil.infrastructure.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据文件加密测试。
 *
 * <p>重点：默认密钥是 {@code Starveil_DataKey}（正好 16 字节），
 * 内容可以换成自己的一套；长度不合法时保持原值而不是崩掉。
 */
class FileCryptoTest {

    @BeforeEach
    @AfterEach
    void reset() {
        FileCrypto.resetForTest();
    }

    @Test
    void defaultKeyIsExactlySixteenBytes() {
        assertEquals(16, FileCrypto.DEFAULT_KEY.getBytes(StandardCharsets.UTF_8).length,
                "AES-128 的密钥必须正好 16 字节");
        assertEquals("Starveil_DataKey", FileCrypto.DEFAULT_KEY);
        assertEquals(16, FileCrypto.KEY_LENGTH);
    }

    @Test
    void defaultIvIsTheHistoricalValue() {
        assertEquals("1234567890123456", FileCrypto.DEFAULT_IV);
        assertEquals(16, FileCrypto.IV_LENGTH);
    }

    @Test
    void roundTripWithDefaultKey() throws Exception {
        String plain = "{\"starveil:cant_exit\":\"true\"}";
        byte[] encrypted = FileCrypto.encrypt(plain);
        assertEquals(plain, FileCrypto.decrypt(encrypted));
    }

    @Test
    void contentCanReplaceTheKey() throws Exception {
        // 先用默认密钥加密一份数据
        byte[] oldData = FileCrypto.encrypt("秘密");
        assertEquals("秘密", FileCrypto.decrypt(oldData));

        // 换成内容自己的密钥
        assertTrue(FileCrypto.configure("MyGame_SecretKey", null));
        assertEquals(16, FileCrypto.keyLength());

        // 老数据解不开了 —— 这正是「不同游戏的数据互不可读」的效果
        assertThrows(Exception.class, () -> FileCrypto.decrypt(oldData),
                "换了密钥就不该还能解开旧数据");

        String plain = "新密钥下的数据";
        assertEquals(plain, FileCrypto.decrypt(FileCrypto.encrypt(plain)));
    }

    @Test
    void contentCanReplaceBothKeyAndIv() throws Exception {
        assertTrue(FileCrypto.configure("MyGame_SecretKey", "MyGame_InitVec16"));
        assertEquals(16, FileCrypto.keyLength());
        assertEquals(16, FileCrypto.ivLength());

        String plain = "内容自己的密钥";
        assertEquals(plain, FileCrypto.decrypt(FileCrypto.encrypt(plain)));
    }

    @Test
    void wrongLengthKeyIsRejectedButDoesNotThrow() {
        assertFalse(FileCrypto.configure("short", null), "长度不对要返回失败");
        assertEquals(16, FileCrypto.keyLength(), "失败时保持默认密钥不变");

        assertFalse(FileCrypto.configure("ThisKeyIsWayTooLongToBeAes128", null));
        assertEquals(16, FileCrypto.keyLength());
    }

    @Test
    void wrongLengthIvIsRejected() {
        assertFalse(FileCrypto.configure(null, "short"));
        assertEquals(16, FileCrypto.ivLength());
    }

    @Test
    void nullOrEmptyMeansKeepCurrent() {
        assertTrue(FileCrypto.configure("MyGame_SecretKey", null));
        // 再传 null / 空串表示「不改这一项」
        assertTrue(FileCrypto.configure(null, null));
        assertTrue(FileCrypto.configure("", ""));
        assertEquals(16, FileCrypto.keyLength());
    }

    @Test
    void contentConfigExposesTheSameEntryPoint() throws Exception {
        assertTrue(com.xiaowu.game.starveil.infrastructure.ContentConfig
                .setDataCryptoKey("Content_OwnKey16", null));
        String plain = "经由 ContentConfig 配置后的数据";
        assertEquals(plain, FileCrypto.decrypt(FileCrypto.encrypt(plain)));
    }

    @Test
    void fileRoundTripThroughDisk() throws Exception {
        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("starveil-crypto", ".dat");
        try {
            String plain = "{\"starveil:player_name\":\"霁雾\"}";
            FileCrypto.encryptAndSave(tmp.toString(), plain);
            assertEquals(plain, FileCrypto.loadAndDecrypt(tmp.toString()));
        } finally {
            java.nio.file.Files.deleteIfExists(tmp);
        }
    }
}
