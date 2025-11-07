package com.ld.poetry.utils;

import com.ld.poetry.constants.CommonConst;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 加密解密工具类
 */
public class CryptoUtil {
    
    // 使用统一密钥，从CommonConst获取
    private static final String KEY = CommonConst.CRYPOTJS_KEY;
    
    /**
     * AES加密 - 使用ECB模式
     * @param data 待加密的数据
     * @return 加密后的字符串
     */
    public static String encrypt(String data) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "AES");
            
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            
            byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * AES解密 - 使用ECB模式
     * @param encryptedData 加密的数据
     * @return 解密后的字符串
     */
    public static String decrypt(String encryptedData) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "AES");
            
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            
            byte[] decoded = Base64.getDecoder().decode(encryptedData);
            byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 将Map转换为加密的响应格式
     * @param data 待加密的数据
     * @return 包含加密数据的Map
     */
    public static Map<String, Object> encryptResponse(Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();
        response.put("encrypted", encrypt(JsonUtils.toJsonString(data)));
        return response;
    }
    
    /**
     * 解密请求中的数据
     * @param encryptedData 加密的数据
     * @return 解密后的Map
     */
    public static Map<String, Object> decryptRequest(String encryptedData) {
        String decrypted = decrypt(encryptedData);
        if (decrypted != null) {
            return JsonUtils.parseObject(decrypted, Map.class);
        }
        return null;
    }
}