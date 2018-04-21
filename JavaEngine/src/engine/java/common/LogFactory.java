package engine.java.common;

import engine.java.util.string.StringBuilderPrinter;
import engine.java.util.string.TextUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.UnknownHostException;
import java.util.Calendar;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 日志管理工厂<p>
 * 功能：日志文件映射关系，开关与导出
 * 
 * @author Daimon
 * @version N
 * @since 6/6/2014
 */
public final class LogFactory {

    private static File logDir;                                 // 日志输出目录

    private static final AtomicBoolean logEnabled
    = new AtomicBoolean();                                      // 日志开启状态

    private static final ConcurrentHashMap<String, String> map
    = new ConcurrentHashMap<String, String>();                  // [类名-日志文件]映射表

    private static final ConcurrentHashMap<String, LogFile> logs
    = new ConcurrentHashMap<String, LogFile>();                 // 日志文件查询表

    private static final String DEFAULT_LOG_FILE = "log.txt";   // 默认日志输出文件

    /**
     * 开启/关闭日志记录
     */
    public static void enableLOG(boolean enable) {
        logEnabled.compareAndSet(!enable, enable);
    }
    
    /**
     * 初始化
     * 
     * @param logDir 日志输出目录
     */
    public static void init(File logDir) {
        LogFactory.logDir = logDir;
    }
    
    /**
     * 日志功能是否开启
     */
    public static boolean isLogEnabled() {
        return logEnabled.get();
    }

    /**
     * 获取日志输出目录
     */
    private static synchronized File getLogDir() {
        if (logDir == null)
        {
            throw new NullPointerException("未设置日志输出目录");
        }
        
        if (!logDir.exists())
        {
            logDir.mkdirs();
        }

        return logDir;
    }

    /**
     * 添加日志文件
     */
    public static void addLogFile(Class<?> logClass, String logFile) {
        map.putIfAbsent(logClass.getName(), logFile);
    }

    /**
     * 添加日志映射
     */
    public static void addLogFile(Class<?> logClass, Class<?> mapClass) {
        String logFile = map.get(mapClass.getName());
        if (logFile != null)
        {
            map.putIfAbsent(logClass.getName(), logFile);
        }
    }

    /**
     * 操作日志文件
     */
    private static LogFile getLogFile(String className) {
        String logFile;
        if (TextUtils.isEmpty(className))
        {
            logFile = DEFAULT_LOG_FILE;
        }
        else
        {
            logFile = map.get(className);
            if (logFile == null)
            {
                logFile = DEFAULT_LOG_FILE;
            }
        }

        return getOrCreateLogFile(logFile);
    }

    /**
     * @param logFile 日志文件名称
     */
    private static LogFile getOrCreateLogFile(String logFile) {
        LogFile log = logs.get(logFile);
        if (log == null)
        {
            logs.putIfAbsent(logFile, new LogFile(new File(getLogDir(), logFile)));
            log = logs.get(logFile);
        }
        
        return log;
    }

    /**
     * 需要导出日志时调用此方法获取日志目录（会阻塞当前线程）
     */
    public static File flush() {
        for (LogFile log : logs.values())
        {
            try {
                log.flush();
            } catch (Exception e) {
                System.out.println(new LogFile.LogRecord(
                        LogFactory.class.getName(), 
                        String.format("Failed to flush Log:\n%s,cause:%s", 
                                log, LogUtil.getExceptionInfo(e)), 
                        System.currentTimeMillis()));
            }
        }
        
        return getLogDir();
    }

    /**
     * 日志文件
     */
    private static class LogFile implements Runnable {

        private static final int CAPACITY = 10;

        private static final ConcurrentLinkedQueue<LogRecord> logs
        = new ConcurrentLinkedQueue<LogRecord>();

        private final File logFile;

        private final AtomicBoolean isFlushing = new AtomicBoolean();

        public LogFile(File logFile) {
            this.logFile = logFile;
        }

        public void LOG(LogRecord record) {
            logs.offer(record);
            if (logs.size() >= CAPACITY && isFlushing.compareAndSet(false, true))
            {
                new Thread(this, logFile.getName()).start();
            }
        }

        public synchronized void flush() throws Exception {
            FileWriter fw = null;
            try {
                fw = new FileWriter(logFile, true); // The file will be created
                                                    // if it does not exist.
                LogRecord record;
                while ((record = logs.peek()) != null)
                {
                    fw.append(record.toString()).append('\n');
                    logs.poll();
                }
            } finally {
                if (fw != null)
                {
                    fw.close();
                }
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            StringBuilderPrinter printer = new StringBuilderPrinter(sb);
            LogRecord record;
            while ((record = logs.poll()) != null)
            {
                printer.println(record.toString());
            }

            return sb.toString();
        }

        @Override
        public void run() {
            try {
                flush();
            } catch (Exception e) {
                System.out.println(new LogRecord(
                        getClass().getName(), 
                        String.format("Failed to write into Log (%s),cause:%s", 
                                logFile.getName(), LogUtil.getExceptionInfo(e)), 
                        System.currentTimeMillis()));
            }

            isFlushing.set(false);
        }

        /**
         * 日志记录
         */
        private static class LogRecord {

            private static final Calendar CAL = Calendar.getInstance();

            private final String tag;

            private final String message;

            private final long timeInMillis;

            public LogRecord(String tag, String message, long timeInMillis) {
                this.tag = tag;
                this.message = message;
                this.timeInMillis = timeInMillis;
            }

            @Override
            public String toString() {
                if (TextUtils.isEmpty(tag))
                {
                    if (TextUtils.isEmpty(message))
                    {
                        return "";
                    }

                    return String.format("%s|%s", getTime(), message);
                }

                return String.format("%s|%s|%s", getTime(), getTag(), message);
            }

            public String getTime() {
                CAL.setTimeInMillis(timeInMillis);
                return CalendarFormat.format(CAL, "MM-dd HH:mm:ss.SSS");
            }

            public String getTag() {
                return LogUtil.getFixedText(tag, 40);
            }
        }
    }

