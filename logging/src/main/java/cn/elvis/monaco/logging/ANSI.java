package cn.elvis.monaco.logging;

import java.util.logging.Level;

public class ANSI {

    static final String RESET = "\u001B[0m";
    static final String BOLD  = "\u001B[1m";

    static final String FG_GRAY   = "\u001B[90m";
    static final String FG_BLUE   = "\u001B[34m";
    static final String FG_GREEN  = "\u001B[32m";
    static final String FG_YELLOW = "\u001B[33m";
    static final String FG_RED    = "\u001B[31m";

    static String colorFor(Level level) {
        int v = level.intValue();
        if (v >= LogLevel.ERROR.intValue()) return BOLD + FG_RED;
        if (v >= LogLevel.WARN.intValue())  return FG_YELLOW;
        if (v >= LogLevel.INFO.intValue())  return FG_GREEN;
        if (v >= LogLevel.DEBUG.intValue()) return FG_BLUE;
        return FG_GRAY; // TRACE 及更低
    }
}
