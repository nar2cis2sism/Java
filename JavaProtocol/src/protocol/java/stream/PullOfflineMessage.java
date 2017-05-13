package protocol.java.stream;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import protocol.java.ProtocolWrapper.ProtocolEntity.ProtocolData;

/**
 * 拉取离线消息
 */
public class PullOfflineMessage implements ProtocolData {
    
    public String account;          // 用户账号

    @Override
    public void write(DataOutputStream dos) throws IOException {
        dos.writeUTF(account);
    }

    @Override
    public void read(DataInputStream dis) throws IOException {
        account = dis.readUTF();
    }
}