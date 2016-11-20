package protocol.java.json;

/**
 * 客户端升级信息
 */
public class AppUpgradeInfo {
    
    /**
     * 0：建议升级
     * 1：强制升级
     */
    public int type;                        // 升级类型
    
    public String name;                     // 版本名称
    
    public String version;                  // 新版本号
    
    public String url;                      // 升级包下载地址
    
    public String desc;                     // 升级描述
}