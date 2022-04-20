package org.SingleFlight;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/*
 T: 内容唯一key的类型
 R: 存储的内容类型
 */
public class SingleFlight<K, V> {


    private ConcurrentHashMap<K, SegmentLock<V>> map = new ConcurrentHashMap<>();

    public V setResult(K key, Task<V> supplier) throws Throwable {
        if (key == null) throw new NullPointerException();
        SegmentLock<V> oldSegmentLock = map.get(key); // 查 如果能查的到那么就是能用的，有可能是已经生产好的，也有可能正在生产
        if (oldSegmentLock != null) {
//            System.out.println("消费1 key：" + key);
            return getResult(oldSegmentLock);
        }
        SegmentLock<V> segmentLock;
        segmentLock = new SegmentLock<>();
        segmentLock.writeLock.lock();
        oldSegmentLock = map.putIfAbsent(key, segmentLock); // 增 防止了两个生产者同时生产，ConcurrentHashMap的put是线程安全的
        if (oldSegmentLock == null) { // 这里是怕两个生产者，抢着并发生产 double check key值
//            System.out.println("生产 key：" + key);
            V v;
            try {
                v = supplier.get();
                segmentLock.result = v;
                segmentLock.isProduced = true;
                if (!map.remove(key, segmentLock)) { // 防止删除错误的元素
                    System.out.println("移除元素失败！");
                }
            } catch (Throwable e) {
              map.remove(key, segmentLock);
              throw e;
            } finally {
                segmentLock.writeLock.unlock();
            }
            return v;
        } else {
//            System.out.println("消费0 key：" + key);
            return getResult(oldSegmentLock);
        }
    }

    private V getResult(SegmentLock<V> oldSegmentLock) {
        if (!oldSegmentLock.isProduced) {
            try {
//                Long start = System.currentTimeMillis();
                oldSegmentLock.readLock.lock();  // 进行等待
//                System.out.println("读锁消耗时间：" + String.valueOf(System.currentTimeMillis() - start));
//                System.out.println("结果是:" + oldSegmentLock.result);
            } finally {
                oldSegmentLock.readLock.unlock();
            }
        }
        return oldSegmentLock.result;
    }

    // TODO 建议在redis缓存消失的时候，调用这一个库，得到结果后再重新设置缓存
    // TODO 线程错误处理 这个是真的要做的


}
