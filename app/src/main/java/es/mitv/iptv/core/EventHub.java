package es.mitv.iptv.core;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingDeque;

/** Fan-out of small JSON events to every connected phone (one queue per open event stream). */
public final class EventHub {

    /** Put in a queue to make its reader stop. */
    static final String CLOSE = "\u0000close";
    static final int MAX_QUEUED = 64;

    private final CopyOnWriteArrayList<BlockingDeque<String>> queues = new CopyOnWriteArrayList<>();

    public BlockingDeque<String> subscribe() {
        BlockingDeque<String> q = new LinkedBlockingDeque<>();
        queues.add(q);
        return q;
    }

    public void unsubscribe(BlockingDeque<String> q) {
        queues.remove(q);
    }

    public int subscribers() {
        return queues.size();
    }

    /** Queues an event for every subscriber. A slow phone loses its oldest events, never blocks the TV. */
    public void publish(String json) {
        for (BlockingDeque<String> q : queues) {
            while (q.size() >= MAX_QUEUED) q.pollFirst();
            q.offerLast(json);
        }
    }

    /** Wakes every reader up so it can finish (used when the server stops). */
    public void closeAll() {
        for (BlockingDeque<String> q : queues) q.offerLast(CLOSE);
        queues.clear();
    }
}
