package cn.elvis.monaco.logging;

import java.util.logging.Level;

public final class LogLevel {

    // JUL 中：FINEST=300, FINER=400, FINE=500, CONFIG=700, INFO=800, WARNING=900, SEVERE=1000
    public static final Level TRACE = new Level("TRACE", 350) {};
    public static final Level DEBUG = new Level("DEBUG", 550) {};
    public static final Level INFO  = Level.INFO;        // 800
    public static final Level WARN  = Level.WARNING;     // 900
    public static final Level ERROR = new Level("ERROR", 950) {};
}
