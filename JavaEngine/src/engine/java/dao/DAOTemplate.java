package engine.java.dao;

import static engine.java.util.log.LogFactory.LOG.log;
import engine.java.dao.DAOTemplate.DAOClause.DAOParam;
import engine.java.dao.annotation.DAOPrimaryKey;
import engine.java.dao.annotation.DAOProperty;
import engine.java.dao.annotation.DAOTable;
import engine.java.dao.db.DataBaseConnection;
import engine.java.util.Pair;
import engine.java.util.log.LogFactory;
import engine.java.util.log.LogUtil;
import engine.java.util.string.TextUtils;

import java.lang.reflect.Array;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 操作数据库的模板，尽量面向对象，以简化DAO层<br>
 * 不支持多表联合操作，使用原生SQL语句或事务处理效率更高<br>
 * 
 * @author Daimon
 * @version N
 * @since 4/5/2015
 */

public final class DAOTemplate {

    private boolean printLog = true;

	private final DataBaseConnection db;

    /**
     * 数据库表监听器
     */

    public static interface DAOListener {

        /** Daimon:标志位 **/
        public static final int ALL     = ~0;

        public static final int INSERT  = 1 << 0;

        public static final int DELETE  = 1 << 1;

        public static final int UPDATE  = 1 << 2;

        public void onChange();
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
            if (this == o)
            {
                return true;
            }
            
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

    public DAOTemplate(DataBaseConnection db) {
    	this.db = db;
    }

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
        if (observers != null && !observers.isEmpty())
        {
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
        for (DAOObserver observer : observers)
        {
            if (observer.hasChange(op))
            {
                observer.notifyChange();
            }
        }
    }

    /**
     * 默认打印数据库执行语句，如有性能问题可以关闭
     */

    public void disablePrintLog(boolean disable) {
        printLog = !disable;
    }

    /**
     * 执行SQL语句，一般用来执行建表语句
     * 
     * @param sql 遵循数据库语法规则，用;隔开
     */

