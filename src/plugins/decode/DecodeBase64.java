package plugins.decode;

import java.util.Base64;
import java.nio.charset.StandardCharsets;

public class DecodeBase64 {
    public String decodeBase64(String decode_string) {
        try {
            // 1. 执行解码
            byte[] decodedBytes = Base64.getDecoder().decode(decode_string);
            return new String(decodedBytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // 2. 捕获非法参数异常，打印堆栈并返回错误提示
            e.printStackTrace();
            return "解码失败: 包含非法Base64字符 -> " + e.getMessage();
        }
    }
}
