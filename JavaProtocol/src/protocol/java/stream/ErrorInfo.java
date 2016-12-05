package protocol.java.stream;

import protocol.java.ProtocolWrapper.ProtocolEntity.ProtocolData;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * 推送给客户端的错误信息
 */
public class ErrorInfo extends protocol.java.json.ErrorInfo implements ProtocolData {

    public static final int CMD = ErrorInfo.class.hashCode();
    
    public ErrorInfo(int code) {
        super(code);
    }

    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeInt(code);
        dos.writeUTF(msg);
    }

    @Override
    public void read(DataInputStream dis) throws IOException {
        code = dis.readInt();
        msg = dis.readUTF();
    }
}