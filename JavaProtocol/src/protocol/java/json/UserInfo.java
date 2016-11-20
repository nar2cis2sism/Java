package protocol.java.json;

/**
 * 用户资料
 */
public class UserInfo {
    
    public String user_info_ver;            // 用户信息版本
    
    public long uid;                        // 用户唯一标识
    
    public String username;                 // 注册用户名

    public String avatar_url;               // 头像下载地址

    public String realname;                 // 真实姓名

    public String nickname;                 // 昵称
    
    /**
     * 0：男
     * 1：女
     */
    public int gender;                      // 性别

    public long birthday;                   // 出生日期

    public String resume;                   // 个人简介

    /**
     * 0：未认证
     * 1：已认证
     */
    public int authentication;              // 实名认证
}