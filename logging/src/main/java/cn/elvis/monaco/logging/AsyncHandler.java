package cn.elvis.monaco.logging; /**
 * 异步 Handler（纯 Java）：使用无界队列 + 独立工作线程。
 * 将 LogRecord 放入队列，由后台线程调用目标 handler.publish()。
 */
class AsyncHandler extends Handler {
    private final Handler target;
    private final BlockingQueue<LogRecord> queue = new LinkedBlockingQueue<>();
    private final Thread worker;
    private volatile boolean running = true;

    public AsyncHandler(Handler target) {
        this.target = Objects.requireNonNull(target);
        this.worker = new Thread(this::drainLoop, "JUL-AsyncHandler");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    private void drainLoop() {
        try {
            while (running || !queue.isEmpty()) {
                LogRecord r = queue.poll(200, TimeUnit.MILLISECONDS);
                if (r != null) {
                    target.publish(r);
                }
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void publish(LogRecord record) {
        if (!isLoggable(record)) return;
        queue.offer(record);
    }

    @Override
    public void flush() {
        target.flush();
    }

    @Override
    public void close() throws SecurityException {
        running = false;
        try { worker.join(1000); } catch (InterruptedException ignored) {}
        target.close();
    }

    @Override
    public boolean isLoggable(LogRecord record) {
        return super.isLoggable(record) && target.isLoggable(record);
    }
}