package org.SingleFlight;
import java.util.concurrent.CompletableFuture;

public class SegmentLock<V>{
//    public Boolean isProduced = false; // 测试用字段，保留
//    public AtomicInteger count = new AtomicInteger();
    public SegmentLock() {
    }

    public CompletableFuture<V> result = new CompletableFuture<>();

}
