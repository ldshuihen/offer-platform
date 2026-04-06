package com.xi.wx.utils;

import lombok.extern.slf4j.Slf4j;

import java.security.MessageDigest;
import java.util.Arrays;

/**
 * sha1生成签名工具
 *
 * @author: ChickenWing
 * @date: 2023/11/5
 *
 * ✅ 核心作用：
 * 根据微信官方算法，生成用于身份验证的 SHA1 签名，主要用于：
 * 服务器配置时的 URL 验证（GET 请求）
 * 接收加密消息时的签名校验（POST 请求，若启用安全模式）
 *
 * 📌 微信签名规则（来自官方文档）：
 * 将 token、timestamp、nonce 三个参数进行字典序排序 → 拼接成字符串 → 对字符串进行 SHA1 加密 → 获得签名。
 * 💡 注意：在明文模式下，encrypt 参数为空字符串 ""；只有在加密模式下才传入 msg_encrypt。
 */
@Slf4j
public class SHA1 {

    /**
     * 用SHA1算法生成安全签名
     *
     * @param token     票据
     * @param timestamp 时间戳
     * @param nonce     随机字符串
     * @param encrypt   密文
     * @return 安全签名
     */
    public static String getSHA1(String token, String timestamp, String nonce, String encrypt) {
        try {
            String[] array = new String[]{token, timestamp, nonce, encrypt};
            StringBuffer sb = new StringBuffer();
            // 字符串排序
            Arrays.sort(array);
            for (int i = 0; i < 4; i++) {
                sb.append(array[i]);
            }
            String str = sb.toString();
            // SHA1签名生成
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(str.getBytes());
            byte[] digest = md.digest();

            StringBuffer hexStr = new StringBuffer();
            String shaHex = "";
            for (int i = 0; i < digest.length; i++) {
                shaHex = Integer.toHexString(digest[i] & 0xFF);
                if (shaHex.length() < 2) {
                    hexStr.append(0);
                }
                hexStr.append(shaHex);
            }
            return hexStr.toString();
        } catch (Exception e) {
            log.error("sha加密生成签名失败:", e);
            return null;
        }
    }
}
