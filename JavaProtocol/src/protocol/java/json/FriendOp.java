package protocol.java.json;

/**
 * 好友操作
 */
public class FriendOp {
    
    public long uid;                        // 好友ID
    
    /**
     * 0：增加
     * 1：删除
     * 2：更新
     */
    public int op;                          // 操作指令
    
    public String remark;                   // 好友备注
    
    public String nickname;                 // 好友昵称
    
    public String signature;                // 好友签名
    
    public String avatar_url;               // 好友头像下载地址
    
    public String friend_info_ver;          // 好友资料版本号
}