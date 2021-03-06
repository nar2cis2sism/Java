package engine.java.http;

import engine.java.util.common.TextUtils;
import engine.java.util.io.IOUtil;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Http响应体
 * 
 * @author Daimon
 * @since 6/6/2015
 */
public class HttpResponse {
    
    private final int code;
    private final String reason;
    private final byte[] content;
    
    private Map<String, List<String>> headers;
    
    HttpResponse(HttpURLConnection conn) throws Exception {
        this(conn.getResponseCode(), conn.getResponseMessage(), IOUtil.readStream(conn.getInputStream()));
        headers = conn.getHeaderFields();
    }
    
    HttpResponse(int responseCode, String message, byte[] data) {
        code = responseCode;
        reason = message;
        content = data;
    }
    
    public int getStatusCode() {
        return code;
    }
    
    public String getReasonPhrase() {
        return reason;
    }
    
    public byte[] getContent() {
        return content;
    }
    
    public InputStream getInputStream() {
        return new ByteArrayInputStream(content);
    }
    
    String getHeaderField(String key) {
        if (headers == null)
        {
            return null;
        }
        
        List<String> list = headers.get(key);
        if (list == null || list.isEmpty())
        {
            return null;
        }
        
        return TextUtils.join(",", list);
    }
    
    public int getHeaderFieldInt(String field, int defaultValue) {
        try {
            return Integer.parseInt(getHeaderField(field));
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    public long getHeaderFieldLong(String field, long defaultValue) {
        try {
            return Long.parseLong(getHeaderField(field));
        } catch (Exception e) {
            return defaultValue;
        }
    }
        
    @SuppressWarnings("deprecation")
    public long getHeaderFieldDate(String field, long defaultValue) {
        try {
            return Date.parse(getHeaderField(field));
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    /**
     * 获取头信息
     */
    public String getHeader(String key) {
        return getHeaderField(key);
    }
    
    public String getContentEncoding() {
        return getHeaderField("Content-Encoding");
    }
    
    public long getContentLength() {
        return getHeaderFieldLong("Content-Length", -1);
    }
    
    public String getContentType() {
        return getHeaderField("Content-Type");
    }
    
    public String getUserAgent() {
        return getHeaderField("User-Agent");
    }
    
    public long getDate() {
        return getHeaderFieldDate("Date", 0);
    }
    
    public long getExpiration() {
        return getHeaderFieldDate("Expires", 0);
    }
    
    public long getLastModified() {
        return getHeaderFieldDate("Last-Modified", 0);
    }
    
    public String getLocation() {
        return getHeaderField("Location");
    }
    
    public String getHost() {
        return getHeaderField("Host");
    }
}