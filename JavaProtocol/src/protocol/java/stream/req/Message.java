package protocol.java.stream.req;

import static protocol.java.ProtocolHelper.writeUTF;
import static protocol.java.ProtocolHelper.readUTF;

import protocol.java.ProtocolWrapper.ProtocolEntity.ProtocolData;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * 聊天消息
 */
public class Message implements ProtocolData {
    
    public String from;                     // 发送方账号
    public String to;                       // 接收方账号
    public MessageBody[] body;              // 消息体

    @Override
    public void write(DataOutputStream dos) throws IOException {
        writeUTF(dos, from);
        writeUTF(dos, to);
        int num = body == null ? 0 : body.length;
        dos.writeInt(num);
        if (num > 0)
        {
            for (MessageBody body : this.body)
            {
                body.write(dos);
            }
        }
    }

    @Override
    public void read(DataInputStream dis) throws IOException {
        from = readUTF(dis);
        to = readUTF(dis);
        int num = dis.readInt();
        if (num > 0)
        {
            body = new MessageBody[num];
            for (int i = 0; i < num; i++)
            {
                (body[i] = new MessageBody()).read(dis);
            }
        }
    }
    
    public static class MessageBody implements ProtocolData {
        
        public String id;                       // 消息ID，用于排重
        
        public String content;                  // 消息内容
        
        /**
         * 0：文本
         * 1：单图
         * 2：多图
         * 3：音频
         * 4：视频
         * 5：位置
         * 6：名片
         */
        public int type;                        // 消息类型
        
        /**
         * 0：二人会话
         * 1：群组消息
         * 2：系统消息
         * 3：公众平台
         */
        public int event;                       // 消息事件
        
        public long creationTime;               // 消息创建时间（客户端不用设置）

        @Override
        public void write(DataOutputStream dos) throws IOException {
            writeUTF(dos, id);
            writeUTF(dos, content);
            dos.writeInt(type);
            dos.writeInt(event);
            dos.writeLong(creationTime);
        }

        @Override
        public void read(DataInputStream dis) throws IOException {
            id = readUTF(dis);
            content = readUTF(dis);
            type = dis.readInt();
            event = dis.readInt();
            creationTime = dis.readLong();
        }
    }
}