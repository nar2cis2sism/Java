package protocol.java;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * 辅助类<p>
 * 功能：针对特殊数据进行转换
 * 
 * @author Daimon
 * @version N
 * @since 6/6/2014
 */
public final class ProtocolHelper {
    
    public static void writeUTF(DataOutputStream dos, String str) throws IOException {
        if (str == null)
        {
            dos.writeBoolean(false);
        }
        else
        {
            dos.writeBoolean(true);
            dos.writeUTF(str);
        }
    }
    
    public static String readUTF(DataInputStream dis) throws IOException {
        if (dis.readBoolean())
        {
            return dis.readUTF();
        }
        
        return null;
    }
}