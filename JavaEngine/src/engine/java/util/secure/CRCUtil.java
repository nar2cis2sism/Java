package engine.java.util.secure;

/**
 * CRC校验生成工具
 * 
 * @author Daimon
 * @since 8/12/2010
 */
public final class CRCUtil {

    private static final int[] CRC16table = { 0xf1c0, 0xf248, 0x553e, 0xae68, 0xc753, 0x6269, 0x9a19,
            0x7fed, 0xb010, 0x4d44, 0x6d07, 0x9ec0, 0x578c, 0xbb57, 0x07f1, 0x3d1f, 0x6944, 0x1f29,
            0x014d, 0xce4a, 0x08b5, 0x6f29, 0xdb33, 0x0c96, 0x1e8b, 0x2045, 0x90b0, 0x676f, 0xb3c1,
            0x9316, 0xcc1f, 0x8e54, 0xc1ea, 0x65a2, 0xa28b, 0xe271, 0x5801, 0x9c97, 0x636e, 0x31f1,
            0xc563, 0x06cb, 0x1145, 0xac9b, 0x38ed, 0xeadc, 0xbecb, 0xc577, 0xf853, 0x49f0, 0x25e6,
            0x7cbf, 0x9424, 0xd1b9, 0xa882, 0x71b2, 0x571b, 0x45f9, 0x58ed, 0x4545, 0x33b1, 0xd356,
            0xf677, 0xd606, 0xb103, 0x10bb, 0xcc60, 0x53ef, 0x608f, 0x5bcb, 0x6458, 0xa920, 0x485a,
            0xb492, 0xd323, 0x5cc6, 0xf96f, 0x9c72, 0x5d16, 0x3655, 0xd0e1, 0x89c5, 0x198d, 0xe965,
            0xb1b7, 0x1c39, 0x6790, 0x44f9, 0x7069, 0x103f, 0x4338, 0xc585, 0xbcce, 0x8327, 0x2a91,
            0x661d, 0xa5b9, 0xcac7, 0x1218, 0x6e6b, 0x6996, 0xe1b2, 0x3bfc, 0x79b6, 0x39b6, 0xd112,
            0x6ace, 0x81cf, 0x7239, 0xcc8d, 0x2f46, 0x1518, 0x9ebd, 0x1f35, 0xca3e, 0x7b97, 0x0428,
            0xb3db, 0x9723, 0xa54b, 0x6253, 0x0a2e, 0x005e, 0x6517, 0xc461, 0xbd05, 0xee83, 0xf766,
            0x9500, 0x87f5, 0x4451, 0x261e, 0x53f0, 0x7980, 0x9cbf, 0xbad8, 0x4c77, 0x20bb, 0xf5b3,
            0xfd02, 0x18b7, 0x3e5a, 0x890f, 0x84d0, 0xa3fa, 0xc444, 0x9f36, 0xe02e, 0x4e70, 0xc951,
            0xf13f, 0x7bea, 0xdefc, 0x647e, 0x0e6d, 0xa714, 0xa3f3, 0xb406, 0x77a2, 0xb725, 0x9207,
            0x034f, 0x94e7, 0x5abd, 0xe8b4, 0xe576, 0x9c46, 0x4e42, 0xf5df, 0xdfc3, 0xc680, 0xd4d5,
            0x8e90, 0x7123, 0x1569, 0x5b4f, 0xc8e8, 0x0c3f, 0x48f3, 0x504d, 0x03c8, 0xda9b, 0xbb2a,
            0xb03f, 0x62c4, 0x066e, 0x88b2, 0x05d5, 0x294d, 0x7f9e, 0xfa83, 0xafd5, 0xde40, 0xe0be,
            0x66f9, 0xb991, 0x693d, 0x7b30, 0x0376, 0xa964, 0x7d70, 0x465e, 0x3520, 0xebda, 0x31ad,
            0xecb4, 0x2686, 0xdae9, 0xac17, 0x9c32, 0x9130, 0x6e08, 0xd7a9, 0x780d, 0x1568, 0x1792,
            0x444d, 0xdd86, 0xf7b9, 0x8315, 0x2678, 0x9ae3, 0xfafa, 0x392f, 0xf95b, 0x9833, 0x1ee2,
            0x9be5, 0x1f23, 0x27ae, 0x9e74, 0x64f4, 0x0ce9, 0xc452, 0x6ec1, 0xa54c, 0xac38, 0xadbd,
            0x05dc, 0xa5f1, 0xb25b, 0xad01, 0x2aed, 0xd3df, 0x4dcb, 0xa5a7, 0x4bbf, 0x05b7, 0xc477,
            0xed46, 0x2150, 0xc427, 0x01bd, 0x0059, 0x9c1d, 0xa457 };

    public static byte[] generate(byte[] data) {
        return generate(data, 0, data.length);
    }

    /**
     * 计算数据{data}区域[offset..offset+len-1]段的CRC值 <br>
     * CRC结果在data[offset+len]和data[offset+len+1]两个数据位
     */
    public static byte[] generate(byte[] data, int offset, int len) {
        int end = offset + len + 2;
        if (data.length < end)
        {
            byte[] bs = new byte[end];
            System.arraycopy(data, 0, bs, 0, data.length);
            data = bs;
        }

        int crc = calculate(data, offset, len);
        data[offset + len] = (byte) ((crc >>> 0) & 0xff);
        data[offset + len + 1] = (byte) ((crc >>> 8) & 0xff);
            return data;
        }

    public static boolean validate(byte[] data) {
        return validate(data, 0, data.length);
    }

    /**
     * 验证{data}区域[offset..offset+len-1]段的CRC值 <br>
     * CRC保存在data[offset+len]和data[offset+len+1]两个数据位
     */
    public static boolean validate(byte[] data, int offset, int len) {
        if (data.length < offset + len + 2)
        {
            return false;
        }

        int crc = calculate(data, offset, len);
        return (data[offset + len] & 0xff) == ((crc >>> 0) & 0xff)
        && ((data[offset + len + 1] & 0xff) == ((crc >>> 8) & 0xff));
    }

    public static int calculate(byte[] data) {
        return calculate(data, 0, data.length);
    }

    /**
     * 计算{data}区域[offset..offset+len-1]段的CRC值
     */
    public static int calculate(byte[] data, int offset, int len) {
        int crcIndex = 0, crcEntry = 0;
        for (int i = offset, end = offset + len; i < end; i++)
        {
            crcEntry = CRC16table[crcIndex & 0xF | (data[i] & 0x0f) << 0x4];
            crcIndex = (crcIndex >>> 0x4 ^ crcEntry);
            crcEntry = CRC16table[crcIndex & 0xF | data[i] & 0xf0];
            crcIndex = (crcIndex >>> 0x4 ^ crcEntry);
        }
        
        return crcIndex;
    }
}