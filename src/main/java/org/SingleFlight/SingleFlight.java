package org.SingleFlight;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/*
 T: 内容唯一key的类型
 R: 存储的内容类型
 */
public class SingleFlight<K, V> {
    private ConcurrentHashMap<K, SegmentLock<V>> map = new ConcurrentHashMap<>();
    private long timeout;
    private TimeUnit timeUnit;
    private int flags; // 控制生产者与消费时是否需要等待
    public SingleFlight() {}
    public SingleFlight(long timeout, TimeUnit timeUnit) {
        this(timeout, timeUnit, TIMER_FLAG.CONSUMER_TIME_UP.flag);
    }

    public SingleFlight(long timeout, TimeUnit timeUnit, int flags) {
        if (timeUnit == null) {
            throw new NullPointerException();
        }
        this.timeout = timeout;
        this.timeUnit = timeUnit;
        this.flags = flags;
    }
    public enum TIMER_FLAG{
//        PRODUCER_TIME_UP("PRODUCER_TIME_UP",0x1),
        CONSUMER_TIME_UP("CONSUMER_TIME_OUT", 0x2),
        ;
        public String type;
        public int flag;
        TIMER_FLAG(String type, int flag) {
            this.type = type;
            this.flag = flag;
        }
    }

    public Result<V> setResult(K key, Task<V> supplier) throws Throwable {
        if (key == null) throw new NullPointerException();
        SegmentLock<V> oldSegmentLock = map.get(key); // 查：如果能查的到那么就是能用的，有可能是已经生产好的，也有可能正在生产
        if (oldSegmentLock != null) {
            return getResult(oldSegmentLock);
        }
        SegmentLock<V> segmentLock;
        segmentLock = new SegmentLock<>();
        oldSegmentLock = map.putIfAbsent(key, segmentLock); // 增：防止了两个生产者同时生产，ConcurrentHashMap的putIfAbsent是线程安全的
        if (oldSegmentLock == null) { // 这里是怕两个生产者，抢着并发生产 double check key值
            V v;
            try {
                v = supplier.get(); // 开始阻塞同步生产
                segmentLock.result.complete(v);
                if (!map.remove(key, segmentLock)) { // 防止删除错误的元素
                }
            } catch (Throwable e) { // 捕获生产过程中出现的错误
              segmentLock.result.cancel(false);
              boolean res = map.remove(key, segmentLock);
              throw e;
            }
            return new Result<V>(v, Result.RESULT_TYPE.PRODUCER);
        } else {
            return getResult(oldSegmentLock);
        }
    }

    private Result<V> getResult(SegmentLock<V> oldSegmentLock) throws ExecutionException, InterruptedException {
        if ((this.flags & TIMER_FLAG.CONSUMER_TIME_UP.flag) > 0) {
            try {
                return new Result<V>(oldSegmentLock.result.get(this.timeout, this.timeUnit), Result.RESULT_TYPE.CONSUMER);
            } catch (TimeoutException e) {
                e.printStackTrace();
                return new Result<V>(null, Result.RESULT_TYPE.CONSUMER_TIME_OUT);
            }
        } else {
            return new Result<V>(oldSegmentLock.result.get(), Result.RESULT_TYPE.CONSUMER);
        }

    }
}
