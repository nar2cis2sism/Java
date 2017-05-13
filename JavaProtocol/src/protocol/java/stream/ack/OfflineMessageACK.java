package protocol.java.stream.ack;

import protocol.java.ProtocolWrapper.ProtocolEntity.ProtocolData;
import protocol.java.stream.Message;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * 离线消息应答
 */
public class OfflineMessageACK implements ProtocolData {
    
    public Message[] message;

    @Override
    public void write(DataOutputStream dos) throws IOException {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void read(DataInputStream dis) throws IOException {
        // TODO Auto-generated method stub
        
    }
}