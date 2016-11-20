package protocol.java;

import engine.java.util.io.ByteDataUtil;
import engine.java.util.io.ByteDataUtil.ByteData;
import engine.java.util.secure.CryptoUtil;
import engine.java.util.secure.ZipUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 网络通信通用协议<p>
 * 功能：适用于多平台网络交互
 * 
 * @author Daimon
 * @version N
 * @since 6/6/2014
 */

public final class ProtocolWrapper {
    
    private static final int HEADER_LENGTH = 13;
    
    // 通讯协议数据包加解密的密钥
    private static byte[] protocolEncryptKey;
    
    public static void setEncryptSecret(byte[] key) {
        protocolEncryptKey = key;
    }

    private static byte[] encrypt(byte[] key, byte[] data) {
        return CryptoUtil.AES_encrypt(key, data);
    }

    private static byte[] decrypt(byte[] key, byte[] data) {
        return CryptoUtil.AES_decrypt(key, data);
    }
    
    public static byte[] wrap(ProtocolEntity entity) throws Exception {
        if (entity.body == null)
        {
            throw new Exception("Please call function ProtocolEntiry.generateBody() before.");
        }
        
        // Header
        byte[] header = new byte[HEADER_LENGTH];
        
        int offset = 0;
        ByteDataUtil.intToBytes_HL(entity.packageSize, header, offset);
        offset += 4;    // 4位，包的长度
        ByteDataUtil.intToBytes_HL(entity.cmd, header, offset);
        offset += 4;    // 4位，推送指令码
        ByteDataUtil.intToBytes_HL(entity.msgId, header, offset);
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
        int packageSize = ByteDataUtil.bytesToInt_HL(header, offset);
        offset += 4;    // 4位，包的长度
        int cmd = ByteDataUtil.bytesToInt_HL(header, offset);
        offset += 4;    // 4位，推送指令码
        int msgId = ByteDataUtil.bytesToInt_HL(header, offset);
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

    private static boolean readStream(InputStream is, byte[] buffer)
            throws IOException {
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
    
    public static final class ProtocolEntity {
        
        int packageSize;
        
        // 每条信令的唯一标示符，当发消息时对方收到了，
        // 再重复使用msgId重发消息，该消息收不到回包，所有msgId不可复用
        int msgId;

        // 指令码
        int cmd;
        
        // 加密压缩标志
        int flag;
        
        byte[] body;
        
        private ByteData data;
        
        private ProtocolEntity() {}
        
        public static ProtocolEntity newInstance(int msgId, int cmd, ByteData data) {
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

        public ByteData getData() {
            if (data == null)
            {
                throw new RuntimeException("Please call function ProtocolEntiry.parseBody() before.");
            }
            
            return data;
        }
        
        public void generateBody() throws Exception {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            try {
                dos.writeUTF(data.getClass().getName());
                data.write(dos);
                
                byte[] body = baos.toByteArray();
                if (body.length > 512)
                {
                    // 压缩
                    body = ZipUtil.gzip(body);
                    flag |= 0x01;
                }

                if (protocolEncryptKey != null)
                {
                    // 加密
                    body = encrypt(protocolEncryptKey, body);
                    flag |= 0x02;
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
                body = decrypt(protocolEncryptKey, body);
            }
            
            if ((flag & 0x01) != 0)
            {
                // 解压缩
                body = ZipUtil.ungzip(body);
            }
            
            DataInputStream dis = new DataInputStream(
                    new ByteArrayInputStream(body));
            try {
                Class<? extends ByteData> cls = Class.forName(dis.readUTF())
                        .asSubclass(ByteData.class);
                data = cls.newInstance();
                data.read(dis);
            } finally {
                dis.close();
            }
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (data != null)
            {
                sb.append(data.getClass().getSimpleName());
            }
            
            sb
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