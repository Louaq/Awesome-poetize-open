import CryptoJS from 'crypto-js';
import constant from "./constant";

/**
 * 加密工具类
 */
class CryptoUtil {
  constructor() {
    // 使用项目中定义的密钥
    this.key = constant.cryptojs_key;
  }

  /**
   * AES加密 - 使用ECB模式，与后端保持一致
   * @param {string} data 待加密的数据
   * @returns {string} 加密后的字符串
   */
  encrypt(data) {
    try {
      const text = JSON.stringify(data);
      const encrypted = CryptoJS.AES.encrypt(text, CryptoJS.enc.Utf8.parse(this.key), {
        mode: CryptoJS.mode.ECB,
        padding: CryptoJS.pad.Pkcs7
      });
      return encrypted.toString();
    } catch (error) {
      console.error('加密失败:', error);
      return null;
    }
  }

  /**
   * AES解密 - 使用ECB模式，与后端保持一致
   * @param {string} encryptedData 加密的数据
   * @returns {object} 解密后的对象
   */
  decrypt(encryptedData) {
    try {
      const decrypted = CryptoJS.AES.decrypt(encryptedData, CryptoJS.enc.Utf8.parse(this.key), {
        mode: CryptoJS.mode.ECB,
        padding: CryptoJS.pad.Pkcs7
      });
      const decryptedText = decrypted.toString(CryptoJS.enc.Utf8);
      return JSON.parse(decryptedText);
    } catch (error) {
      console.error('解密失败:', error);
      return null;
    }
  }

  /**
   * AES解密Base64 - 专门用于解密后端使用Base64编码的加密数据
   * @param {string} encryptedData Base64编码的加密数据
   * @returns {object} 解密后的对象
   */
  decryptBase64(encryptedData) {
    try {
      // 直接解密Base64编码的数据
      const decrypted = CryptoJS.AES.decrypt(encryptedData, CryptoJS.enc.Utf8.parse(this.key), {
        mode: CryptoJS.mode.ECB,
        padding: CryptoJS.pad.Pkcs7
      });
      const decryptedText = decrypted.toString(CryptoJS.enc.Utf8);
      return JSON.parse(decryptedText);
    } catch (error) {
      console.error('Base64解密失败:', error);
      return null;
    }
  }

  /**
   * 生成随机密钥对
   * @returns {object} 包含公钥和私钥的对象
   */
  generateKeyPair() {
    // 简化实现，实际项目中应使用更安全的密钥生成方式
    const timestamp = new Date().getTime();
    const random = Math.random().toString(36).substring(2);
    return {
      publicKey: `pub_${timestamp}_${random}`,
      privateKey: `priv_${timestamp}_${random}`
    };
  }
}

// 创建单例实例
const cryptoUtil = new CryptoUtil();

export default cryptoUtil;