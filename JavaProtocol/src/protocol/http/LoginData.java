package protocol.http;

/**
 * 用户登录返回数据
 */
public class LoginData {
    
    public String token;                            // 用户登录凭证

    /**
     * 高位表示“头像版本(Int32)”
     * 低位表示“信息版本(Int32)”
     */
    public long version;                            // 用户资料版本
    
    public Long friend_list_timestamp;              // 好友列表最近更新的时间戳
}