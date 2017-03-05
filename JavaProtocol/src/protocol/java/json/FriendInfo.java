package protocol.java.json;


/**
 * 好友信息
 */
public class FriendInfo {
    
    public long friend_id;                  // 好友ID
    
    /**
     * 1：增加
     * 2：删除
     * 3：更新
     */
    public int op;                          // 操作指令
    
    public String remark;                   // 好友备注
    
    public String nickname;                 // 好友昵称
    
    public String signature;                // 好友签名
    
    public String avatar_url;               // 好友头像下载地址
    
    public String friend_info_ver;          // 好友信息版本号
}