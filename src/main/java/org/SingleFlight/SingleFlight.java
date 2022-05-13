package org.SingleFlight;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

/*
 T: 内容唯一key的类型
 R: 存储的内容类型
 */
public class SingleFlight<K, V> {
    private ConcurrentHashMap<K, SegmentLock<V>> map = new ConcurrentHashMap<>();

    public Result<V> setResult(K key, Task<V> supplier) throws Throwable {

        if (key == null) throw new NullPointerException();
        SegmentLock<V> oldSegmentLock = map.get(key); // 查：如果能查的到那么就是能用的，有可能是已经生产好的，也有可能正在生产
        if (oldSegmentLock != null) {
//            System.out.println("消费1 key：" + key);
//            oldSegmentLock.count.incrementAndGet();
            return getResult(oldSegmentLock);
        }
        SegmentLock<V> segmentLock;
        segmentLock = new SegmentLock<>();
        oldSegmentLock = map.putIfAbsent(key, segmentLock); // 增：防止了两个生产者同时生产，ConcurrentHashMap的putIfAbsent是线程安全的
        if (oldSegmentLock == null) { // 这里是怕两个生产者，抢着并发生产 double check key值
//            System.out.println("生产 key：" + key);
            V v;
            try {
                v = supplier.get(); // 开始阻塞同步生产
                segmentLock.result.complete(v);
//                segmentLock.isProduced = true;
                if (!map.remove(key, segmentLock)) { // 防止删除错误的元素
//                    System.out.println("移除元素失败！");
                }
//                log.info("get:" + segmentLock.count.get());
            } catch (Throwable e) { // 捕获生产过程中出现的错误
              segmentLock.result.cancel(false);
              boolean res = map.remove(key, segmentLock);
              throw e;
            }
            return new Result<V>(v, Result.RESULT_TYPE.PRODUCER);
        } else {
//            System.out.println("消费0 key：" + key);
            return getResult(oldSegmentLock);
        }
    }

    private Result<V> getResult(SegmentLock<V> oldSegmentLock) throws ExecutionException, InterruptedException {
        return new Result<V>(oldSegmentLock.result.get(), Result.RESULT_TYPE.CONSUMER);
    }
}
