package protocol.util;

import static protocol.util.ProtocolUtil.bytesToInt;
import static protocol.util.ProtocolUtil.intToBytes;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * 网络通信通用协议<p>
 * 功能：适用于多平台网络交互
 * 
 * @author Daimon
 * @since 6/6/2014
 */
public final class ProtocolWrapper {
    
    private static final int HEADER_LENGTH = 13;
    
    // 通讯协议数据包加解密的密钥
    private static byte[] protocolEncryptKey;
    
    public static void setEncryptSecret(byte[] key) {
        protocolEncryptKey = key;
    }
    
    public static byte[] wrap(ProtocolEntity entity) throws IOException {
        if (entity.body == null)
        {
            throw new IOException("Please call ProtocolEntiry.generateBody() before.");
        }
        
        // Header
        byte[] header = new byte[HEADER_LENGTH];
        
        int offset = 0;
        intToBytes(entity.packageSize, header, offset);
        offset += 4;    // 4位，包的大小
        intToBytes(entity.cmd, header, offset);
        offset += 4;    // 4位，指令码
        intToBytes(entity.msgId, header, offset);
        offset += 4;    // 4位，信令id
        header[offset] = (byte) entity.flag;
        offset++;       // 1位，加密压缩标志，0x01压缩，0x02加密

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(header);
        // Body
        baos.write(entity.body);
        return baos.toByteArray();
    }

    public static ProtocolEntity parse(InputStream is) throws IOException {
        // Header
        byte[] header = new byte[HEADER_LENGTH];
        if (!readStream(is, header))
        {
            return null;
        }
        
        int offset = 0;
        int packageSize = bytesToInt(header, offset);
        offset += 4;    // 4位，包的大小
        int cmd = bytesToInt(header, offset);
        offset += 4;    // 4位，指令码
        int msgId = bytesToInt(header, offset);
        offset += 4;    // 4位，信令id
        int flag = header[offset] & 0xff;
        offset++;       // 1位，加密压缩标志，0x01压缩，0x02加密
        
        // Body
        byte[] body = new byte[packageSize - offset];
        if (!readStream(is, body))
        {
            return null;
        }
        
        ProtocolEntity entity = new ProtocolEntity();
        entity.packageSize = packageSize;
        entity.msgId = msgId;
        entity.cmd = cmd;
        entity.flag = flag;
        entity.body = body;
        
        return entity;
    }

    private static boolean readStream(InputStream is, byte[] buffer) throws IOException {
        int length = buffer.length;
        int index = 0;
        int len = 0;
        while (index != length 
           && (len = is.read(buffer, index, length - index)) != -1)
        {
            index += len;
        }
        
        return len != -1;
    }
    
    /**
     * 信令实体类
     */
    public static final class ProtocolEntity {
        
        /**
         * 数据流传输扩展，使用{@link #writeString(String)}代替{@link #writeUTF(String)}
         */
        public static final class ProtocolDataOutputStream extends DataOutputStream {

            public ProtocolDataOutputStream(OutputStream out) {
                super(out);
            }
            
            /**
             * Writes a string to the underlying output stream, consider it is null value.
             */
            public void writeString(String str) throws IOException {
                if (str == null)
                {
                    writeBoolean(false);
                }
                else
                {
                    writeBoolean(true);
                    writeUTF(str);
                }
            }
        }

        /**
         * 数据流传输扩展，使用{@link #readString()}代替{@link #readUTF()}
         */
        public static final class ProtocolDataInputStream extends DataInputStream {

            public ProtocolDataInputStream(InputStream in) {
                super(in);
            }
            
            /**
             * Reads a string from the contained input stream, may be null value.
             */
            public String readString() throws IOException {
                if (readBoolean())
                {
                    return readUTF();
                }
                
                return null;
            }
        }

        /**
         * 用于协议传输的数据接口
         */
        public interface ProtocolData {

            void write(ProtocolDataOutputStream dos) throws IOException;

            void read(ProtocolDataInputStream dis) throws IOException;
        }
        
        // 信令包大小（包含信令头）
        int packageSize;
        
        // 每条信令的唯一标识符，不可复用，0表示推送消息
        int msgId;

        // 指令码
        int cmd;
        
        // 加密压缩标志
        int flag;
        
        // 数据体
        byte[] body;
        ProtocolData data;
        
        ProtocolEntity() {}
        
        public static ProtocolEntity newInstance(int msgId, ProtocolData data) {
            return newInstance(msgId, data.getClass().getName().hashCode(), data);
        }
        
