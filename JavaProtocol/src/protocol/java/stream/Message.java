package protocol.java.stream;

import engine.java.util.io.ByteDataUtil.ByteData;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * 聊天消息
 */
public class Message implements ByteData {

    public static final int CMD = Message.class.hashCode();
    
    public long from;                       // 发送方uid
    public long to;                         // 接收方uid
    public String content;                  // 消息内容

    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeLong(from);
        dos.writeLong(to);
        dos.writeUTF(content);
    }

    @Override
    public void read(DataInputStream dis) throws IOException {
        from = dis.readLong();
        to = dis.readLong();
        content = dis.readUTF();
    }
}