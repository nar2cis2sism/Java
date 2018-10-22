package protocol.http;

import java.util.List;

/**
 * 搜索联系人返回数据
 */
public class SearchContactData {
    
    public int count;                               // 满足条件的总数量
    
    public List<ContactData> list;                  // 搜索范围内的联系人列表
    
    public static class ContactData {
        
        public String account;                      // 联系人账号
        
        public String nickname;                     // 联系人昵称
        
        public String avatar_url;                   // 联系人头像下载地址
    }
}