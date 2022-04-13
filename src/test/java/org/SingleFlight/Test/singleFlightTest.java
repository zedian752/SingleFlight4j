package org.SingleFlight.Test;

import org.SingleFlight.SingleFlight;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

public class singleFlightTest {

    // 主测试函数，测试生产流程
    @Test
    public void test1() {
        int threadCount = 5; // 线程数
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount); // 线程池
        int count = 100; // 并发次数
        List<String> resList = new CopyOnWriteArrayList<>(); // 保存结果
        CountDownLatch countDownLatch = new CountDownLatch(count); // 计数器
        AtomicInteger produceCount = new AtomicInteger(0); // 记录生产次数
        SingleFlight<String, String> singleFlight = new SingleFlight<>();
        int round = 1;
        System.out.printf("请求次数为%d, mod为 %d, 线程池大小为%d%n", count, round, threadCount);
        for (int i = 0 ; i < count; ++i) {
            int finalI = i;
            Runnable task = () -> {
                String res = singleFlight.kaishiqifei(String.valueOf(finalI % round).intern(), () -> {
                long startTime = System.currentTimeMillis();
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    produceCount.incrementAndGet();
                return String.format("生产所用时间为 %d", System.currentTimeMillis() - startTime);
                }

            );
                resList.add(res);
                countDownLatch.countDown();
            };
            executorService.submit(task);
        }

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        int assertProduceCount = (int )Math.ceil ((double)count / (double) threadCount) * (round); // 预估生产次数：与线程数和并发数和取余数相关
        System.out.printf("实际总生产次数为[%d], 理想生产次数为[%d]  预估生产次数为[%d]%n", produceCount.get(), round, assertProduceCount);
        assertThat(produceCount.get(), lessThanOrEqualTo(assertProduceCount));
        test2(singleFlight);
    }
    // 用于测试缓存有没有被及时清除
    void test2(SingleFlight singleFlight) {
        try {
            Field mapField =  singleFlight.getClass().getDeclaredField("map");
            mapField.setAccessible(true);
            Map<String ,String> innerMap = (Map) mapField.get(singleFlight);
//            innerMap.put("1", "@");
            assertThat(innerMap.size(), equalTo(0));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }
}
