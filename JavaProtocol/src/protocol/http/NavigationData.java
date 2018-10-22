package protocol.http;

/**
 * 获取导航配置返回数据
 */
public class NavigationData {
    
    public String socket_server_url;                // Socket服务器地址
    
    public String upload_server_url;                // 文件上传服务器地址
    
    public String download_server_url;              // 文件下载服务器地址
    
    public AppUpgradeInfo upgrade;                  // APP升级信息
    
    public static class AppUpgradeInfo {
        
        /**
         * 0：建议升级
         * 1：强制升级
         */
        public int type;                            // 升级类型
        
        public String name;                         // 版本名称
        
        public String version;                      // 新版本号
        
        public String url;                          // 升级包下载地址
        
        public String desc;                         // 升级描述
    }
}