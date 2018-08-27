package protocol.socket;

import protocol.util.ProtocolWrapper.ProtocolEntity.ProtocolData;
import protocol.util.ProtocolWrapper.ProtocolEntity.ProtocolDataInputStream;
import protocol.util.ProtocolWrapper.ProtocolEntity.ProtocolDataOutputStream;

import java.io.IOException;

/**
 * 空数据包
 */
public class SimpleData implements ProtocolData {

    @Override
    public void write(ProtocolDataOutputStream dos) throws IOException {}

    @Override
    public void read(ProtocolDataInputStream dis) throws IOException {}
}