    public void execute(String sql) {
        if (TextUtils.isEmpty(sql))
        {
            return;
        }

        Connection conn = db.getConnection();
        if (conn == null)
        {
        	log("取不到数据库连接");
        }
        else
        {
            try {
                Statement st = conn.createStatement();
                try {
                    String[] strs = sql.split(";");
                    for (String s : strs)
                    {
                        if (printLog)
                            LOG_SQL(s);
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
    }

    /**
     * 执行一条SQL语句
     */

    public void execute(String sql, Object[] bindArgs) {
        if (TextUtils.isEmpty(sql))
        {
            return;
        }

        try {
    		execute(sql, bindArgs, Boolean.class);
    	} catch (Exception e) {
			processException(e);
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

	private <D> D execute(String sql, Object[] bindArgs, Class<D> returnType) throws Exception {
        if (printLog)
            LOG_SQL(sql, bindArgs);

        Connection conn = db.getConnection();
        if (conn == null)
        {
        	throw new Exception("取不到数据库连接");
        }
        
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
     * 创建索引
     * 
     * @param c JavaBean类
     * @param indexName 索引名称
     * @param clause 在指定列上创建索引
     */

    public void createIndex(Class<?> c, String indexName, DAOClause clause) {
        checkNull(indexName);

        if (clause == null)
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

        clause.appendTo(table, sql);

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
     * 创建视图
     * 
     * @param viewName 视图名称
     */

    public <T> void createView(String viewName, DAOQueryBuilder<T> builder) {
        checkNull(viewName);
        checkNull(builder);

        StringBuilder sql = new StringBuilder()
                .append("CREATE VIEW ")
                .append(viewName)
                .append(" AS ");

        builder.build(0);
        execute(sql.append(builder.sql).toString(), builder.selectionArgs.toArray());
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
                    .append(primaryKey.isAsInteger() ?
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
            sql
                .append(";UPDATE sqlite_sequence SET seq=0 WHERE name='")
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
                new String[] { Table.getTable(c).getTableName() }) > 0;
    }

    /**
     * 保存单条数据
     * 
     * @param obj JavaBean对象，映射到数据库的一张表
     * @return 是否有数据保存
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
                sql.append(i++ > 0 ? "," : "");
                sql.append(property.getColumn());
            }
            
            sql.append(")").append(" VALUES (");

            List<Object> bindArgs = new ArrayList<Object>(properties.size());

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
     * @return 是否保存成功
     */

    public <T> boolean save(T... obj) {
        checkNull(obj);

        if (obj.length == 0)
        {
            return false;
        }
        
        if (obj.length == 1)
        {
        	return save(obj[0]);
        }

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
                sql.append(i > 0 ? "," : "");
                sql.append(property.getColumn());
                values.append(i > 0 ? ",?" : "(?");
                i++;
            }

            sql.append(")").append(values).append(")");

            Connection conn = db.getConnection();
            if (conn == null)
            {
            	throw new Exception("取不到数据库连接");
            }
            
            boolean success = true;
            conn.setAutoCommit(false);
            if (printLog)
                LOG_SQL("事务开始");
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
     * 删除数据
     * 
     * @return 是否有数据被删除
     */

    public <T> boolean remove(DAOSQLBuilder<T> builder) {
        checkNull(builder);

        Table table = builder.table;

        try {
            StringBuilder sql = new StringBuilder()
                    .append("DELETE FROM ")
                    .append(table.getTableName());

            List<Object> bindArgs = new LinkedList<Object>();
            builder.appendWhereClause(sql, bindArgs);

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

    /**
     * 修改数据
     * 
     * @param bean JavaBean对象，映射到数据库的一张表
     * @param fields 需要修改的字段，不设置则修改所有字段
     * @return 是否有数据更改
     */

    public <T> boolean edit(DAOSQLBuilder<T> builder, T bean, String... fields) {
        checkNull(builder);
        checkNull(bean);

        Table table = builder.table;

        try {
            StringBuilder sql = new StringBuilder()
                    .append("UPDATE ")
                    .append(table.getTableName())
                    .append(" SET ");

            List<Object> bindArgs;

            int i = 0;

            if (fields == null || fields.length == 0)
            {
                Collection<Property> properties = table.getPropertiesWithModifiablePrimaryKey();

                bindArgs = new ArrayList<Object>(properties.size());

                for (Property property : properties)
                {
                    sql.append(i++ > 0 ? "," : "");
                    sql.append(property.getColumn());
                    sql.append("=?");
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
                        sql.append(i++ > 0 ? "," : "");
                        sql.append(property.getColumn());
                        sql.append("=?");
                        bindArgs.add(property.getValue(bean));
                    }
                }
            }

            builder.appendWhereClause(sql, bindArgs);

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
     * 
     * @param returnType 返回数据类型
     * @return (ResultSet)结果集<br>
     *         (Long)符合条件数据的数量<br>
     *         (T)数据表里第一条满足条件的数据，如没有则返回Null<br>
     *         (T[])封装好的对象数组
     */

    public <T, D> D find(DAOQueryBuilder<T> builder, Class<D> returnType) {
        checkNull(builder);

        Table table = builder.table;

        try {
            ResultSet rs = null;
            try {
                if (returnType == ResultSet.class)
                {
                    builder.build(0);
                    List<String> selectionArgs = builder.selectionArgs;
                    return returnType.cast(execute(builder.sql,
                            selectionArgs.toArray(new String[selectionArgs.size()]), returnType));
                }
                else if (returnType == Long.class)
                {
                    builder.build(DAOQueryBuilder.CONSTRAINT_COUNT);
                    List<String> selectionArgs = builder.selectionArgs;
                    return returnType.cast(execute(builder.sql,
                            selectionArgs.toArray(new String[selectionArgs.size()]), returnType));
                }
                else if (returnType == builder.c)
                {
                    builder.build(DAOQueryBuilder.CONSTRAINT_LIMIT);
                    List<String> selectionArgs = builder.selectionArgs;
                    rs = execute(builder.sql,
                            selectionArgs.toArray(new String[selectionArgs.size()]), ResultSet.class);
                	if (rs != null && rs.first())
                	{
                        return extractFromResultSet(rs, table, returnType);
                	}
                }
                else if (returnType.isArray())
                {
                    Class<?> arrayType = returnType.getComponentType();
                    if (arrayType == builder.c)
                    {
                        builder.build(0);
                        List<String> selectionArgs = builder.selectionArgs;
                        rs = execute(builder.sql,
                                selectionArgs.toArray(new String[selectionArgs.size()]), ResultSet.class);
                        if (rs != null)
                        {
                            Class<T> c = builder.c;
                            @SuppressWarnings("unchecked")
                            T[] array = (T[]) Array.newInstance(c, getRowCount(rs));
                            int index = 0;

                            while (rs.next())
                            {
                                array[index++] = extractFromResultSet(rs, table, c);
                            }

                            return returnType.cast(array);
                        }
                    }
                }
            } finally {
                if (rs != null)
                {
                    rs.close();
                }
            }
        } catch (Exception e) {
            processException(e);
        }

        return null;
    }
    
    private static int getRowCount(ResultSet rs) throws Exception {
    	rs.last();
    	int rowCount = rs.getRow();
    	rs.beforeFirst();
    	return rowCount;
    }

    static void checkNull(Object obj) {
        if (obj == null)
        {
            String message;
            StackTraceElement stack = LogUtil.getCallerStackFrame();
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
    }

    private static void processException(Exception t) {
        LOG_DAOException(new DAOException(t));
    }

    public static <T> T convertFromResultSet(ResultSet rs, Class<T> c) {
        try {
            return extractFromResultSet(rs, Table.getTable(c), c);
        } catch (Exception e) {
        	processException(e);
        }

        return null;
    }

    private static <T> T extractFromResultSet(ResultSet rs, Table table, Class<T> c)
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

    /**
     * This is a convenience utility that helps build SQL语句
     */

    public static class DAOSQLBuilder<T> {

        final Class<T> c;

        final Table table;

        DAOExpression whereClause;

        DAOSQLBuilder(Class<T> c) {
            table = Table.getTable(this.c = c);
        }

        public static <T> DAOSQLBuilder<T> create(Class<T> c) {
            return new DAOSQLBuilder<T>(c);
        }

        public DAOSQLBuilder<T> setWhereClause(DAOExpression whereClause) {
            this.whereClause = whereClause;
            return this;
        }

        void appendWhereClause(StringBuilder sql, List<Object> args) {
            if (whereClause != null)
            {
                whereClause.appendTo(table, sql.append(" WHERE "), args);
            }
        }

        static void appendExpression(StringBuilder sql, List<Object> args,
                Table table, DAOExpression expression) {
            if (expression != null)
            {
                expression.appendTo(table, sql, args);
            }
        }

        /**
         * SQL表达式
         */

        public static class DAOExpression {

            PropertyCondition condition;

            boolean isCombineExpression;

            DAOExpression() {}

            public static DAOCondition create(String fieldOrColumn) {
                return create(new DAOParam(fieldOrColumn));
            }

            public static DAOCondition create(DAOParam param) {
                DAOExpression expression = new DAOExpression();
                PropertyCondition condition = new PropertyCondition(expression, param);
                return expression.condition = condition;
            }

            public DAOExpression and(DAOExpression expression) {
                return join(expression, " AND ");
            }

            public DAOExpression or(DAOExpression expression) {
                return join(expression, " OR ");
            }

            DAOExpression join(DAOExpression expression, String op) {
                return new DAOCombineExpression(this).join(expression, op);
            }

            void appendTo(Table table, StringBuilder expression, List<Object> whereArgs) {
                if (condition != null)
                {
                    condition.appendTo(table, expression, whereArgs);
                }
            }

            /**
             * 条件判断，一般用于where子句
             */

            public static interface DAOCondition {

                public DAOCondition not();

                public DAOExpression equal(Object value);

                public DAOExpression like(String value);

                public DAOExpression between(Object value1, Object value2);

                public DAOExpression in(Object... values);

                public DAOExpression greaterThan(Object value);

                public DAOExpression lessThan(Object value);

                public DAOExpression isNull();
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

                private void setup(String op, Object... values) {
                    this.op = op;
                    this.values = values;
                }

                @Override
                public DAOCondition not() {
                    notIsCalled = true;
                    return this;
                }

                @Override
                public DAOExpression equal(Object value) {
                    setup(notIsCalled ? "<>?" : "=?", value);
                    return expression;
                }

                @Override
                public DAOExpression like(String value) {
                    setup((notIsCalled ? " NOT" : "") + " LIKE ?", value);
                    return expression;
                }

                @Override
                public DAOExpression between(Object value1, Object value2) {
                    setup((notIsCalled ? " NOT" : "") + " BETWEEN ? AND ?", value1, value2);
                    return expression;
                }

                @Override
                public DAOExpression in(Object... values) {
                    StringBuilder sb = new StringBuilder(" IN (");

                    for (int i = 0; i < values.length;)
                    {
                        sb.append(i++ > 0 ? ",?" : "?");
                    }

                    setup((notIsCalled ? " NOT" : "") + sb.append(")"), values);
                    return expression;
                }

                @Override
                public DAOExpression greaterThan(Object value) {
                    setup(notIsCalled ? "<=?" : ">?", value);
                    return expression;
                }

                @Override
                public DAOExpression lessThan(Object value) {
                    setup(notIsCalled ? ">=?" : "<?", value);
                    return expression;
                }

                @Override
                public DAOExpression isNull() {
                    setup(notIsCalled ? " IS NOT NULL" : " IS NULL");
                    return expression;
                }

                void appendTo(Table table, StringBuilder expression, List<Object> whereArgs) {
                    expression.append(param.getParam(table)).append(op);
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

        /**
         * 组合表达式，连接多个子句
         */

        private static class DAOCombineExpression extends DAOExpression {

            private final List<Pair<DAOExpression, String>> children;

            public DAOCombineExpression(DAOExpression expression) {
                isCombineExpression = true;
                children = new LinkedList<Pair<DAOExpression, String>>();
                condition = expression.condition;
            }

            DAOExpression join(DAOExpression expression, String op) {
                children.add(new Pair<DAOExpression, String>(expression, op));
                return this;
            }

            void appendTo(Table table, StringBuilder expression, List<Object> whereArgs) {
                super.appendTo(table, expression, whereArgs);

                for (Pair<DAOExpression, String> child : children)
                {
                    expression.append(child.second);

                    DAOExpression clause = child.first;
                    if (clause.isCombineExpression)
                        expression.append("(");
                    clause.appendTo(table, expression, whereArgs);
                    if (clause.isCombineExpression)
                        expression.append(")");
                }
            }
        }
    }

    /**
     * This is a convenience utility that helps build 数据库查询语句
     */

    public static class DAOQueryBuilder<T> extends DAOSQLBuilder<T> {

        DAOClause selectionClause;

        boolean isDistinct;                         // 消除重复的记录

        DAOClause groupClause;                      // 按条件进行分组

        DAOExpression havingClause;                 // 分组上设置条件

        DAOClause orderClause;                      // 按指定顺序显示

        boolean orderDesc;                          // 按降序进行排列

        Page page;                                  // 分页工具

        DAOQueryBuilder(Class<T> c) {
            super(c);
        }

        public static <T> DAOQueryBuilder<T> create(Class<T> c) {
            return new DAOQueryBuilder<T>(c);
        }

        public DAOQueryBuilder<T> setSelectionClause(DAOClause selectionClause) {
            this.selectionClause = selectionClause;
            return this;
        }

        public DAOQueryBuilder<T> setDistinct(boolean isDistinct) {
            this.isDistinct = isDistinct;
            return this;
        }

        @Override
        public DAOQueryBuilder<T> setWhereClause(DAOExpression whereClause) {
            super.setWhereClause(whereClause);
            return this;
        }

        public DAOQueryBuilder<T> setGroupClause(DAOClause groupClause) {
            this.groupClause = groupClause;
            return this;
        }

        public DAOQueryBuilder<T> setHavingClause(DAOExpression havingClause) {
            this.havingClause = havingClause;
            return this;
        }

        public DAOQueryBuilder<T> setOrderClause(DAOClause orderClause) {
            this.orderClause = orderClause;
            return this;
        }

        public DAOQueryBuilder<T> setOrderClause(DAOClause orderClause, boolean desc) {
            orderDesc = desc;
            return setOrderClause(orderClause);
        }

        /**
         * 使用分页技术
         * 
         * @return 需设置分页参数
         */

        public Page usePage() {
            if (page == null)
            {
                page = new Page(10);
            }

            return page;
        }

        /**
         * 停止使用分页技术
         */

        public void stopPage() {
            page = null;
        }

        /** Can not be Null. */
        String sql;

        /** Can not be Null. */
        List<String> selectionArgs;

        static final int CONSTRAINT_COUNT = 1;

        static final int CONSTRAINT_LIMIT = 2;

        void build(int constraint) {
            StringBuilder sql = new StringBuilder(120);
            List<Object> args = new LinkedList<Object>();

            appendSelectionClause(sql, constraint);
            appendWhereClause(sql, args);
            appendGroupClause(sql);
            appendHavingClause(sql, args);
            appendOrderClause(sql);

            if (constraint == CONSTRAINT_LIMIT)
            {
                sql.append(" LIMIT 1");
            }
            else if (page != null)
            {
                sql
                .append(" LIMIT ")
                .append(page.getPageSize())
                .append(",")
                .append(page.getBeginRecord());
            }

            this.sql = sql.toString();
            selectionArgs = convertArgs(args);
        }

        void appendSelectionClause(StringBuilder sql, int constraint) {
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

                if (selectionClause == null)
                {
                    sql.append("*");
                }
                else
                {
                    selectionClause.appendTo(table, sql);
                }
            }

            sql.append(" FROM ").append(table.getTableName());
        }

        void appendGroupClause(StringBuilder sql) {
            appendClause(sql, table, " GROUP BY ", groupClause);
        }

        void appendHavingClause(StringBuilder sql, List<Object> args) {
            if (havingClause != null)
            {
                havingClause.appendTo(table, sql.append(" HAVING "), args);
            }
        }

        void appendOrderClause(StringBuilder sql) {
            appendClause(sql, table, " ORDER BY ", orderClause);
            if (orderDesc)
            {
                sql.append(" DESC");
            }
        }

        static void appendClause(StringBuilder sql, Table table,
                String name, DAOClause clause) {
            if (clause != null)
            {
                clause.appendTo(table, sql.append(name));
            }
        }

        static List<String> convertArgs(Collection<Object> args) {
            if (args == null)
            {
                return null;
            }

            List<String> list = new ArrayList<String>(args.size());
            for (Object arg : args)
            {
                list.add(String.valueOf(arg));
            }

            return list;
        }
    }

    /**
     * 数据库操作语句，可用来指定查询列
     */

    public static class DAOClause {

        private final List<DAOParam> params;

        private DAOClause() {
            params = new LinkedList<DAOParam>();
        }

        public static DAOClause create(DAOParam param) {
            return new DAOClause().add(param);
        }

        public DAOClause add(DAOParam param) {
            params.add(param);
            return this;
        }

        String[] build(Table table) {
            String[] clause = new String[params.size()];
            for (int i = 0, len = clause.length; i < len; i++)
            {
                clause[i] = params.get(i).getParam(table);
            }

            return clause;
        }

        void appendTo(Table table, StringBuilder sql) {
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

        /**
         * 数据库操作的最小单元（对应数据库表的列）<br>
         * 不可重复使用
         */

        public static final class DAOParam {

            private static final String FORMAT_TOKEN = "%s";

            private final String fieldName;         // 默认识别为域名

            private List<String> format;            // 执行一些函数操作

            private String param;

            /**
             * @param fieldOrColumn 可以识别映射bean的域，也可以直接操作表的列
             */

            public DAOParam(String fieldOrColumn) {
                fieldName = fieldOrColumn;
            }

            private DAOParam addFormat(String s) {
                if (format == null)
                {
                    format = new LinkedList<String>();
                }

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

                    param = format(initFormat(), column);
                }

                return param;
            }

            private String initFormat() {
                if (format == null)
                {
                    return null;
                }

                StringBuilder sb = new StringBuilder(FORMAT_TOKEN);
                for (String s : format)
                {
                    sb.insert(0, s + "(").append(")");
                }

                return sb.toString();
            }

            private static String format(String format, String arg) {
                if (TextUtils.isEmpty(format))
                {
                    return arg;
                }

                return format.replaceFirst(FORMAT_TOKEN, arg);
            }
        }
    }

    private static class Property {

        private final String fieldName;               // 对应JavaBean的域

        private final String column;                  // 对应DataBase的列

        private final Class<?> dataType;              // 数据类型

        public Property(Field field, DAOProperty property) {
            this(field.getName(), property.column(), field.getType());
        }

        public Property(String fieldName, String column, Class<?> dataType) {
            if (TextUtils.isEmpty(column))
            {
                column = fieldName;
            }

            this.fieldName = fieldName;
            this.column = column;
            this.dataType = dataType;
        }

        public String getFieldName() {
            return fieldName;
        }

        public String getColumn() {
            return column;
        }

        public Class<?> getDataType() {
            return dataType;
        }

        public Object getValue(Object obj) throws Exception {
            Field field = getField(obj.getClass(), fieldName);
            if (field == null)
            {
                throw new NoSuchFieldException();
            }

            field.setAccessible(true);
            return field.get(obj);
        }

        public void setValue(Object obj, Object value) throws Exception {
            Field field = getField(obj.getClass(), fieldName);
            if (field == null)
            {
                throw new NoSuchFieldException();
            }

            field.setAccessible(true);
            field.set(obj, value);
        }

        /**
         * 利用反射获取类的变量
         * 
         * @param fieldName 变量名称
         */

        private static Field getField(Class<?> c, String fieldName) {
            Field field = null;
            for (; c != Object.class; c = c.getSuperclass())
            {
                try {
                    field = c.getDeclaredField(fieldName);
                    break;
                } catch (Exception e) {
                    // 这里甚么都不要做！
                    // 如果这里的异常打印或者往外抛，就不会往下执行，最后就不会进入到父类中了
                }
            }

            return field;
        }
    }

    private static class PrimaryKey extends Property {

        private final boolean asInteger;

        private final boolean isAutoincrement;

        public PrimaryKey(Field field, DAOPrimaryKey primaryKey) {
            super(field.getName(), primaryKey.column(), field.getType());
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

        public boolean isAsInteger() {
            return asInteger;
        }
    }

    private static class Table {

        private static final ConcurrentHashMap<String, Table> tables =
                new ConcurrentHashMap<String, Table>();// 类名为索引

        private final String tableName;

        private PrimaryKey primaryKey;

        private final HashMap<String, Property> propertiesByField =
                new HashMap<String, Property>();// 域名为索引

        private final HashMap<String, Property> propertiesByColumn =
                new HashMap<String, Property>();// 列名为索引

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

            // 当没有注解的时候默认用类的名称作为表名,并把点(.)替换为下划线(_)
            return c.getSimpleName().replace('.', '_');
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

        public Collection<Property> getPropertiesWithModifiablePrimaryKey() {
            Collection<Property> properties = propertiesByField.values();
            if (primaryKey != null && !primaryKey.isAutoincrement())
            {
                properties = new ArrayList<Property>(properties);
                properties.add(primaryKey);
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
                    || primaryKey.getColumn().equals(name)))
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

    static
    {
    	LogFactory.addLogFile(DAOTemplate.class, "dao.txt");
    }

    private static void LOG_DAOException(DAOException e) {
        log("数据库操作异常", e);
    }

    private static void LOG_SQL(String sql, Object[] bindArgs) {
        if (bindArgs != null)
        {
            int i = 0, index = 0;

            while ((index = sql.indexOf("?", index)) >= 0)
            {
                String arg = String.valueOf(bindArgs[i++]);
                sql = sql.substring(0, index) + arg + sql.substring(++index);
                index += arg.length();
            }
        }

        LOG_SQL(sql);
    }

    private static void LOG_SQL(String sql) {
        log("执行SQL语句", sql);
    }

    private static class DAOException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public DAOException(String detailMessage, Throwable throwable) {
            super(detailMessage, throwable);
        }

        public DAOException(Throwable throwable) {
            super(throwable);
        }
    }
}