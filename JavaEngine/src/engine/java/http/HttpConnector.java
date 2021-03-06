package engine.java.http;

import engine.java.http.HttpRequest.HttpEntity;
import engine.java.util.common.LogFactory;
import engine.java.util.common.LogFactory.LOG;
import engine.java.util.common.TextUtils;

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
 * Http连接器
 * 
 * @author Daimon
 * @since 6/6/2014
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

    private final AtomicBoolean isConnected = new AtomicBoolean(); // 网络是否连接完成

    private final AtomicBoolean isCancelled = new AtomicBoolean(); // 是否取消网络连接

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
     * @param entity 请求数据
     */
    public HttpConnector(String url, HttpEntity entity) {
        this(url, null, entity);
    }

    /**
     * Http连接请求
     * 
     * @param url 请求URL地址
     * @param headers 请求头
     * @param entity 请求数据
     */
    public HttpConnector(String url, Map<String, String> headers, HttpEntity entity) {
        request = new HttpRequest(url, headers, entity);
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
    
    public HttpConnector setTag(Object tag) {
        this.tag = tag;
        return this;
    }

    public Object getTag() {
        return tag;
    }

    public HttpRequest getRequest() {
        return request;
    }

    /**
     * 设置连接参数
     */
    public HttpParams getParams() {
        if (params == null) params = new HttpParams();
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
     * 连接网络
     */
    public synchronized HttpResponse connect() throws Exception {
        isConnected.set(false);
        if (isCancelled())
        {
            return null;
        }
        
        long time = System.currentTimeMillis();
        try {
            HttpResponse response = doConnect(request);
            if (!isCancelled())
            {
                log(String.format("服务器响应时间--%dms", System.currentTimeMillis() - time));
                return response;
            }
        } catch (Exception e) {
            if (!isCancelled())
            {
                log(e);
                throw e;
            }
        } finally {
            isConnected.set(true);
            close();
        }

        return null;
    }
    
    protected HttpResponse doConnect(HttpRequest request) throws Exception {
        lock.lock();
        try {
            URL href = new URL(request.getUrl());
            if (proxy != null)
            {
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
        
        String method = request.getMethod();
        HttpEntity entity = request.getEntity();
        Map<String, String> headers = request.getHeaders();
        // 设置超时
        if (timeout > 0)
        {
            conn.setConnectTimeout(timeout);
            conn.setReadTimeout(timeout);
        }

        conn.setRequestMethod(method);
        if (HttpRequest.METHOD_POST.equals(method))
        {
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.addRequestProperty("Content-Length", String.valueOf(entity.getContentLength()));
        }

        conn.addRequestProperty("Host", getHost(request.getUrl()));
        if (headers != null && !headers.isEmpty())
        {
            for (Entry<String, String> entry : headers.entrySet())
            {
                conn.addRequestProperty(entry.getKey(), entry.getValue());
            }
        }

        if (params != null) params.setup(conn);
        if (entity != null)
        {
            OutputStream outputstream = conn.getOutputStream();
            entity.writeTo(outputstream);
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
            if (!isConnected.get()) log("取消网络连接：" + request.getUrl());
            close();
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

    /**
     * 日志输出
     */
    private void log(Object message) {
        if (!TextUtils.isEmpty(name)) LOG.log(name, message);
    }

    /**
     * Daimon:从url中分离出主机域名
     * 
     * @param url 主机地址
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