    /**
     * 日志输出
     */
    public static final class LOG {
        
        /**
         * 输出日志（空行）
         */
        public static void log() {
            log("", null);
        }

        /**
         * 输出日志（取调用函数的类名+方法名作为标签）
         * 
         * @param message 日志内容
         */
        public static void log(Object message) {
            log(LogUtil.getCallerStackFrame(), message);
        }

        /**
         * 输出日志
         * 
         * @param tag 日志标签
         * @param message 日志内容
         */
        public static void log(String tag, Object message) {
            String className = null;
            StackTraceElement stack = LogUtil.getCallerStackFrame();
            if (stack != null)
            {
                className = stack.getClassName();
            }

            log(className, tag, message, System.currentTimeMillis());
        }

        /**
         * 输出日志
         * 
         * @param stack 取函数的类名+方法名作为标签
         * @param message 日志内容
         */
        public static void log(StackTraceElement stack, Object message) {
            String className = null;
            String tag = null;
            if (stack != null)
            {
                className = stack.getClassName();
                tag = LogUtil.getClassAndMethod(stack);
            }

            log(className, tag, message, System.currentTimeMillis());
        }

        private static void log(String className, String tag, Object message, long timeInMillis) {
            LogFile.LogRecord record = new LogFile.LogRecord(tag, getMessage(message), timeInMillis);
            
            if (isLogEnabled())
            {
                LogFile log = getLogFile(className);
                if (log != null)
                {
                    log.LOG(record);
                }
            }
            else
            {
                System.out.println(record);
            }
        }
        
        private static String getMessage(Object message) {
            if (message instanceof Throwable)
            {
                return LogUtil.getExceptionInfo((Throwable) message);
            }
            else
            {
                return message == null ? "" : message.toString();
            }
        }
    }
    
    public static final class LogUtil {

        private static final int CURRENT_STACK_FRAME = 3;

        /**
         * 获取当前函数堆栈信息
         */
        public static StackTraceElement getCurrentStackFrame() {
            return getStackFrame(CURRENT_STACK_FRAME);
        }

        /**
         * 获取调用函数堆栈信息
         */
        public static StackTraceElement getCallerStackFrame() {
            return getStackFrame(CURRENT_STACK_FRAME + 1);
        }

        /**
         * 顾名思义
         */
        public static StackTraceElement getSuperCallerStackFrame() {
            return getStackFrame(CURRENT_STACK_FRAME + 2);
        }

        private static StackTraceElement getStackFrame(int index) {
            StackTraceElement[] stacks = Thread.currentThread().getStackTrace();
            return stacks.length > ++index ? stacks[index] : null;
        }

        /**
         * Handy function to get a loggable stack trace from a Throwable
         * @param tr An exception to log
         */
        public static String getExceptionInfo(Throwable tr) {
            if (tr == null) {
                return "";
            }

            // This is to reduce the amount of log spew that apps do in the non-error
            // condition of the network being unavailable.
            Throwable t = tr;
            while (t != null) {
                if (t instanceof UnknownHostException) {
                    return "";
                }
                t = t.getCause();
            }

            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            tr.printStackTrace(pw);
            return sw.toString();
        }
        
        /**
         * 提取类名+方法名
         */
        public static String getClassAndMethod(StackTraceElement stack) {
            String className = stack.getClassName();
            return String.format("%s.%s", 
                    className.substring(className.lastIndexOf(".") + 1), 
                    stack.getMethodName());
        }

        /**
         * Daimon:获取固定长度文本
         */
        public static String getFixedText(String text, int length) {
            StringBuilder sb = new StringBuilder();
            int len;

            if (TextUtils.isEmpty(text))
            {
                len = 0;
            }
            else
            {
                sb.append(text);
                len = length(text);
            }

            if (len < length)
            {
                for (int i = len; i < length; i++)
                {
                    sb.append(" ");
                }
            }
            else if (len > length)
            {
                sb.delete(0, sb.length());
                text = substring(text, length - 3);

                sb.append(text).append("...");
                len = length(text) + 3;

                if (len < length)
                {
                    for (int i = len; i < length; i++)
                    {
                        sb.append(" ");
                    }
                }
            }

            return sb.toString();
        }

        private static String substring(String s, int length) {
            int len = length;
            String sub;
            while (length(sub = s.substring(0, len)) > length)
            {
                len--;
            }

            return sub;
        }

        private static int length(String s) {
            return s.replaceAll("[^\\x00-\\xff]", "**").length();
        }
    }
}