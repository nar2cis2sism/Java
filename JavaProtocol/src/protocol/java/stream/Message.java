package protocol.java.stream;

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
        dos.writeUTF(from);
        dos.writeUTF(to);
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
        from = dis.readUTF();
        to = dis.readUTF();
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
}