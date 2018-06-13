package protocol.java.stream;

import protocol.java.ProtocolWrapper.ProtocolEntity.ProtocolData;
import protocol.java.ProtocolWrapper.ProtocolEntity.ProtocolDataInputStream;
import protocol.java.ProtocolWrapper.ProtocolEntity.ProtocolDataOutputStream;

import java.io.IOException;

/**
 * 返回给客户端的错误信息
 */
public class ErrorInfo extends protocol.java.json.ErrorInfo implements ProtocolData {
    
    public ErrorInfo(int code) {
        super(code);
    }

    @Override
    public void write(ProtocolDataOutputStream dos) throws IOException {
        dos.writeInt(code);
        dos.writeString(msg);
    }

    @Override
    public void read(ProtocolDataInputStream dis) throws IOException {
        code = dis.readInt();
        msg = dis.readString();
    }
}