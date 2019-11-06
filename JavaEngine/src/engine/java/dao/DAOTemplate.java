package engine.java.dao;

import static engine.java.dao.DAOUtil.checkNull;
import static engine.java.dao.DAOUtil.extractFromResultSet;
import static engine.java.util.common.LogFactory.LOG.log;
import static engine.java.util.common.LogFactory.LogUtil.getCallerStackFrame;

import engine.java.dao.DAOUtil.DAOException;
import engine.java.dao.annotation.DAOPrimaryKey;
import engine.java.dao.annotation.DAOProperty;
import engine.java.dao.annotation.DAOTable;
import engine.java.dao.db.DataBaseConnection;
import engine.java.dao.util.Page;
import engine.java.util.common.LogFactory;
import engine.java.util.common.LogFactory.LogUtil;
import engine.java.util.common.Pair;
import engine.java.util.common.TextUtils;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 操作数据库的模板，尽量面向对象，以简化DAO层<br>
 * 不支持多表联合操作，使用原生SQL语句或事务处理效率更高<br>
 * 
 * @author Daimon
 * @since 4/5/2015
 */
public class DAOTemplate {

    /**
     * 数据库更新监听器
     */
    public interface DBUpdateListener {

        void onCreate(DAOTemplate dao);

        void onUpdate(DAOTemplate dao, int oldVersion, int newVersion);
    }

    /**
     * 数据库事务处理
     */
    public interface DAOTransaction {

        /**
         * 事务执行（抛出异常或返回false表示事务处理失败）
         */
        boolean execute(DAOTemplate dao) throws Exception;
    }

    private final AtomicReference<Connection> conn
    = new AtomicReference<Connection>();
    
    private boolean printLog = true;

    private final DataBaseConnection db;

    /**
     * 数据库表监听器
     */
    public interface DAOListener {

        /** Daimon:标志位 **/
        int ALL     = ~0;

        int INSERT  = 1 << 0;

        int DELETE  = 1 << 1;

        int UPDATE  = 1 << 2;

        void onChange();
    }

    /**
     * 数据库观察者
     */
    private static class DAOObserver {

        public final DAOListener listener;

        private final int op;

        public DAOObserver(DAOListener listener, int op) {
            this.listener = listener;
            this.op = op;
        }

        public boolean hasChange(int op) {
            return (this.op & op) != 0;
        }

        public void notifyChange() {
            listener.onChange();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof DAOObserver)
            {
                DAOObserver observer = (DAOObserver) o;
                return observer.listener == listener && observer.op == op;
            }

            return false;
        }

