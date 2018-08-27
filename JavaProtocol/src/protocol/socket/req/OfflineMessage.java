package protocol.socket.req;

import protocol.util.ProtocolWrapper.ProtocolEntity.ProtocolData;
import protocol.util.ProtocolWrapper.ProtocolEntity.ProtocolDataInputStream;
import protocol.util.ProtocolWrapper.ProtocolEntity.ProtocolDataOutputStream;

import java.io.IOException;

/**
 * 拉取离线消息
 */
public class OfflineMessage implements ProtocolData {
    
    public long timestamp;                  // 最新消息时间戳

    @Override
    public void write(ProtocolDataOutputStream dos) throws IOException {
        dos.writeLong(timestamp);
    }

    @Override
    public void read(ProtocolDataInputStream dis) throws IOException {
        timestamp = dis.readLong();
    }
}