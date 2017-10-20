package protocol.java.stream;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import protocol.java.ProtocolWrapper.ProtocolEntity.ProtocolData;

/**
 * 消息体
 */
public class MessageBody implements ProtocolData {
    
    public String id;               // 消息ID，用于排重
    
    public String content;          // 消息内容
    
    /**
     * 0：文本
     * 1：单图
     * 2：多图
     * 3：音频
     * 4：视频
     * 5：位置
     * 6：名片
     */
    public int type;                // 消息类型
    
    /**
     * 0：二人会话
     * 1：群组消息
     * 2：系统消息
     * 3：公众平台
     */
    public int event;               // 消息事件
    
    public long creationTime;       // 消息创建时间（客户端不用设置）

    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeUTF(id);
        dos.writeUTF(content);
        dos.writeInt(type);
        dos.writeInt(event);
        dos.writeLong(creationTime);
    }

    @Override
    public void read(DataInputStream dis) throws IOException {
        id = dis.readUTF();
        content = dis.readUTF();
        type = dis.readInt();
        event = dis.readInt();
        creationTime = dis.readLong();
    }
}