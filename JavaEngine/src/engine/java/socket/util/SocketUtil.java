package engine.java.socket.util;

import engine.java.util.secure.CRCUtil;
import engine.java.util.secure.Obfuscate;

public final class SocketUtil {

    private static byte[] crypt_key;                                // 数据密钥

    /**
     * 握手
     * 
     * @param bs 握手信息
     * @return CRC校验值
     */
    public static int handshake(byte[] bs) {
        // 解密握手信息
        crypt_key = Obfuscate.clarify(bs);
        return CRCUtil.calculate(crypt_key);
    }

    /**
     * 数据处理
     */
    public static void crypt(byte[] data) {
        if (crypt_key != null && data != null)
        {
            for (int i = 0; i < data.length; i++)
            {
                data[i] = (byte) (data[i] ^ crypt_key[i % crypt_key.length]);
            }
        }
    }
}