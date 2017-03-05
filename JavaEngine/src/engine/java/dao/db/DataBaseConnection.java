package engine.java.dao.db;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import engine.java.util.log.LogFactory.LOG;

/**
 * 数据库连接类，实现了连接池和代理机制
 * 
 * @author Daimon
 */

public class DataBaseConnection {
	
	private final String url;
	private final String username;
	private final String password;
	
	private int maxConnectionNum = 50;					// 数据库连接数上限
	private int minConnectionNum = 10;					// 数据库连接数下限
	
	private Builder builder;							// 创建连接
	private LinkedBlockingQueue<Connection> list;		// 连接池，里面装的是连接的代理类
	
	private int active_count;							// 连接的活动数量
	
	private boolean isClosed;
	
	DataBaseConnection(String url, String username, String password) {
		this.url = url;
		this.username = username;
		this.password = password;
		
		builder = new Builder();
		list = new LinkedBlockingQueue<Connection>();
		
		builder.start();
	}
	
	public void config(int maxConnectionNum, int minConnectionNum) {
		this.maxConnectionNum = maxConnectionNum;
		this.minConnectionNum = minConnectionNum;
	}
	
	public synchronized Connection getConnection() {
		if (isClosed)
		{
			throw new DBException("数据库已关闭");
		}
		
		if (list.isEmpty() && active_count < maxConnectionNum)
		{
			builder.start();
		}
		
		Connection conn = null;
		try {
			conn = list.poll(3, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
		}
		
		if (conn != null)
		{
			active_count++;
			if (isPendingCreate())
			{
				builder.start();
			}
		}
		
		return conn;
	}
	
	boolean isPendingCreate() {
		int size = list.size();
		return size < minConnectionNum && size + active_count < maxConnectionNum;
	}
	
	void createConnection() {
		Connection conn = null;
		try {
			conn = DriverManager.getConnection(url, username, password);
		} catch (Exception e) {
			LOG.log(e);
		}
		
		if (conn != null)
		{
			Proxy p = (Proxy) Proxy.newProxyInstance(
					conn.getClass().getClassLoader(), 
					new Class[] {Connection.class}, 
					new ConnectionHandle(conn));
			list.add((Connection) p);
		}
	}
	
	public int getActiveCount() {
		return active_count;
	}
	
	/**
	 * 连接的空闲数量，即可用连接
	 */
	
	public int getFreeCount() {
		return list.size();
	}
	
	public synchronized void close() {
		isClosed = true;
		builder.stop();
		for (Connection conn : list)
		{
			ConnectionHandle handle = (ConnectionHandle) Proxy.getInvocationHandler(conn);
			handle.close();
		}
		
		list.clear();
	}
	
	private class ConnectionHandle implements InvocationHandler {
		
		private final Connection conn;
		
		public ConnectionHandle(Connection conn) {
			this.conn = conn;
		}
		
		public void close() {
			try {
				conn.close();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
			}
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args)
				throws Throwable {
			Object obj = null;
			if ("close".equals(method.getName()))
			{
				if (isClosed || list.size() >= maxConnectionNum)
				{
					conn.close();
				}
				else
				{
					list.add((Connection) proxy);
					active_count--;
				}
			}
			else
			{
				obj = method.invoke(conn, args);
			}
			
			return obj;
		}
	}
	
	private class Builder implements Runnable {
		
		private final ExecutorService executor = Executors.newSingleThreadExecutor();
		
		private boolean isRunning;
		
		public void start() {
			if (!isRunning)
			{
				isRunning = true;
				executor.execute(this);
			}
		}
		
		public void stop() {
			executor.shutdownNow();
		}

		@Override
		public void run() {
			while (isPendingCreate())
			{
				createConnection();
			}
			
			isRunning = false;
		}
	}
}