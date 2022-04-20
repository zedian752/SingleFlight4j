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
    // 主测试函数，测试多并发，单一生产任务
    @Test
    public void test1() {
        int threadCount = 10; // 线程数
//        ExecutorService executorService = Executors.newFixedThreadPool(threadCount); // 线程池
        ExecutorService executorService = new ThreadPoolExecutor(threadCount, threadCount, 10000, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(), Executors.defaultThreadFactory(), new ThreadPoolExecutor.AbortPolicy());

        int count = 1; // 并发次数
        List<String> resList = new CopyOnWriteArrayList<>(); // 保存结果
        CountDownLatch countDownLatch = new CountDownLatch(count); // 计数器
        AtomicInteger produceCount = new AtomicInteger(0); // 记录生产次数
        AtomicInteger ErrorProduceCount = new AtomicInteger(0); // 记录错误生产次数
        SingleFlight<String, String> singleFlight = new SingleFlight<>();
        int round = 1;
        System.out.printf("请求次数为%d, mod为 %d, 线程池大小为%d%n", count, round, threadCount);
        for (int i = 0; i < count; ++i) {
            int finalI = i;
            Runnable task = () -> {
                String res = null;
                try {
                    res = singleFlight.setResult(String.valueOf(finalI % round).intern(), () -> {
                                long startTime = System.currentTimeMillis();
                                try {
                                    Thread.sleep(1000);
                                    System.out.println(1 / 0);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                produceCount.incrementAndGet();
                                return String.format("生产所用时间为 %d", System.currentTimeMillis() - startTime);
                            }

                    );
                } catch (Throwable e) {
                    ErrorProduceCount.incrementAndGet();
                    e.printStackTrace();
                }
                resList.add(res);
                countDownLatch.countDown();
            };
            executorService.execute(task);
        }
        new Thread(() -> {
            List list = resList;
            ExecutorService that = executorService;
            while (true) {
                try {
                    Thread.sleep(2000);
                    System.out.printf("countDownLatch 大小为 %d%n", countDownLatch.getCount());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        int assertProduceCount = (int) Math.ceil((double) count / (double) threadCount) * (round) + ErrorProduceCount.get(); // 预估生产次数：与线程数和并发数和取余数,和生产错误相关
        System.out.printf("实际总生产次数为[%d], 理想生产次数为[%d]  预估生产次数为[%d]%n", produceCount.get(), round, assertProduceCount);
        assertThat(produceCount.get(), lessThanOrEqualTo(assertProduceCount));
        clearMapCahce(singleFlight);
        assertThat((int)resList.stream().filter(i -> i == null).count(), equalTo(ErrorProduceCount.get()));
    }

    // 用于测试缓存有没有被及时清除
    void clearMapCahce(SingleFlight singleFlight) {
        try {
            Field mapField = singleFlight.getClass().getDeclaredField("map");
            mapField.setAccessible(true);
            Map<String, String> innerMap = (Map) mapField.get(singleFlight);
            assertThat("缓存池没清", innerMap.size(), equalTo(0));
            System.out.println("缓存清理成功");
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    ThreadLocal<String> string = new ThreadLocal<>();

    // 测试多线程多任务场景
    @Test
    void tests3() {
//        SingleFlight<String, String> singleFlight = new SingleFlight<>();
//        CountDownLatch countDownLatch = new CountDownLatch(2);
//        CompletableFuture completableFuture1 = CompletableFuture.runAsync(() -> {test3_1(singleFlight);});
//        CompletableFuture completableFuture2 = CompletableFuture.runAsync(() -> {test3_2(singleFlight);});
//        completableFuture1.join();
//        completableFuture2.join();
//        System.out.println(212);
        CompletableFuture<Void> stringCompletableFuture = CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(500);
                System.out.println(1234);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).thenAccept((Void) -> {
            System.out.println("come in");
        });
        stringCompletableFuture.join();
    }



    public void test3_1(SingleFlight<String, String> singleFlight) {
        Thread.currentThread().interrupt();
        int threadCount = 10; // 线程数
        ExecutorService executorService = new ThreadPoolExecutor(threadCount, threadCount, 10000, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(), Executors.defaultThreadFactory(), new ThreadPoolExecutor.AbortPolicy());
        int count = 50; // 并发次数
        List<String> resList = new CopyOnWriteArrayList<>(); // 保存结果
        CountDownLatch countDownLatch = new CountDownLatch(count); // 计数器
        AtomicInteger produceCount = new AtomicInteger(0); // 记录生产次数
        AtomicInteger ErrorProduceCount = new AtomicInteger(0); // 记录错误生产次数
//        SingleFlight<String, String> singleFlight = singleFlight;
        int round = 1;
        System.out.printf("请求次数为%d, mod为 %d, 线程池大小为%d%n", count, round, threadCount);
        for (int i = 0; i < count; ++i) {
            int finalI = i;
            Runnable task = () -> {
                String res = null;
                try {
                    res = singleFlight.setResult(String.valueOf(finalI % round).intern(), () -> {
                                long startTime = System.currentTimeMillis();
                                try {
                                    Thread.sleep(1000);
                                    System.out.println(1 / 0);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                produceCount.incrementAndGet();
                                return String.format("生产所用时间为 %d", System.currentTimeMillis() - startTime);
                            }

                    );
                } catch (Throwable e) {
                    ErrorProduceCount.incrementAndGet();
                    e.printStackTrace();
                }
                resList.add(res);
                countDownLatch.countDown();
            };
            executorService.execute(task);
        }
        new Thread(() -> {
            List list = resList;
            ExecutorService that = executorService;
            while (true) {
                try {
                    Thread.sleep(2000);
                    System.out.printf("countDownLatch 大小为 %d%n", countDownLatch.getCount());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        int assertProduceCount = (int) Math.ceil((double) count / (double) threadCount) * (round) + ErrorProduceCount.get(); // 预估生产次数：与线程数和并发数和取余数相关
        System.out.printf("实际总生产次数为[%d], 理想生产次数为[%d]  预估生产次数为[%d]%n", produceCount.get(), round, assertProduceCount);
        assertThat(produceCount.get(), lessThanOrEqualTo(assertProduceCount));
        clearMapCahce(singleFlight);
//        assertThat((int)resList.stream().filter(i -> i == null).count(), equalTo(ErrorProduceCount.get()));
    }

    public void test3_2(SingleFlight<String, String> singleFlight) {
        int threadCount = 10; // 线程数
        ExecutorService executorService = new ThreadPoolExecutor(threadCount, threadCount, 10000, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(), Executors.defaultThreadFactory(), new ThreadPoolExecutor.AbortPolicy());

        int count = 50; // 并发次数
        List<String> resList = new CopyOnWriteArrayList<>(); // 保存结果
        CountDownLatch countDownLatch = new CountDownLatch(count); // 计数器
        AtomicInteger produceCount = new AtomicInteger(0); // 记录生产次数
        AtomicInteger ErrorProduceCount = new AtomicInteger(0); // 记录错误生产次数
//        SingleFlight<String, String> singleFlight = singleFlight;
        int round = 1;
        System.out.printf("请求次数为%d, mod为 %d, 线程池大小为%d%n", count, round, threadCount);
        for (int i = 0; i < count; ++i) {
            int finalI = i;
            Runnable task = () -> {
                String res = null;
                try {
                    res = singleFlight.setResult(String.valueOf(finalI % round).intern(), () -> {
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
                } catch (Throwable e) {
                    ErrorProduceCount.incrementAndGet();
                    e.printStackTrace();
                }
                resList.add(res);
                countDownLatch.countDown();
            };
            executorService.execute(task);
        }
        new Thread(() -> {
            List list = resList;
            ExecutorService that = executorService;
            while (true) {
                try {
                    Thread.sleep(2000);
                    System.out.printf("countDownLatch 大小为 %d%n", countDownLatch.getCount());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        int assertProduceCount = (int) Math.ceil((double) count / (double) threadCount) * (round) + ErrorProduceCount.get(); // 预估生产次数：与线程数和并发数和取余数相关
        System.out.printf("实际总生产次数为[%d], 理想生产次数为[%d]  预估生产次数为[%d]%n", produceCount.get(), round, assertProduceCount);
        assertThat(produceCount.get(), lessThanOrEqualTo(assertProduceCount));
        clearMapCahce(singleFlight);
//        assertThat((int)resList.stream().filter(i -> i == null).count(), equalTo(ErrorProduceCount.get()));
    }


}
