package protocol.http;

import java.util.List;

/**
 * 查询好友列表返回数据
 */
public class FriendListData {
    
    public long timestamp;                          // 最近更新的时间戳
    
    /**
     * 0：全量更新
     * 1：增量更新
     */
    public int sync_type;                           // 同步类型
    
    /**
     * 0：同步完成
     * 1：继续同步
     */
    public int sync_status;                         // 同步状态
    
    public List<FriendListItem> list;               // 好友列表
    
    public static class FriendListItem {
        
        public String account;                      // 好友账号
        
        /**
         * 0：加为好友
         * 1：删除好友
         */
        public int op;                              // 操作指令
        
        public FriendInfo info;                     // 好友资料
        
        public static class FriendInfo extends protocol.http.FriendData.FriendInfo {

            /**
             * 高位表示“头像版本(Int32)”
             * 低位表示“信息版本(Int32)”
             */
            public long version;                    // 好友资料版本
        }
    }
}