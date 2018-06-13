package engine.java.util.log;

class StringBuilderPrinter {
    private final StringBuilder mBuilder;
    
    /**
     * Create a new Printer that sends to a StringBuilder object.
     * 
     * @param builder The StringBuilder where you would like output to go.
     */
    public StringBuilderPrinter(StringBuilder builder) {
        mBuilder = builder;
    }
    
    public void println(String x) {
        mBuilder.append(x);
        int len = x.length();
        if (len <= 0 || x.charAt(len-1) != '\n') {
            mBuilder.append('\n');
        }
    }
}