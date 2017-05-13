package protocol.java.json;

/**
 * 用户资料
 */
public class UserInfo {
    
    public String version;                  // 用户信息版本

    public String nickname;                 // 用户昵称
    
    /**
     * 0：男
     * 1：女
     */
    public int gender;                      // 性别

    public long birthday;                   // 出生日期

    public String city;                     // 常驻城市
    
    public String signature;                // 签名

    public String profile;                  // 个人简介

    /**
     * 0：未认证
     * 1：已认证
     */
    public int authentication;              // 实名认证

    public String avatar_url;               // 头像下载地址
}