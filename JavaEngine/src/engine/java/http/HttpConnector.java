package engine.java.http;

import engine.java.log.LogFactory;
import engine.java.log.LogFactory.LOG;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy.Type;
import java.net.URL;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Http连接器<br>
 * 需要声明权限<uses-permission android:name="android.permission.INTERNET" />
 * 
 * @author Daimon
 * @version N
 * @since 6/6/2014
 * 
 * Daimon:HttpURLConnection
 */

public class HttpConnector {

    public static final String CTWAP  = "10.0.0.200";              // 中国电信代理

    public static final String CMWAP  = "10.0.0.172";              // 中国移动代理

    public static final String UNIWAP = "10.0.0.172";              // 中国联通代理

    private String name;                                           // 连接标识

    private Object tag;                                            // 标签属性

    private final HttpRequest request;                             // 连接请求

    private HttpURLConnection conn;                                // 连接管理
    
    private final ReentrantLock lock = new ReentrantLock();        // 连接操作锁

    private HttpParams params;                                     // 连接参数

    private java.net.Proxy proxy;                                  // 连接代理

    private int timeout;                                           // 超时时间（毫秒）

    private String remark;                                         // 备注（用于日志查询）

    private long time;                                             // 时间（用于调试）

    private final AtomicBoolean isConnected = new AtomicBoolean(); // 网络是否连接完成

    private final AtomicBoolean isCancelled = new AtomicBoolean(); // 是否取消网络连接

    private HttpConnectionListener listener;                       // HTTP连接监听器

    /**
     * GET请求
     * 
     * @param url 请求URL地址
     */

    public HttpConnector(String url) {
        this(url, null, null);
    }

    /**
     * POST请求
     * 
     * @param url 请求URL地址
     * @param postData 请求消息数据
     */

    public HttpConnector(String url, byte[] postData) {
        this(url, null, postData);
    }

    /**
     * Http连接请求
     * 
     * @param url 请求URL地址
     * @param headers 请求头
     * @param postData 请求消息数据
     */

    public HttpConnector(String url, Map<String, String> headers, byte[] postData) {
        request = new HttpRequest(url, headers, postData);
    }

    /**
     * 设置请求名称
     */

    public HttpConnector setName(String name) {
        this.name = name;
        return this;
    }

    public String getName() {
        return name;
    }
    
    public HttpRequest getRequest() {
        return request;
    }

    /**
     * 设置连接参数
     */
    
    public HttpParams getParams() {
        if (params == null)
        {
            params = new HttpParams();
        }
        
        return params;
    }

    /**
     * 设置代理主机
     */

    public HttpConnector setProxy(java.net.Proxy proxy) {
        this.proxy = proxy;
        return this;
    }

    /**
     * 设置代理地址（不含scheme）
     */

    public HttpConnector setProxyAddress(String address) {
        int port = 80;
        int index = address.indexOf(":");
        if (index > 0)
        {
            port = Integer.parseInt(address.substring(index + 1));
            address = address.substring(0, index);
        }

        proxy = new java.net.Proxy(Type.HTTP, new InetSocketAddress(address, port));
        return this;
    }

    /**
     * 设置超时时间
     * 
     * @param timeout 单位：毫秒
     */

    public HttpConnector setTimeout(int timeout) {
        this.timeout = timeout;
        return this;
    }

    /**
     * 设置备注提示（输出信息到日志）
     */

    public HttpConnector setRemark(String remark) {
        this.remark = remark;
        return this;
    }

    public Object getTag() {
        return tag;
    }

    public HttpConnector setTag(Object tag) {
        this.tag = tag;
        return this;
    }

    /**
     * 连接网络
     */

