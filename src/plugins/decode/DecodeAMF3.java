package plugins.decode;

import flex.messaging.io.SerializationContext;
import flex.messaging.io.amf.Amf3Input;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.Base64;

public class DecodeAMF3 {

    public String decodeAMF3(String base64Amf3String) {
        try {
            // 1. 将传入的 Base64 字符串还原为 AMF3 的二进制字节数组
            byte[] amf3Bytes = Base64.getDecoder().decode(base64Amf3String);

            // 2. 将字节数组包装为 DataInputStream
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(amf3Bytes));

            // 3. 初始化 BlazeDS 的序列化上下文和 Amf3Input
            SerializationContext context = new SerializationContext();
            Amf3Input amf3Input = new Amf3Input(context);
            amf3Input.setInputStream(dis);

            // 4. 读取并解析 AMF3 数据
            Object decodedObject = amf3Input.readObject();

            // 5. 将解析后的对象转为字符串返回（实际业务中可根据需要强转为具体类型）
            return "解码成功: " + (decodedObject != null ? decodedObject.toString() : "null");

        } catch (IllegalArgumentException e) {
            // 捕获 Base64 格式错误
            e.printStackTrace();
            return "Base64解码失败: 包含非法字符 -> " + e.getMessage();
        } catch (Exception e) {
            // 捕获 AMF3 解析错误（如数据截断、格式不匹配等）
            e.printStackTrace();
            return "AMF3解码失败: " + e.getMessage();
        }
    }
}