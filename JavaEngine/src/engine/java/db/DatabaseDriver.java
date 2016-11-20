package engine.java.db;

/**
 * 数据库驱动
 * 
 * @author Daimon
 */

public class DatabaseDriver {
	
	private static final String DRIVER_MYSQL = "com.mysql.jdbc.Driver";
	
	public static DatabaseDriver getDriver(String driver) {
		try {
			Class.forName(driver);
		} catch (Exception e) {
			throw new DBException("数据库驱动加载失败", e);
		}
		
		return new DatabaseDriver();
	}
	
	public static DatabaseDriver getMysqlDriver() {
		return getDriver(DRIVER_MYSQL);
	}
	
	private DatabaseDriver() {}
	
	public DataBaseConnection createConnection(String url, String username, String password) {
		return new DataBaseConnection(url, username, password);
	}
	
	public DataBaseConnection createConnection(String host, String database, String username, String password) {
		String url = String.format("jdbc:mysql://%s/%s?useUnicode=true&characterEncoding=utf-8&useSSL=false", 
		        host, database);
		return new DataBaseConnection(url, username, password);
	}
}