package protocol.http;

/**
 * 获取好友资料返回数据
 */
public class FriendData {

    /**
     * 高位表示“头像版本(Int32)”
     * 低位表示“信息版本(Int32)”
     */
    public long version;                        // 好友资料版本
    
    public FriendInfo info;                     // 好友信息
    
    public static class FriendInfo {

        public String nickname;                 // 好友昵称
        
        /**
         * 0：男
         * 1：女
         */
        public int gender;                      // 性别

        /**
         * 此字段包含
         * “区域编码(String)”与
         * “地区名称(String)”，
         * 用“:”分隔
         */
        public String region;                   // 所在地区
        
        public String signature;                // 签名

        public String avatar_url;               // 头像下载地址
        
        public String mobile_phone;             // 手机号
    }
}