package protocol.socket.ack;

import protocol.socket.req.Message;
import protocol.util.ProtocolWrapper.ProtocolEntity.ProtocolData;
import protocol.util.ProtocolWrapper.ProtocolEntity.ProtocolDataInputStream;
import protocol.util.ProtocolWrapper.ProtocolEntity.ProtocolDataOutputStream;

import java.io.IOException;

/**
 * 离线消息应答
 */
public class OfflineMessageACK implements ProtocolData {
    
    public Message[] message;

    @Override
    public void write(ProtocolDataOutputStream dos) throws IOException {
        int num = message != null ? message.length : 0;
        dos.writeInt(num);
        if (num > 0)
        {
            for (Message msg : message)
            {
                msg.write(dos);
            }
        }
    }

    @Override
    public void read(ProtocolDataInputStream dis) throws IOException {
        int num = dis.readInt();
        if (num > 0)
        {
            message = new Message[num];
            for (int i = 0; i < num; i++)
            {
                (message[i] = new Message()).read(dis);
            }
        }
    }
}