package protocol.java.json;

import engine.java.util.Util;

import java.util.HashMap;

public class ErrorInfo {
    
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
    
    protected int code;
    
    protected String msg;
    
    public ErrorInfo(int code) {
        this.code = code;
        msg = Util.getString(errorMap.get(code), "");
    }
}