        @Override
        public int hashCode() {
            return listener.hashCode() + 31 * op;
        }
    }

    private final ConcurrentHashMap<String, CopyOnWriteArraySet<DAOObserver>> listeners
    = new ConcurrentHashMap<String, CopyOnWriteArraySet<DAOObserver>>(); // 表名为索引

    private final CopyOnWriteArraySet<DAOObserver> pendingListeners
    = new CopyOnWriteArraySet<DAOObserver>();

    public DAOTemplate(DataBaseConnection db) {
        this.db = db;
    }

    /**
     * 关闭数据库
     */
    public void close() {
        db.close();
    }

    /**
     * 默认打印数据库执行语句，如有性能问题可以关闭
     */
    public void disablePrintLog(boolean disable) {
        printLog = !disable;
    }

    private Connection getConnection() throws Exception {
        Connection conn = this.conn.get();
        if (conn == null)
        {
            conn = db.getConnection();
            if (conn == null)
            {
                throw new Exception("取不到数据库连接");
            }
        }
    
        return conn;
    }

    /******************************* 华丽丽的分割线 *******************************/

    /**
     * @param createIfNotExist If true, create an empty set if it's not existed.
     */
    private CopyOnWriteArraySet<DAOObserver> getObservers(Table table,
            boolean createIfNotExist) {
        String key = table.getTableName();

        if (!listeners.contains(key) && createIfNotExist)
        {
            listeners.putIfAbsent(key, new CopyOnWriteArraySet<DAOObserver>());
        }

        return listeners.get(key);
    }

    public void registerListener(Class<?> c, DAOListener listener, int op) {
        checkNull(listener);
        getObservers(Table.getTable(c), true).add(new DAOObserver(listener, op));
    }

    public void registerListener(Class<?> c, DAOListener listener) {
        registerListener(c, listener, DAOListener.ALL);
    }

    public void unregisterListener(Class<?> c, DAOListener listener) {
        CopyOnWriteArraySet<DAOObserver> observers = getObservers(Table.getTable(c), false);
        if (observers == null || observers.isEmpty()) return;
        
        if (listener == null)
        {
            observers.clear();
            return;
        }

        Iterator<DAOObserver> iter = observers.iterator();
        while (iter.hasNext())
        {
            DAOObserver observer = iter.next();
            if (observer.listener == listener)
            {
                observers.remove(observer);
            }
        }
    }

    /**
     * 外界可以通过此方法自行通知数据库表更新
     */
    public void notifyChange(Class<?> c) {
        notifyChange(Table.getTable(c), DAOListener.ALL);
    }

    /**
     * 目前只对增删改进行通知
     * 
     * @see #save(Object...)
     * @see #remove(DAOSQLBuilder)
     * @see #edit(DAOSQLBuilder, Object, String...)
     */
    private void notifyChange(Table table, int op) {
        CopyOnWriteArraySet<DAOObserver> observers = getObservers(table, false);
        if (observers != null && !observers.isEmpty())
        {
            dispatchChange(observers, op);
        }
    }

    private void dispatchChange(CopyOnWriteArraySet<DAOObserver> observers, int op) {
        if (conn.get() != null)
        {
            for (DAOObserver observer : observers)
            {
                if (observer.hasChange(op))
                {
                    pendingListeners.add(observer);
                }
            }
        }
        else
        {
            for (DAOObserver observer : observers)
            {
                if (observer.hasChange(op))
                {
                    observer.notifyChange();
                }
            }
        }
    }

    private void dispatchChange(boolean success) {
        if (success)
        {
            if (pendingListeners.isEmpty()) return;
            
            Iterator<DAOObserver> iter = pendingListeners.iterator();
            pendingListeners.clear();
            while (iter.hasNext()) iter.next().notifyChange();
        }
        else
        {
            pendingListeners.clear();
        }
    }

    /******************************* 华丽丽的分割线 *******************************/

    private <D> D execute(String sql, Object[] bindArgs, Class<D> returnType) throws Exception {
        if (printLog) LOG_SQL(sql, bindArgs);

        Connection conn = getConnection();
        try {
            PreparedStatement ps = conn.prepareStatement(sql);
            if (bindArgs != null && bindArgs.length > 0)
            {
                for (int i = bindArgs.length; i != 0; i--)
                {
                    ps.setObject(i, bindArgs[i - 1]);
                }
            }

            Object returnObj = null;
            if (returnType == Boolean.class)
            {
                returnObj = ps.execute();
                ps.close();
            }
            else if (returnType == Integer.class)
            {
                returnObj = ps.executeUpdate();
                ps.close();
            }
            else if (returnType == ResultSet.class)
            {
                returnObj = ps.executeQuery();
                ps.closeOnCompletion();
            }
            else if (returnType == Long.class)
            {
                ResultSet rs = ps.executeQuery();
                if (rs != null)
                {
                    if (rs.first())
                    {
                        returnObj = rs.getLong(1);
                    }
                    
                    ps.close();
                }
            }

            return returnType.cast(returnObj);
        } finally {
            conn.close();
        }
    }
    
    /**
     * 查询结果集
     * 
     * @param sql 查询条件
     * @param selectionArgs 查询参数
     */
    public ResultSet queryResultSet(String sql, String[] selectionArgs) {
        try {
            return execute(sql, selectionArgs, ResultSet.class);
        } catch (Exception e) {
            processException(e);
        }

        return null;
    }

    /**
     * 执行查询语句<br>
     * e.g. SELECT count(*)
     * 
     * @param sql 查询条件
     * @param selectionArgs 查询参数
     * @return 结果数量
     */
    public long queryCount(String sql, String[] selectionArgs) {
        try {
            return execute(sql, selectionArgs, Long.class);
        } catch (Exception e) {
            processException(e);
        }

        return -1;
    }

    /**
     * 执行SQL语句，一般用来执行建表语句
     * 
     * @param sql 遵循数据库语法规则，用;隔开
     */
    public void execute(String sql) {
        if (TextUtils.isEmpty(sql)) return;

        try {
            Connection conn = getConnection();
            Statement st = conn.createStatement();
            try {
                String[] strs = sql.split(";");
                for (String s : strs)
                {
                    if (printLog) LOG_SQL(s);
                    st.execute(s);
                }
            } finally {
                st.close();
                conn.close();
            }
        } catch (Exception e) {
            processException(e);
        }
    }

    /**
     * 执行一条SQL语句
     */
    public void execute(String sql, Object[] bindArgs) {
        if (TextUtils.isEmpty(sql)) return;

        try {
            execute(sql, bindArgs, Boolean.class);
        } catch (Exception e) {
            processException(e);
        }
    }

    /**
     * 执行事务
     */
    public boolean execute(DAOTransaction transaction) {
        boolean success = false;
        
        try {
            Connection conn = getConnection();
            conn.setAutoCommit(false);
            this.conn.set(conn);
            if (printLog) log(LogUtil.getClassAndMethod(getCallerStackFrame()), "事务开始");
            try {
                if (transaction.execute(this))
                {
                    success = true;
                }
            } finally {
                if (success)
                {
                    conn.commit();
                }
                else
                {
                    conn.rollback();
                }
                
                conn.close();
                this.conn.set(null);
                if (printLog) log(LogUtil.getClassAndMethod(getCallerStackFrame()), "事务结束:success=" + success);
                dispatchChange(success);
            }
        } catch (DAOException e) {
            LOG_DAOException(e);
        } catch (Exception e) {
            LOG_DAOException(new DAOException(e));
        }

        return success;
    }

    /**
     * 创建索引
     * 
     * @param c JavaBean类
     * @param indexName 索引名称
     * @param fields 在指定列上创建索引
     */
    public void createIndex(Class<?> c, String indexName, String... fields) {
        checkNull(indexName);

        if (fields == null || fields.length == 0)
        {
            throw new DAOException("请指定需要添加索引的字段", new NullPointerException());
        }

        Table table = Table.getTable(c);

        StringBuilder sql = new StringBuilder()
        .append("CREATE INDEX ")
        .append(indexName)
        .append(" ON ")
        .append(table.getTableName())
        .append(" (");
        
        DAOClause.create(fields).appendTo(table, sql);

        sql.append(")");

        execute(sql.toString());
    }

    /**
     * 删除索引
     * 
     * @param indexName 索引名称
     */
    public void deleteIndex(String indexName) {
        checkNull(indexName);

        execute("DROP INDEX " + indexName);
    }

    /**
     * 删除视图
     * 
     * @param viewName 视图名称
     */
    public void deleteView(String viewName) {
        checkNull(viewName);

        execute("DROP VIEW " + viewName);
    }

    /**
     * 创建数据库表
     * 
     * @param c JavaBean类
     */
    public void createTable(Class<?> c) {
        createTable(c, false);
    }

    /**
     * 创建数据库表
     * 
     * @param c JavaBean类
     * @param deleteOldTable 是否删除旧表
     */
    public void createTable(Class<?> c, boolean deleteOldTable) {
        Table table = Table.getTable(c);
        PrimaryKey primaryKey = table.getPrimaryKey();
        Collection<Property> properties = table.getPropertiesWithoutPrimaryKey();

        StringBuilder sql = new StringBuilder(500);
        if (deleteOldTable)
        {
            sql.append("DROP TABLE IF EXISTS ")
            .append(table.getTableName())
            .append(";");
        }

        sql.append("CREATE TABLE IF NOT EXISTS ")
        .append(table.getTableName())
        .append("\n(\n");

        if (primaryKey != null)
        {
            sql.append("    ")
            .append(primaryKey.getColumn())
            .append(" ")
            .append(primaryKey.asInteger() ?
                    "INTEGER" : translateType(primaryKey.getDataType()))
            .append(" PRIMARY KEY")
            .append(primaryKey.isAutoincrement() ?
                    " AUTO_INCREMENT" : "")
            .append(",\n");
        }

        for (Property property : properties)
        {
            sql.append("    ")
            .append(property.getColumn())
            .append(" ")
            .append(translateType(property.getDataType()))
            .append(",\n");
        }

        sql.deleteCharAt(sql.length() - 2).append(")");
        execute(sql.toString());
    }
    
    private static String translateType(Class<?> dataType) {
        if (dataType == String.class)
        {
            return "varchar(255)";
        }
        else if (dataType == Long.class || dataType == long.class)
        {
            return "bigint";
        }
        
        return dataType.getSimpleName();
    }

    /**
     * 删除数据库表
     * 
     * @param c JavaBean类
     */
    public void deleteTable(Class<?> c) {
        Table table = Table.getTable(c);
        execute("DROP TABLE IF EXISTS " + table.getTableName());
    }

    /**
     * 重命名数据库表(更新{@link DAOTable#name()}时需同步)
     */
    public void renameTable(String oldName, String newName) {
        StringBuilder sql = new StringBuilder()
        .append("ALTER TABLE ")
        .append(oldName)
        .append(" RENAME TO ")
        .append(newName);

        execute(sql.toString());
    }
    
    /**
     * 更改数据库表结构(备份数据然后重建表)
     * 
     * @param c JavaBean类
     */
    public void alterTable(final Class<?> c) {
        execute(new DAOTransaction() {

            @Override
            public boolean execute(DAOTemplate dao) throws Exception {
                Table table = Table.getTable(c);
                String tempTable = "tempTable";

                dao.renameTable(table.getTableName(), tempTable);
                dao.createTable(c);
                dao.execute("INSERT INTO " + table.getTableName() +
                        " SELECT * FROM " + tempTable);
                dao.execute("DROP TABLE " + tempTable);
                return true;
            }
        });
    }

    /**
     * 清除表中所有数据,并且自增长id还原为 1
     * 
     * @param c JavaBean类
     */
    public void resetTable(Class<?> c) {
        Table table = Table.getTable(c);

        StringBuilder sql = new StringBuilder()
        .append("DELETE FROM ")
        .append(table.getTableName());

        PrimaryKey primaryKey = table.getPrimaryKey();
        if (primaryKey.isAutoincrement())
        {
            sql .append(";UPDATE sqlite_sequence SET seq=0 WHERE name='")
                .append(table.getTableName())
                .append("'")
                .append(";VACUUM");
        }

        execute(sql.toString());
    }

    /**
     * 自动整理数据库（当删除很多条数据后需要对数据库空间进行清理回收）
     */
    public void enableAutoTrim() {
        execute("auto_vacuum pragma");
    }

    /**
     * 检测数据库表是否存在
     * 
     * @param c JavaBean类
     */
    public boolean isTableExist(Class<?> c) {
        return queryCount("SELECT count(*) FROM sqlite_master WHERE type='table' AND name=?",
                new String[]{ Table.getTable(c).getTableName() }) > 0;
    }

    /**
     * 保存单条数据
     * 
     * @param obj JavaBean对象，映射到数据库的一张表
     * @return 是否保存成功
     */
    public <T> boolean save(T obj) {
        checkNull(obj);

        Table table = Table.getTable(obj.getClass());

        try {
            StringBuilder sql = new StringBuilder()
            .append("INSERT INTO ")
            .append(table.getTableName())
            .append("(");

            int i = 0;

            Collection<Property> properties = table.getPropertiesWithModifiablePrimaryKey();

            for (Property property : properties)
            {
                sql .append(i++ > 0 ? "," : "")
                    .append(property.getColumn());
            }
            
            sql.append(") VALUES (");

            ArrayList<Object> bindArgs = new ArrayList<Object>(properties.size());

            i = 0;

            for (Property property : properties)
            {
                sql.append(i++ > 0 ? ",?" : "?");
                bindArgs.add(property.getValue(obj));
            }

            sql.append(")");

            if (execute(sql.toString(), bindArgs.toArray(), Integer.class) > 0)
            {
                notifyChange(table, DAOListener.INSERT);
                return true;
            }
        } catch (Exception e) {
            processException(e);
        }

        return false;
    }

    /**
     * 保存多条数据（必须是同一类型）
     * 
     * @param obj JavaBean对象，映射到数据库的一张表
     * @return 是否有数据保存
     */
    @SuppressWarnings("unchecked")
    public <T> boolean save(T... obj) {
        checkNull(obj);
        if (obj.length == 0) return false;

        Table table = Table.getTable(obj.getClass().getComponentType());

        try {
            StringBuilder sql = new StringBuilder(50 + 10 * obj.length)
            .append("INSERT INTO ")
            .append(table.getTableName())
            .append("(");

            StringBuilder values = new StringBuilder(" VALUES ");

            int i = 0;

            Collection<Property> properties = table.getPropertiesWithModifiablePrimaryKey();

            for (Property property : properties)
            {
                sql .append(i++ > 0 ? "," : "")
                    .append(property.getColumn());
            }

            ArrayList<Object> bindArgs = new ArrayList<Object>(properties.size() * obj.length);

            for (Object o : obj)
            {
                i = 0;

                for (Property property : properties)
                {
                    values.append(i++ > 0 ? ",?" : "(?");
                    bindArgs.add(property.getValue(o));
                }

                values.append("),");
            }

            sql.append(")").append(values).deleteCharAt(sql.length() - 1);

            Connection conn = getConnection();
            boolean success = true;
            if (printLog) LOG_SQL(sql.toString(), bindArgs.toArray());
            try {
                PreparedStatement ps = conn.prepareStatement(sql.toString());
                for (Object o : obj)
                {
                    i = 0;

                    for (Property property : properties)
                    {
                        Object value = property.getValue(o);
                        ps.setObject(++i, value);
                    }
                    
                    ps.addBatch();
                }
                
                int[] results = ps.executeBatch();
                for (int result : results)
                {
                    if (result != 1)
                    {
                        success = false;
                    }
                }

                return success;
            } finally {
                conn.close();
            }
        } catch (Exception e) {
            processException(e);
        }

        return false;
    }

    /**
     * 更新或删除数据
     */
    public <T> DAOEditBuilder<T> edit(Class<T> c) {
        return new DAOEditBuilder<T>(c);
    }

    /**
     * 根据主键删除某条数据
     *
     * @param obj JavaBean对象，映射到数据库的一张表
     * @return 是否删除成功
     */
    public <T> boolean remove(T obj) {
        checkNull(obj);

        @SuppressWarnings("unchecked")
        DAOEditBuilder<T> builder = new DAOEditBuilder<T>((Class<T>) obj.getClass());

        try {
            PrimaryKey primaryKey = builder.table.getPrimaryKey();
            if (primaryKey != null)
            {
                return builder.where(DAOExpression
                    .create(primaryKey.getColumn())
                    .eq(primaryKey.getValue(obj)))
                .delete();
            }
        } catch (Exception e) {
            processException(e);
        }

        return false;
    }

    /**
     * 根据主键更新某一条数据
     *
     * @param obj JavaBean对象，映射到数据库的一张表
     * @param fields 需要修改的字段，不设置则修改所有字段
     * @return 是否更新成功
     */
    public <T> boolean update(T obj, String... fields) {
        checkNull(obj);

        @SuppressWarnings("unchecked")
        DAOEditBuilder<T> builder = new DAOEditBuilder<T>((Class<T>) obj.getClass());

        try {
            PrimaryKey primaryKey = builder.table.getPrimaryKey();
            if (primaryKey != null)
            {
                return builder.where(DAOExpression
                    .create(primaryKey.getColumn())
                    .eq(primaryKey.getValue(obj)))
                .update(obj, fields);
            }
        } catch (Exception e) {
            processException(e);
        }

        return false;
    }

    private <T> boolean remove(DAOSQLBuilder<T> builder) {
        Table table = builder.table;

        try {
            StringBuilder sql = new StringBuilder()
            .append("DELETE FROM ")
            .append(table.getTableName());

            LinkedList<Object> bindArgs = new LinkedList<Object>();
            builder.appendWhere(sql, bindArgs);

            if (execute(sql.toString(), bindArgs.toArray(), Integer.class) > 0)
            {
                notifyChange(table, DAOListener.DELETE);
                return true;
            }
        } catch (Exception e) {
            processException(e);
        }

        return false;
    }

    private <T> boolean edit(DAOSQLBuilder<T> builder, T bean, String... fields) {
        checkNull(bean);

        Table table = builder.table;

        try {
            StringBuilder sql = new StringBuilder()
            .append("UPDATE ")
            .append(table.getTableName())
            .append(" SET ");

            ArrayList<Object> bindArgs;

            int i = 0;

            if (fields == null || fields.length == 0)
            {
                Collection<Property> properties = table.getPropertiesWithModifiablePrimaryKey();

                bindArgs = new ArrayList<Object>(properties.size());

                for (Property property : properties)
                {
                    sql .append(i++ > 0 ? "," : "")
                        .append(property.getColumn())
                        .append("=?");
                    bindArgs.add(property.getValue(bean));
                }
            }
            else
            {
                bindArgs = new ArrayList<Object>(fields.length);

                for (String field : fields)
                {
                    Property property = table.getProperty(field);
                    if (property != null)
                    {
                        sql .append(i++ > 0 ? "," : "")
                            .append(property.getColumn())
                            .append("=?");
                        bindArgs.add(property.getValue(bean));
                    }
                }
            }

            builder.appendWhere(sql, bindArgs);

            if (execute(sql.toString(), bindArgs.toArray(), Integer.class) > 0)
            {
                notifyChange(table, DAOListener.UPDATE);
                return true;
            }
        } catch (Exception e) {
            processException(e);
        }

        return false;
    }

    /**
     * 查询数据
     */
    public <T> DAOQueryBuilder<T> find(Class<T> c) {
        return new DAOQueryBuilder<T>(c);
    }

    private void processException(Exception t) {
        DAOException e = new DAOException(t);
        if (conn.get() != null)
        {
            throw e;
        }

        LOG_DAOException(e);
    }

    /**
     * 将结果集所在记录转换为对象
     */
    public static <T> T convertFromResultSet(ResultSet rs, Class<T> c) {
        try {
            return extractFromResultSet(rs, Table.getTable(c), c);
        } catch (Exception e) {
            LOG_DAOException(new DAOException(e));
        }

        return null;
    }

    /******************************* 华丽丽的分割线 *******************************/

    /**
     * 数据库操作的最小单元（对应数据库表的列）<br>
     * 封装一些函数操作
     */
    public static final class DAOParam {

        private final String fieldName;         // 默认识别为域名

        private LinkedList<String> format;      // 执行一些函数操作

        private String param;                   // 缓存参数

        /**
         * @param fieldOrColumn 可以识别映射bean的域，也可以直接操作表的列
         */
        public DAOParam(String fieldOrColumn) {
            fieldName = fieldOrColumn;
        }

        private DAOParam addFormat(String s) {
            if (format == null) format = new LinkedList<String>();
            format.add(s);
            return this;
        }

        public DAOParam count() {
            return addFormat("count");
        }

        public DAOParam max() {
            return addFormat("max");
        }

        public DAOParam min() {
            return addFormat("min");
        }

        public DAOParam avg() {
            return addFormat("avg");
        }

        public DAOParam sum() {
            return addFormat("sum");
        }

        public DAOParam abs() {
            return addFormat("abs");
        }

        public DAOParam upper() {
            return addFormat("upper");
        }

        public DAOParam lower() {
            return addFormat("lower");
        }

        public DAOParam length() {
            return addFormat("length");
        }

        String getParam(Table table) {
            if (param == null)
            {
                String column = fieldName;
                Property property = table.getPropertyByField(column);
                if (property != null)
                {
                    column = property.getColumn();
                }

                param = format(column);
            }

            return param;
        }

        private String format(String column) {
            if (format == null) return column;

            StringBuilder sb = new StringBuilder(column);
            for (String s : format)
            {
                sb.insert(0, s + "(").append(")");
            }

            return sb.toString();
        }
    }

    /**
     * 数据库操作语句，可用来指定查询列
     */
    private static class DAOClause {

        private final LinkedList<DAOParam> params
                = new LinkedList<DAOParam>();
        
        private DAOClause() {}

        public void add(DAOParam param) {
            params.add(param);
        }

        public static DAOClause create(String... params) {
            if (params == null || params.length == 0) return null;

            DAOClause clause = new DAOClause();
            for (String param : params)
            {
                clause.add(new DAOParam(param));
            }

            return clause;
        }

        public static DAOClause create(Object... params) {
            if (params == null || params.length == 0) return null;

            DAOClause clause = new DAOClause();
            for (Object param : params)
            {
                if (param instanceof String)
                {
                    clause.add(new DAOParam((String) param));
                }
                else if (param instanceof DAOParam)
                {
                    clause.add((DAOParam) param);
                }
                else
                {
                    throw new DAOException("parameters only allow String or DAOParam",
                            new IllegalArgumentException());
                }
            }

            return clause;
        }

        public void appendTo(Table table, StringBuilder sql) {
            boolean firstTime = true;
            for (DAOParam param : params)
            {
                if (firstTime)
                {
                    firstTime = false;
                }
                else
                {
                    sql.append(",");
                }

                sql.append(param.getParam(table));
            }
        }
    }

    /**
     * SQL表达式
     */
    public static class DAOExpression {

        protected PropertyCondition condition;

        protected boolean isCombineExpression;

        private DAOExpression() {}

        public static DAOCondition create(String fieldOrColumn) {
            return create(new DAOParam(fieldOrColumn));
        }

        public static DAOCondition create(DAOParam param) {
            DAOExpression expression = new DAOExpression();
            return expression.condition = new PropertyCondition(expression, param);
        }

        public DAOCondition and(String fieldOrColumn) {
            return and(new DAOParam(fieldOrColumn));
        }

        public DAOCondition and(DAOParam param) {
            DAOExpression expression = new DAOExpression();
            return expression.condition = new PropertyCondition(join(expression, " AND "), param);
        }

        public DAOCondition or(String fieldOrColumn) {
            return or(new DAOParam(fieldOrColumn));
        }

        public DAOCondition or(DAOParam param) {
            DAOExpression expression = new DAOExpression();
            return expression.condition = new PropertyCondition(join(expression, " OR "), param);
        }

        protected DAOExpression join(DAOExpression expression, String op) {
            return new DAOCombineExpression(this).join(expression, op);
        }

        protected void appendTo(Table table, StringBuilder sql, List<Object> whereArgs) {
            condition.appendTo(table, sql, whereArgs);
        }

        /**
         * 组合表达式，连接多个子句
         */
        private static class DAOCombineExpression extends DAOExpression {

            private final LinkedList<Pair<DAOExpression, String>> children;

            public DAOCombineExpression(DAOExpression expression) {
                condition = expression.condition;
                isCombineExpression = true;
                children = new LinkedList<Pair<DAOExpression, String>>();
            }

            protected DAOExpression join(DAOExpression expression, String op) {
                children.add(new Pair<DAOExpression, String>(expression, op));
                return this;
            }

            protected void appendTo(Table table, StringBuilder sql, List<Object> whereArgs) {
                super.appendTo(table, sql, whereArgs);

                for (Pair<DAOExpression, String> child : children)
                {
                    sql.append(child.second);

                    DAOExpression expression = child.first;
                    if (expression.isCombineExpression) sql.append("(");
                    expression.appendTo(table, sql, whereArgs);
                    if (expression.isCombineExpression) sql.append(")");
                }
            }
        }

        /**
         * 条件判断，一般用于where子句
         */
        public interface DAOCondition {

            DAOCondition not();

            DAOExpression eq(Object value);

            DAOExpression like(String value);

            DAOExpression between(Object value1, Object value2);

            DAOExpression in(Object... values);

            DAOExpression greaterThan(Object value);

            DAOExpression lessThan(Object value);

            DAOExpression isNull();
        }

        private static class PropertyCondition implements DAOCondition {

            private final DAOExpression expression;

            private final DAOParam param;

            private String op;

            private Object[] values;

            private boolean notIsCalled;

            public PropertyCondition(DAOExpression expression, DAOParam param) {
                this.expression = expression;
                this.param = param;
            }

            private DAOExpression setup(String op, Object... values) {
                this.op = op;
                this.values = values;
                return expression;
            }

            @Override
            public DAOCondition not() {
                notIsCalled = true;
                return this;
            }

            @Override
            public DAOExpression eq(Object value) {
                return setup(notIsCalled ? "<>?" : "=?", value);
            }

            @Override
            public DAOExpression like(String value) {
                return setup((notIsCalled ? " NOT" : "") + " LIKE ?", value);
            }

            @Override
            public DAOExpression between(Object value1, Object value2) {
                return setup((notIsCalled ? " NOT" : "") + " BETWEEN ? AND ?", value1, value2);
            }

            @Override
            public DAOExpression in(Object... values) {
                StringBuilder sb = new StringBuilder(" IN (");
                for (int i = 0; i < values.length; i++)
                {
                    sb.append(i > 0 ? ",?" : "?");
                }

                return setup((notIsCalled ? " NOT" : "") + sb.append(")"), values);
            }

            @Override
            public DAOExpression greaterThan(Object value) {
                return setup(notIsCalled ? "<=?" : ">?", value);
            }

            @Override
            public DAOExpression lessThan(Object value) {
                return setup(notIsCalled ? ">=?" : "<?", value);
            }

            @Override
            public DAOExpression isNull() {
                return setup(notIsCalled ? " IS NOT NULL" : " IS NULL");
            }

            public void appendTo(Table table, StringBuilder sql, List<Object> whereArgs) {
                sql.append(param.getParam(table)).append(op);
                if (values != null)
                {
                    for (Object value : values)
                    {
                        whereArgs.add(value);
                    }
                }
            }
        }
    }

    /******************************* 华丽丽的分割线 *******************************/

    /**
     * This is a convenient utility that helps build SQL语句
     */
    private static class DAOSQLBuilder<T> {

        final Class<T> c;

        final Table table;

        private DAOExpression where;

        public DAOSQLBuilder(Class<T> c) {
            table = Table.getTable(this.c = c);
        }

        public DAOSQLBuilder<T> where(DAOExpression expression) {
            where = expression;
            return this;
        }

        void appendWhere(StringBuilder sql, List<Object> args) {
            if (where != null) where.appendTo(table, sql.append(" WHERE "), args);
        }
    }

    public class DAOEditBuilder<T> extends DAOSQLBuilder<T> {

        DAOEditBuilder(Class<T> c) {
            super(c);
        }

        @Override
        public DAOEditBuilder<T> where(DAOExpression expression) {
            super.where(expression);
            return this;
        }

        /**
         * 删除数据
         *
         * @return 是否有数据被删除
         */
        public boolean delete() {
            return remove(this);
        }

        /**
         * 修改数据
         *
         * @param bean JavaBean对象，映射到数据库的一张表
         * @param fields 需要修改的字段，不设置则修改所有字段
         * @return 是否有数据更改
         */
        public boolean update(T bean, String... fields) {
            return edit(this, bean, fields);
        }
    }

    /**
     * This is a convenient utility that helps build 数据库查询语句
     */
    public class DAOQueryBuilder<T> extends DAOSQLBuilder<T> {

        private DAOClause selection;                // 查询指定列

        private boolean isDistinct;                 // 消除重复的记录

        private DAOClause group;                    // 按条件进行分组

        private DAOExpression having;               // 分组上设置条件

        private DAOClause order;                    // 按指定顺序显示

        private boolean orderDesc;                  // 按降序进行排列

        private Page page;                          // 分页工具

        DAOQueryBuilder(Class<T> c) {
            super(c);
        }

        public DAOQueryBuilder<T> select(Object... params) {
            selection = DAOClause.create(params);
            return this;
        }

        public DAOQueryBuilder<T> distinct() {
            isDistinct = true;
            return this;
        }

        @Override
        public DAOQueryBuilder<T> where(DAOExpression expression) {
            super.where(expression);
            return this;
        }

        public DAOQueryBuilder<T> groupBy(Object... params) {
            group = DAOClause.create(params);
            return this;
        }

        public DAOQueryBuilder<T> having(DAOExpression expression) {
            having = expression;
            return this;
        }

        public DAOQueryBuilder<T> orderBy(Object... params) {
            orderDesc = false;
            order = DAOClause.create(params);
            return this;
        }

        public DAOQueryBuilder<T> orderDesc(Object... params) {
            orderDesc = true;
            order = DAOClause.create(params);
            return this;
        }

        /**
         * 使用分页技术
         */
        public DAOQueryBuilder<T> usePage(Page page) {
            this.page = page;
            return this;
        }

        private static final int CONSTRAINT_COUNT = 1;

        private static final int CONSTRAINT_LIMIT = 2;

        private final StringBuilder sql = new StringBuilder(120);

        private final LinkedList<Object> args = new LinkedList<Object>();

        private void build(int constraint) {
            StringBuilder sql = this.sql;
            LinkedList<Object> args = this.args;

            appendSelection(sql, constraint);
            appendWhere(sql, args);
            appendGroup(sql);
            appendHaving(sql, args);
            appendOrder(sql);

            if (constraint == CONSTRAINT_LIMIT)
            {
                sql.append(" LIMIT 1");
            }
            else if (page != null)
            {
                sql
                .append(" LIMIT ")
                .append(page.getBeginRecord())
                .append(",")
                .append(page.getPageSize());
            }
        }

        private void appendSelection(StringBuilder sql, int constraint) {
            sql.append("SELECT ");

            if (constraint == CONSTRAINT_COUNT)
            {
                sql.append("COUNT(*)");
            }
            else
            {
                if (isDistinct)
                {
                    sql.append("DISTINCT ");
                }

                if (selection == null)
                {
                    sql.append("*");
                }
                else
                {
                    selection.appendTo(table, sql);
                }
            }

            sql.append(" FROM ").append(table.getTableName());
        }

        private void appendGroup(StringBuilder sql) {
            if (group != null) group.appendTo(table, sql.append(" GROUP BY "));
        }

        private void appendHaving(StringBuilder sql, List<Object> args) {
            if (having != null) having.appendTo(table, sql.append(" HAVING "), args);
        }

        private void appendOrder(StringBuilder sql) {
            if (order != null)
            {
                order.appendTo(table, sql.append(" ORDER BY "));
                if (orderDesc) sql.append(" DESC");
            }
        }

        private String getSql() {
            String s = sql.toString();
            sql.setLength(0);
            return s;
        }

        private String[] getArgs() {
            if (args.isEmpty()) return null;
            String[] strs = convertArgs(args);
            args.clear();
            return strs;
        }
        
        private String[] convertArgs(List<Object> args) {
            String[] strs = new String[args.size()];
            ListIterator<Object> iter = args.listIterator();
            int index = 0;
            while (iter.hasNext())
            {
                strs[index++] = String.valueOf(iter.next());
            }
            
            return strs;
        }

        /**
         * 获取符合条件数据的数量
         */
        public long getCount() {
            build(CONSTRAINT_COUNT);
            return queryCount(getSql(), getArgs());
        }

        /**
         * 返回结果集
         */
        public ResultSet getResultSet() {
            build(0);
            return queryResultSet(getSql(), getArgs());
        }

        /**
         * 获取数据表里第一条满足条件的数据，如没有则返回Null
         */
        public T get() {
            build(CONSTRAINT_LIMIT);
            try {
                ResultSet rs = execute(getSql(), getArgs(), ResultSet.class);
                if (rs != null)
                {
                    try {
                        if (rs.first())
                        {
                            return extractFromResultSet(rs, table, c);
                        }
                    } finally {
                        rs.close();
                    }
                }
            } catch (Exception e) {
                processException(e);
            }

            return null;
        }
        
        /**
         * 获取满足条件的数据列表
         */
        public List<T> getAll() {
            build(0);
            try {
                ResultSet rs = execute(getSql(), getArgs(), ResultSet.class);
                if (rs != null)
                {
                    try {
                        List<T> list = new ArrayList<T>(getRowCount(rs));
                        while (rs.next())
                        {
                            list.add(extractFromResultSet(rs, table, c));
                        }

                        return list;
                    } finally {
                        rs.close();
                    }
                }
            } catch (Exception e) {
                processException(e);
            }

            return null;
        }
        
        private int getRowCount(ResultSet rs) throws Exception {
            rs.last();
            int rowCount = rs.getRow();
            rs.beforeFirst();
            return rowCount;
        }

        /**
         * 创建视图
         *
         * @param viewName 视图名称
         */
        public void createView(String viewName) {
            checkNull(viewName);

            StringBuilder sql = new StringBuilder()
            .append("CREATE VIEW ")
            .append(viewName)
            .append(" AS ");

            build(0);
            execute(sql.append(getSql()).toString(), getArgs());
        }
    }

    static
    {
        LogFactory.addLogFile(DAOTemplate.class, "dao.txt");
    }

    private static void LOG_SQL(String sql) {
        log("执行SQL语句", sql);
    }

    private static void LOG_SQL(String sql, Object[] bindArgs) {
        if (bindArgs != null)
        {
            StringBuilder sb = new StringBuilder(sql);
            int i = 0, index = 0;

            while ((index = sb.indexOf("?", index)) >= 0)
            {
                String arg = String.valueOf(bindArgs[i++]);
                sb.replace(index, index + 1, arg);
                index += arg.length();
            }
            
            sql = sb.toString();
        }

        LOG_SQL(sql);
    }

    private static void LOG_DAOException(DAOException e) {
        log("数据库操作异常", e);
    }
}

