package org.SingleFlight;
import sun.security.krb5.internal.KdcErrException;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/*
 T: 内容唯一key的类型
 R: 存储的内容类型
 */
public class SingleFlight<K, V> {
    private ConcurrentHashMap<K, SegmentLock<V>> map = new ConcurrentHashMap<>();


    public V kaishiqifei(K key, Supplier<V> supplier) {
        return produce(key, supplier);
    }

    // TODO 可以重写这个方法实现使用redis存储缓存，建议在redis缓存消失的时候，调用这一个库，并且在生产方法里将缓存重新置入
    // TODO 拿key做为锁的可行性
    // TODO 用条件变量要结局，remove的时候，新进来了相同key的问题，这个新来的消费者，不会被notify，所以要将判断是否生产者与删除条件共用一把锁


    private SegmentLock<V> setKey(K key) {
        synchronized (key) {
            // 二次检查
            SegmentLock<V> segmentLock = new SegmentLock<V>();
            SegmentLock<V> res = map.putIfAbsent(key, segmentLock);
            // 如果为null则put成功，将segmentLock返回给生产者进行setRes
            if (res == null) {
                return segmentLock;
            } else { // 否则返回null, 走消费者的路
                return null;
            }
        }
    }
    // 进行生产
    private V produce(K key, Supplier<V> supplier) {
        SegmentLock<V> segmentLock = setKey(key);
        // 设置不成功装转消费者
        if (segmentLock == null) {
            return consume(key);
        }
        segmentLock.lock.lock();
        V v = supplier.get();
        segmentLock.setResult(v);
        if (segmentLock.count.get() == 0) {
            clearCache(key);
        }
        segmentLock.lock.unlock();
        return v;
    }


    // 进行消费
    private V consume(K key) {
        SegmentLock<V> res = map.get(key);
        boolean lockStatus = res.lock.isLocked();
        if (lockStatus) {
            res.count.incrementAndGet();
            res.lock.lock(); // 阻塞等待
            int newCount = res.count.decrementAndGet(); // 当前数值
            if (newCount == 0) {
                SegmentLock<V> oldSegmentLock = clearCache(key);
                if (oldSegmentLock == null) {
                    try {
                        throw new Exception("删除失败，可能出现线程冲突问题");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            res.lock.unlock();
            return res.result;
        } else if (!lockStatus) {
            return res.result;
        }
        return null;
    }
    // 清除某个key的缓存
    private SegmentLock<V> clearCache(K key) {
       return map.remove(key);
    }


}
