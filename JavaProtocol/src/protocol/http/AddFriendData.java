package protocol.http;

import protocol.http.FriendListData.FriendListItem;

/**
 * 添加删除好友返回数据
 */
public class AddFriendData {
    
    public long timestamp;                          // 好友列表更新时间戳
    
    public FriendListItem.FriendInfo info;          // 好友资料
}