class Property {

    private final Field field;                    // 对应JavaBean的域

    private final String fieldName;               // JavaBean变量名称

    private final String column;                  // 对应DataBase的列

    public Property(Field field, DAOProperty property) {
        this(field, property.column());
    }

    public Property(Field field, String column) {
        fieldName = (this.field = field).getName();
        this.column = TextUtils.isEmpty(column) ? fieldName : column;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getColumn() {
        return column;
    }

    /**
     * 获取数据类型
     */
    public Class<?> getDataType() {
        return field.getType();
    }

    public Object getValue(Object obj) throws Exception {
        if (!field.isAccessible()) field.setAccessible(true);
        return field.get(obj);
    }

    public void setValue(Object obj, Object value) throws Exception {
        if (!field.isAccessible()) field.setAccessible(true);
        field.set(obj, value);
    }
}

class PrimaryKey extends Property {

    private final boolean asInteger;

    private final boolean isAutoincrement;

    public PrimaryKey(Field field, DAOPrimaryKey primaryKey) {
        super(field, primaryKey.column());
        Class<?> dataType = getDataType();
        asInteger = dataType == Integer.class || dataType == int.class
                 || dataType == Long.class || dataType == long.class
                 || dataType == Short.class || dataType == short.class
                 || dataType == Byte.class || dataType == byte.class;
        isAutoincrement = primaryKey.autoincrement() && asInteger;
    }

