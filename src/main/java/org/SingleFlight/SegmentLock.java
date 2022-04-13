package org.SingleFlight;


import lombok.Data;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

@Data
public class SegmentLock<V>{
    // 替代条件变量做阻塞和唤起
    public ReentrantLock lock = new ReentrantLock();
    // 存储需要的消费次数
    public AtomicInteger count = new AtomicInteger(0);
    // 结果
    public V result;
}