        public static ProtocolEntity newInstance(int msgId, int cmd, ProtocolData data) {
            ProtocolEntity entity = new ProtocolEntity();
            entity.msgId = msgId;
            entity.cmd = cmd;
            entity.data = data;
            return entity;
        }
        
        public int getMsgId() {
            return msgId;
        }
        
        public int getCmd() {
            return cmd;
        }

        public ProtocolData getData() {
            if (data == null)
            {
                throw new RuntimeException("Please call ProtocolEntiry.parseBody() before.");
            }
            
            return data;
        }
        
        public void generateBody() throws Exception {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ProtocolDataOutputStream dos = new ProtocolDataOutputStream(baos);
            try {
                dos.writeUTF(data.getClass().getName());
                data.write(dos);
                
                byte[] body = baos.toByteArray();
                if (body.length > ProtocolUtil.DEFAULT_COMPRESS_SIZE)
                {
                    // 压缩
                    body = ProtocolUtil.gzip(body);
                    flag |= 0x01;
                }

                if (protocolEncryptKey != null)
                {
                    // 加密
                    try {
                        body = ProtocolUtil.encrypt(protocolEncryptKey, body);
                        flag |= 0x02;
                    } catch (Exception e) {
                        // Keep origin data.
                        e.printStackTrace();
                    }
                }
                
                packageSize = (this.body = body).length + HEADER_LENGTH;
            } finally {
                dos.close();
            }
        }

        public void parseBody() throws Exception {
            byte[] body = this.body;
            if ((flag & 0x02) != 0)
            {
                // 解密
                body = ProtocolUtil.decrypt(protocolEncryptKey, body);
            }
            
            if ((flag & 0x01) != 0)
            {
                // 解压缩
                body = ProtocolUtil.ungzip(body);
            }
            
            ProtocolDataInputStream dis = new ProtocolDataInputStream(new ByteArrayInputStream(body));
            try {
                Class<? extends ProtocolData> cls = Class.forName(dis.readUTF())
                        .asSubclass(ProtocolData.class);
                (data = cls.newInstance()).read(dis);
            } finally {
                dis.close();
            }
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder()
            .append("[packageSize=")
            .append(packageSize)
            .append(", cmd=")
            .append(cmd)
            .append(", msgId=")
            .append(msgId)
            .append(", flag=")
            .append(flag)
            .append("]");
            
            return sb.toString();
        }
    }
}

final class ProtocolUtil {

    public static final int DEFAULT_COMPRESS_SIZE = 512;

    /**
     * 压缩数据
     */
    public static byte[] gzip(byte[] content) throws IOException {
        GZIPOutputStream zos = null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            zos = new GZIPOutputStream(baos);
            zos.write(content);
            zos.finish();
            return baos.toByteArray();
        } finally {
            if (zos != null)
            {
                zos.close();
            }
        }
    }

    /**
     * 解压缩数据
     */
    public static byte[] ungzip(byte[] content) throws IOException {
        GZIPInputStream zis = null;
        try {
            zis = new GZIPInputStream(new ByteArrayInputStream(content));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[DEFAULT_COMPRESS_SIZE * 2];
            int n;
            while ((n = zis.read(buffer)) > 0)
            {
                baos.write(buffer, 0, n);
            }
            
            return baos.toByteArray();
        } finally {
            if (zis != null)
            {
                zis.close();
            }
        }
    }

    public static byte[] encrypt(byte[] key, byte[] data) throws Exception {
        return crypto(key, data, Cipher.ENCRYPT_MODE);
    }

    public static byte[] decrypt(byte[] key, byte[] data) throws Exception {
        return crypto(key, data, Cipher.DECRYPT_MODE);
    }
    
    private static byte[] crypto(byte[] key, byte[] data, int cipherMode) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(cipherMode, new SecretKeySpec(key, "AES"));
        return cipher.doFinal(data);
    }

    public static void intToBytes(int i, byte[] bs, int offset) {
        bs[offset] = (byte) (i >> 24);
        bs[offset + 1] = (byte) (i >> 16);
        bs[offset + 2] = (byte) (i >> 8);
        bs[offset + 3] = (byte) i;
    }

    public static int bytesToInt(byte[] bs, int offset) {
        return (bs[offset + 3] & 0xff)
            | ((bs[offset + 2] & 0xff) << 8)
            | ((bs[offset + 1] & 0xff) << 16)
            | ((bs[offset] & 0xff) << 24);
    }
}