    public boolean isAutoincrement() {
        return isAutoincrement;
    }

    public boolean asInteger() {
        return asInteger;
    }
}

class Table {

    private static final ConcurrentHashMap<String, Table> tables =
            new ConcurrentHashMap<String, Table>(); // 类名为索引

    private final String tableName;

    private PrimaryKey primaryKey;

    private final HashMap<String, Property> propertiesByField =
            new HashMap<String, Property>(); // 域名为索引

    private final HashMap<String, Property> propertiesByColumn =
            new HashMap<String, Property>(); // 列名为索引

    public Table(Class<?> c) {
        tableName = getTableName(c);

        Field[] fields = c.getDeclaredFields();
        for (Field field : fields)
        {
            // 过滤主键
            DAOPrimaryKey primaryKey = field.getAnnotation(DAOPrimaryKey.class);
            if (primaryKey != null && this.primaryKey == null)
            {
                this.primaryKey = new PrimaryKey(field, primaryKey);
            }
            else
            {
                DAOProperty property = field.getAnnotation(DAOProperty.class);
                if (property != null)
                {
                    Property p = new Property(field, property);
                    propertiesByField.put(p.getFieldName(), p);
                    propertiesByColumn.put(p.getColumn(), p);
                }
            }
        }
    }

    private static String getTableName(Class<?> c) {
        DAOTable table = c.getAnnotation(DAOTable.class);
        if (table != null)
        {
            String name = table.name();
            if (name != null && name.trim().length() > 0)
            {
                return name;
            }
        }

        // 当没有注解的时候默认用类的名称作为表名
        return c.getSimpleName();
    }

