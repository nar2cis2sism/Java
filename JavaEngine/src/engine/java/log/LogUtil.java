package engine.java.log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.UnknownHostException;

public class LogUtil {

    private static final int CURRENT_STACK_FRAME = 3;

    public static final StackTraceElement getCurrentStackFrame() {
        return getStackFrame(CURRENT_STACK_FRAME);
    }

    public static final StackTraceElement getCallerStackFrame() {
        return getStackFrame(CURRENT_STACK_FRAME + 1);
    }

    public static final StackTraceElement getSuperCallerStackFrame() {
        return getStackFrame(CURRENT_STACK_FRAME + 2);
    }

    private static final StackTraceElement getStackFrame(int index) {
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
}