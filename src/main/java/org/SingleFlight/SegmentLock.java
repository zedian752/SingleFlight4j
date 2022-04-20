package org.SingleFlight;


import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SegmentLock<V>{
    // 存储需要的消费次数
//    public AtomicInteger count = new AtomicInteger(0);
    // 结果
    public V result;
    public Boolean isProduced = false;
    public ReentrantReadWriteLock.ReadLock readLock;
    public ReentrantReadWriteLock.WriteLock writeLock;
    public SegmentLock() {
        ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
        this.readLock = readWriteLock.readLock();
        this.writeLock = readWriteLock.writeLock();
    }

}