    public static Table getTable(Class<?> c) {
        String name = c.getName();
        Table table = tables.get(name);
        if (table == null)
        {
            tables.putIfAbsent(name, new Table(c));
            table = tables.get(name);
        }

        return table;
    }

    public String getTableName() {
        return tableName;
    }

    public PrimaryKey getPrimaryKey() {
        return primaryKey;
    }

    public Collection<Property> getPropertiesWithoutPrimaryKey() {
        return Collections.unmodifiableCollection(propertiesByField.values());
    }

    public Collection<Property> getPropertiesWithPrimaryKey() {
        Collection<Property> properties = propertiesByField.values();
        if (primaryKey != null)
        {
            ArrayList<Property> list = new ArrayList<Property>(properties.size() + 1);
            list.add(primaryKey);
            list.addAll(properties);
            
            properties = list;
        }

        return Collections.unmodifiableCollection(properties);
    }

    public Collection<Property> getPropertiesWithModifiablePrimaryKey() {
        Collection<Property> properties = propertiesByField.values();
        if (primaryKey != null && !primaryKey.isAutoincrement())
        {
            ArrayList<Property> list = new ArrayList<Property>(properties.size() + 1);
            list.add(primaryKey);
            list.addAll(properties);
            
            properties = list;
        }

        return Collections.unmodifiableCollection(properties);
    }

