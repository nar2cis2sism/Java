package protocol.http;

/**
 * 好友同步信息
 */
public class FriendSync {
    
    public String account;                  // 好友账号
    
    /**
     * 0：加为好友
     * 1：删除好友
     */
    public int action;                      // 操作指令
    
    public String nickname;                 // 好友昵称
    
    public String signature;                // 好友签名
    
    public String avatar_url;               // 好友头像下载地址
}