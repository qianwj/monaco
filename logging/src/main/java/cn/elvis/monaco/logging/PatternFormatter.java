package cn.elvis.monaco.logging; /**
 * 可编程“样式处理器”（Formatter）
 * 支持的占位符：
 *   %d{pattern}  时间（SimpleDateFormat），默认 HH:mm:ss.SSS
 *   %level       级别名（TRACE/DEBUG/INFO/WARN/ERROR）
 *   %logger      Logger 名称
 *   %thread      线程名
 *   %msg         消息
 *   %n           换行
 *   %source      源类#方法（可能为空）
 *   %epoch       纪元毫秒
 *   %instant     Instant.toString()
 * 如果 useAnsi=true，则为整行按级别着色；ERROR 级别自动附加 stacktrace。
 */
class PatternFormatter extends Formatter {
    private final String pattern;
    private final boolean useAnsi;

    private static final Pattern DATE_TOKEN = Pattern.compile("%d\\{([^}]*)}"); // %d{...}

    PatternFormatter(String pattern, boolean useAnsi) {
        this.pattern = Objects.requireNonNullElse(pattern, "%d{HH:mm:ss.SSS} [%level] %logger - %msg%n");
        this.useAnsi = useAnsi;
    }

    @Override
    public String format(LogRecord r) {
        String s = pattern;

        // 处理 %d{...}
        Matcher m = DATE_TOKEN.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String datePat = m.group(1);
            String repl = new SimpleDateFormat(
                    (datePat == null || datePat.isBlank()) ? "HH:mm:ss.SSS" : datePat
            ).format(new Date(r.getMillis()));
            m.appendReplacement(sb, Matcher.quoteReplacement(repl));
        }
        m.appendTail(sb);
        s = sb.toString();

        // 其它占位符
        String levelName = mapLevelName(r.getLevel());
        String loggerName = r.getLoggerName() != null ? r.getLoggerName() : "";
        String threadName = Thread.currentThread().getName();
        String message = formatMessage(r); // JUL 自带参数替换
        String source;
        if (r.getSourceClassName() != null) {
            source = r.getSourceClassName() + (r.getSourceMethodName() != null ? "#" + r.getSourceMethodName() : "");
        } else {
            source = "";
        }

        s = s.replace("%level", levelName)
             .replace("%logger", loggerName)
             .replace("%thread", threadName)
             .replace("%msg", message)
             .replace("%n", System.lineSeparator())
             .replace("%source", source)
             .replace("%epoch", String.valueOf(r.getMillis()))
             .replace("%instant", Instant.ofEpochMilli(r.getMillis()).toString());

        // ERROR 级别自动追加 stacktrace
        if (r.getLevel().intValue() >= CustomLevels.ERROR.intValue() && r.getThrown() != null) {
            StringWriter sw = new StringWriter();
            r.getThrown().printStackTrace(new PrintWriter(sw));
            s += System.lineSeparator() + sw;
        }

        if (useAnsi) {
            String color = Ansi.colorFor(r.getLevel());
            return color + s + Ansi.RESET;
        }
        return s;
    }

    private static String mapLevelName(Level level) {
        if (level == CustomLevels.TRACE) return "TRACE";
        if (level == CustomLevels.DEBUG) return "DEBUG";
        if (level == Level.INFO)         return "INFO";
        if (level == Level.WARNING)      return "WARN";
        if (level == Level.SEVERE)       return "ERROR";
        // 兼容 FINE/FINEST 映射
        int v = level.intValue();
        if (v <= 350) return "TRACE";
        if (v <= 550) return "DEBUG";
        return level.getName();
    }
}