    public synchronized HttpResponse connect() throws Exception {
        if (isCancelled())
        {
            return null;
        }
        
        // 为了防止请求被拦截更改数据
        HttpRequest r = request.clone();
        if (listener != null)
        {
            listener.connectBefore(this, r);
            if (isCancelled())
            {
                return null;
            }
        }

        time = System.currentTimeMillis();
        try {
            HttpResponse response = doConnect(r);
            if (!isCancelled())
            {
                String target = null;
                if (remark != null)
                    log("服务器" + (target == null ? "" : "[" + target + "]") +
                        "响应时间--" + (System.currentTimeMillis() - time) + "ms");
                
                if (listener != null)
                {
                    listener.connectAfter(this, response);
                }

                return response;
            }
        } catch (Exception e) {
            if (!isCancelled())
            {
                if (remark != null)
                    log(new Exception("网络异常", e));
                
                if (listener != null)
                {
                    listener.connectError(this, e);
                }
                
                throw e;
            }
        } finally {
            isConnected.set(true);
            close();
        }

        return null;
    }
    
    protected HttpResponse doConnect(HttpRequest request) throws Exception {
        String url = request.getUrl();
        String method = request.getMethod();
        byte[] postData = request.getPostData();
        Map<String, String> headers = request.getHeaders();
        
        lock.lock();
        try {
            if (isCancelled())
            {
                return null;
            }

            if (remark != null)
                log("联网请求：" + request.getUrl());
            
            URL href = new URL(url);
            if (proxy != null)
            {
                if (remark != null)
                    log("使用代理网关：" + proxy);
                conn = (HttpURLConnection) href.openConnection(proxy);
            }
            else
            {
                conn = (HttpURLConnection) href.openConnection();
            }
        } finally {
            lock.unlock();
        }
        
        // 设置超时
        if (timeout > 0)
        {
            conn.setConnectTimeout(timeout);
            conn.setReadTimeout(timeout);
        }

        if (HttpRequest.METHOD_POST.equals(method))
        {
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.addRequestProperty("Content-Type",
                    "application/x-www-form-urlencoded");
            conn.addRequestProperty("Content-Length",
                    String.valueOf(postData != null ? postData.length : 0));
        }

        conn.addRequestProperty("Host", getHost(url));
        conn.setRequestMethod(method);

        if (params != null)
        {
            params.setup(conn);
        }

        if (headers != null && !headers.isEmpty())
        {
            for (Entry<String, String> entry : headers.entrySet())
            {
                conn.addRequestProperty(entry.getKey(), entry.getValue());
            }
        }

        if (postData != null)
        {
            OutputStream outputstream = conn.getOutputStream();
            outputstream.write(postData);
            outputstream.flush();
            outputstream.close();
        }
        else
        {
            conn.connect();
        }

        return new HttpResponse(conn);
    }

    /**
     * 取消网络连接
     */

    public void cancel() {
        if (isCancelled.compareAndSet(false, true))
        {
            close();

            if (!isConnected.get() && remark != null)
                log("取消网络连接：" + request.getUrl());
        }
    }

    /**
     * 关闭网络连接
     */
    
    private void close() {
        if (conn == null) return;
        
        lock.lock();
        try {
            if (conn != null)
            {
                conn.disconnect();
                conn = null;
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean isCancelled() {
        return isCancelled.get();
    }

    public HttpConnector setListener(HttpConnectionListener listener) {
        this.listener = listener;
        return this;
    }

    /**
     * 日志输出
     */

    private void log(Object message) {
        LOG.log(remark, message);
    }

    /**
     * HTTP连接监听器
     */

    public static interface HttpConnectionListener {

        public void connectBefore(HttpConnector conn, HttpRequest request);

        public void connectAfter(HttpConnector conn, HttpResponse response);

        public void connectError(HttpConnector conn, Exception e);
    }

    /**
     * Daimon:从url中分离出主机域名
     * 
     * @param url 下载地址
     */

    public static final String getHost(String url) {
        String host = null;
        String port = null;
        String tempStr = url;

        int index = tempStr.indexOf("://");
        if (index > -1)
        {
            tempStr = tempStr.substring(index + "://".length());
        }

        index = tempStr.indexOf('/');
        if (index > 0)
        {
            host = tempStr.substring(0, index);
        }
        else
        {
            host = tempStr;
        }

        index = host.indexOf(":");
        if (index > -1)
        {
            port = host.substring(index + 1);
            host = host.substring(0, index);
        }

        if (port != null)
        {
            host += ":" + port;
        }

        return host;
    }

    static
    {
        LogFactory.addLogFile(HttpConnector.class, "http.txt");
    }
}