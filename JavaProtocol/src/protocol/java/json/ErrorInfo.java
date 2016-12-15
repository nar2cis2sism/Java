package protocol.java.json;

import java.util.HashMap;

/**
 * 错误信息
 */
public class ErrorInfo {
    
    public static final int CODE_SUCCESS = 200;
    
    private static final HashMap<Integer, String> errorMap
    = new HashMap<Integer, String>();
    
    static
    {
        errorMap.put(400, "请求格式错误");
        errorMap.put(401, "密码错误");
        errorMap.put(404, "用户不存在");
        errorMap.put(407, "Token已失效");
        errorMap.put(410, "已在其他地方登录");
        errorMap.put(500, "服务器内部错误");
    }
    
    public int code;                    // 错误编码
    
    public String msg;                  // 错误原因描述
    
    public ErrorInfo(int code) {
        msg = errorMap.get(this.code = code);
    }
}