    public Property getPropertyByField(String fieldName) {
        if (primaryKey != null && primaryKey.getFieldName().equals(fieldName))
        {
            return primaryKey;
        }

        return propertiesByField.get(fieldName);
    }

    public Property getPropertyByColumn(String column) {
        if (primaryKey != null && primaryKey.getColumn().equals(column))
        {
            return primaryKey;
        }

        return propertiesByColumn.get(column);
    }

    public Property getProperty(String name) {
        if (primaryKey != null
        && (primaryKey.getFieldName().equals(name)
        ||  primaryKey.getColumn().equals(name)))
        {
            return primaryKey;
        }

        Property property = propertiesByField.get(name);
        if (property == null)
        {
            property = propertiesByColumn.get(name);
        }

        return property;
    }
}

class DAOUtil {

    public static void checkNull(Object obj) {
        if (obj != null) return;
        
        String message;
        StackTraceElement stack = getCallerStackFrame();
        if (stack != null)
        {
            message = String.format("Argument passed to %s[%d] cannot be null",
                    stack.getMethodName(), stack.getLineNumber());
        }
        else
        {
            message = "Argument cannot be null";
        }
    
        throw new DAOException("你故意的吧！", new NullPointerException(message));
    }

    public static <T> T extractFromResultSet(ResultSet rs, Table table, Class<T> c)
            throws Exception {
        T o = c.newInstance();
        int columnCount = rs.getMetaData().getColumnCount();
        if (columnCount > 0)
        {
            for (int columnIndex = columnCount; columnIndex > 0; columnIndex--)
            {
                String columnName = rs.getMetaData().getColumnName(columnIndex);
                Property property = table.getPropertyByColumn(columnName);
                if (property != null)
                {
                    Object value = rs.getObject(columnIndex);
                    if (value != null)
                    {
                        property.setValue(o, value);
                    }
                }
            }
        }

        return o;
    }

    public static class DAOException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public DAOException(Throwable throwable) {
            super(throwable);
        }

        public DAOException(String detailMessage, Throwable throwable) {
            super(detailMessage, throwable);
        }
    }
}