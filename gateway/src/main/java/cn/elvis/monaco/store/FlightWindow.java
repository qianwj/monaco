package cn.elvis.monaco.store;

import cn.elvis.monaco.entity.PublishMessage;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Flight window is subscription message queue that allow received message which previous message is unacknowledged.
 *
 * @author qianwj
 * @since  0.0.1
 */
public final class FlightWindow {

    private final LinkedList<PublishMessage> waitingQueue = new LinkedList<>();

    private final int threshold;

    private final Set<Integer> acknowledgeList = new HashSet<>();

    private AtomicInteger ptr = new AtomicInteger(0);

    public FlightWindow(int threshold) {
        this.threshold = threshold;
    }

    public boolean offer(PublishMessage message) {
        while (true) {
            PublishMessage head = waitingQueue.peek();
            if (head == null) {
                return waitingQueue.offer(message);
            }
            if (acknowledgeList.contains(head.packetId())) {
                waitingQueue.poll();
                acknowledgeList.remove(head.packetId());
            } else {
                if (waitingQueue.size() - acknowledgeList.size() < threshold) {
                    return waitingQueue.offer(message);
                }
                return false;
            }
        }
    }

    public void acknowledge(int packetId) {
        acknowledgeList.add(packetId);
        while (true) {
            PublishMessage head = waitingQueue.peek();
            if (head == null) {
                break;
            }
            if (acknowledgeList.contains(head.packetId())) {
                waitingQueue.poll();
                acknowledgeList.remove(head.packetId());
            } else {
                break;
            }
        }
    }

    public PublishMessage poll() {
        while (true) {
            PublishMessage head = waitingQueue.peek();
            if (head == null) {
                return null;
            }
            if (acknowledgeList.contains(head.packetId())) {
                waitingQueue.poll();
                acknowledgeList.remove(head.packetId());
            } else {
                break;
            }
        }
        if (ptr.get() < waitingQueue.size()) {
            return waitingQueue.get(ptr.incrementAndGet());
        }
        ptr.set(0);
        return waitingQueue.peek();
    }
}
