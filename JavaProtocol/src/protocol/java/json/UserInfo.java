package protocol.java.json;


/**
 * 用户资料
 */
public class UserInfo {
    
    public String user_info_ver;            // 用户信息版本

    public String avatar_url;               // 头像下载地址

    public String nickname;                 // 昵称
    
    public String qrcode;                   // 二维码图片
    
    /**
     * 0：男
     * 1：女
     */
    public int gender;                      // 性别

    public String city;                     // 地区

    public long birthday;                   // 出生日期

    public String resume;                   // 个人简介

    /**
     * 0：未认证
     * 1：已认证
     */
    public int authentication;              // 实名认证
}