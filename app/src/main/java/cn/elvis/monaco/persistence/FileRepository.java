package cn.elvis.monaco.persistence;

import io.vertx.core.Future;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.util.concurrent.ArrayBlockingQueue;

public abstract class FileRepository<T> implements Repository<T> {

    private final ArrayBlockingQueue<T> queue;

    private final Thread writer;

    protected final RocksDB db;

    private volatile boolean running = true;

    private volatile boolean finished = false;

    public FileRepository(String name, int writeQueueSize) throws RocksDBException {
        queue = new ArrayBlockingQueue<>(writeQueueSize);
        db = RocksDB.open(name);
        writer = Thread.ofVirtual().name(name).start(() -> {
            while (running) {
                var data = queue.poll();
                if (data == null) {
                    continue;
                }
                write0(data);
            }
            while (!queue.isEmpty()) {
                write0(queue.poll());
            }
            finished = true;
        });
    }

    public Future<Void> stop() {
        running = false;
        return Future.future(promise -> {
            while (!finished) Thread.onSpinWait();
            promise.complete();
        });
    }

    @Override
    public void write(T t) {
        queue.offer(t);
    }

    abstract void write0(T data);
}
