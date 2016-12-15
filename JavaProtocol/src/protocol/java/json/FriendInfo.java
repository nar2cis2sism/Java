package protocol.java.json;

/**
 * 好友信息
 */
public class FriendInfo {
    
    public long friend_id;                  // 好友ID
    
    public String nickname;                 // 好友昵称
    
    public String remark;                   // 好友备注
    
    public String avatar_url;               // 头像下载地址
    
    public String friend_info_ver;          // 好友信息版本号
    
    public int del;                         // 1：好友已被删除
}