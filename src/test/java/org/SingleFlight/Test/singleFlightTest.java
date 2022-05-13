package org.SingleFlight.Test;

import lombok.extern.slf4j.Slf4j;
import org.SingleFlight.Result;
import org.SingleFlight.SingleFlight;
import org.SingleFlight.Task;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

@Slf4j
public class singleFlightTest {


    // 主测试函数，测试多并发，单一生产任务
    @Test
    public void test1() {
        int threadCount = 5; // 线程数
//        ExecutorService executorService = Executors.newFixedThreadPool(threadCount); // 线程池
        // waiting和blocking 都不会把线程从线程池中释放出来
        ExecutorService executorService = new ThreadPoolExecutor(threadCount, threadCount, 10000, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(), Executors.defaultThreadFactory(), new ThreadPoolExecutor.AbortPolicy());
        Future a = executorService.submit(() -> {});
        try {
            a.get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        int count = 100; // 并发次数
        List<String> resList = new CopyOnWriteArrayList<>(); // 保存结果
        CountDownLatch countDownLatch = new CountDownLatch(count); // 计数器
        AtomicInteger produceCount = new AtomicInteger(0); // 记录生产次数
        AtomicInteger ErrorProduceCount = new AtomicInteger(0); // 记录错误生产次数
        SingleFlight<String, String> singleFlight = new SingleFlight<>();
        int round = 1;
        System.out.printf("任务总数为%d, mod为 %d, 线程池大小为%d 取余数为%d%n", count, round, threadCount, round);
        for (int i = 0; i < count; ++i) {
            int finalI = i;
            Runnable task = () -> {
                Result<String> re = null;
                try {
                    re = singleFlight.setResult(String.valueOf(finalI % round).intern(), () -> {
                                long startTime = System.currentTimeMillis();
                                // 进入生产
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
                log.info(re.getType().type);
                resList.add(re.getVal()); // 记录生产结果
                countDownLatch.countDown();
            };
            executorService.execute(task);
        }

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        int assertProduceCount = (int) Math.ceil((double) count / (double) threadCount) * (round) + ErrorProduceCount.get(); // 预估生产次数：与线程数和并发数和取余数,和生产错误相关
        System.out.printf("实际总生产次数为[%d], 理想生产次数为[%d]  预估生产次数为[%d]%n", produceCount.get(), round, assertProduceCount);
        assertThat(produceCount.get(), lessThanOrEqualTo(assertProduceCount));
        assertClearedMapCache(singleFlight);
        assertThat((int)resList.stream().filter(Objects::isNull).count(), equalTo(ErrorProduceCount.get()));
    }

    // 用于测试缓存有没有被及时清除
    void assertClearedMapCache(SingleFlight singleFlight) {
        try {
            Field mapField = singleFlight.getClass().getDeclaredField("map");
            mapField.setAccessible(true);
            Map<String, String> innerMap = (Map) mapField.get(singleFlight);
            assertThat("数据校验：缓存池清理不完全", innerMap.size(), equalTo(0));
            System.out.println("数据校验：缓存清理成功");
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    // 测试多线程多任务场景
    @Test
    void tests3() {
        SingleFlight<String, String> singleFlight = new SingleFlight<>();
        CompletableFuture completableFuture1 = CompletableFuture.runAsync(() -> {test3_1(singleFlight);});
        CompletableFuture completableFuture2 = CompletableFuture.runAsync(() -> {test3_2(singleFlight);});
        completableFuture1.join();
        completableFuture2.join();
    }

    public void test3_1(SingleFlight<String, String> singleFlight) {
        int threadCount = 10; // 线程数
        ExecutorService executorService = new ThreadPoolExecutor(threadCount, threadCount, 10000, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(), Executors.defaultThreadFactory(), new ThreadPoolExecutor.AbortPolicy());
        int count = 5; // 并发次数
        List<String> resList = new CopyOnWriteArrayList<>(); // 保存结果
        CountDownLatch countDownLatch = new CountDownLatch(count); // 计数器
        AtomicInteger produceCount = new AtomicInteger(0); // 记录生产次数
        AtomicInteger ErrorProduceCount = new AtomicInteger(0); // 记录错误生产次数
        int round = 1;
        System.out.printf("请求次数为%d, mod为 %d, 线程池大小为%d%n", count, round, threadCount);
        for (int i = 0; i < count; ++i) {
            int finalI = i;
            Runnable task = () -> {
                Result<String> re = null;
                try {
                    re = singleFlight.setResult(String.valueOf(finalI % round).intern(), () -> {
                                long startTime = System.currentTimeMillis();
                                try {
                                    Thread.sleep(1000);
                                    // 手动引起生产失败
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
                resList.add(re.getVal());
                countDownLatch.countDown();
            };
            executorService.execute(task);
        }
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        int assertProduceCount = (int) Math.ceil((double) count / (double) threadCount) * (round) + ErrorProduceCount.get(); // 预估生产次数：与线程数和并发数和取余数相关
        System.out.printf("实际总生产次数为[%d], 理想生产次数为[%d]  预估生产次数为[%d]%n", produceCount.get(), round, assertProduceCount);
        assertThat(produceCount.get(), lessThanOrEqualTo(assertProduceCount));
        assertClearedMapCache(singleFlight);
//        assertThat((int)resList.stream().filter(i -> i == null).count(), equalTo(ErrorProduceCount.get())); // 结果的null值数量与捕获throwAble数量是否一致
    }

    public void test3_2(SingleFlight<String, String> singleFlight) {
        int threadCount = 10; // 线程数
        ExecutorService executorService = new ThreadPoolExecutor(threadCount, threadCount, 10000, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(), Executors.defaultThreadFactory(), new ThreadPoolExecutor.AbortPolicy());
        int count = 50; // 并发次数
        List<String> resList = new CopyOnWriteArrayList<>(); // 保存结果
        CountDownLatch countDownLatch = new CountDownLatch(count); // 计数器
        AtomicInteger produceCount = new AtomicInteger(0); // 记录生产次数
        AtomicInteger ErrorProduceCount = new AtomicInteger(0); // 记录错误生产次数
        int round = 1;
        System.out.printf("请求次数为%d, mod为 %d, 线程池大小为%d%n", count, round, threadCount);
        for (int i = 0; i < count; ++i) {
            int finalI = i;
            Runnable task = () -> {
                Result<String> re = null;
                try {
                    re = singleFlight.setResult(String.valueOf(finalI % round).intern(), () -> {
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
                resList.add(re.getVal());
                countDownLatch.countDown();
            };
            executorService.execute(task);
        }

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        int assertProduceCount = (int) Math.ceil((double) count / (double) threadCount) * (round) + ErrorProduceCount.get(); // 预估生产次数：与线程数和并发数和取余数相关
        System.out.printf("实际总生产次数为[%d], 理想生产次数为[%d]  预估生产次数为[%d]%n", produceCount.get(), round, assertProduceCount);
        assertThat(produceCount.get(), lessThanOrEqualTo(assertProduceCount));
        assertClearedMapCache(singleFlight);
//        assertThat((int)resList.stream().filter(i -> i == null).count(), equalTo(ErrorProduceCount.get()));
    }

    // 测试消费者在指定时间内获取失败则放弃获取
    @Test
    public void test4() {
        int threadCount = 10; // 线程数
        ExecutorService executorService = new ThreadPoolExecutor(threadCount, threadCount, 10000, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(), Executors.defaultThreadFactory(), new ThreadPoolExecutor.AbortPolicy());
        int count = 100; // 并发次数
        List<String> resList = new CopyOnWriteArrayList<>(); // 保存结果
        CountDownLatch countDownLatch = new CountDownLatch(count); // 计数器
        AtomicInteger produceCount = new AtomicInteger(0); // 记录生产次数
        AtomicInteger ErrorProduceCount = new AtomicInteger(0); // 记录错误生产次数
        AtomicInteger timeOutCount = new AtomicInteger(0); // 记录超出时间次数
        int round = 1;
        SingleFlight<String, String> singleFlight = new SingleFlight<>(1000, TimeUnit.MILLISECONDS);
        System.out.printf("请求次数为%d, mod为 %d, 线程池大小为%d%n", count, round, threadCount);
        for (int i = 0; i < count; ++i) {
            int finalI = i;
            Runnable task = () -> {
                Result<String> re = null;
                try {
                    re = singleFlight.setResult(String.valueOf(finalI % round).intern(), () -> {
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
                log.info(re.getType().type);
                resList.add(re.getVal());
                if (re.getType() == Result.RESULT_TYPE.CONSUMER_TIME_OUT) {
                    timeOutCount.incrementAndGet();
                }
                countDownLatch.countDown();
            };
            executorService.execute(task);
        }

        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        int assertProduceCount = (int) Math.ceil((double) count / (double) threadCount) * (round) + ErrorProduceCount.get(); // 预估生产次数：与线程数和并发数和取余数相关
        System.out.printf("数据校验：实际总生产次数为[%d]，理想生产次数为[%d]，预估生产次数为[%d]，出错次数为[%d] 消费者等待超时次数为[%d]%n", produceCount.get(), round, assertProduceCount, ErrorProduceCount.get(), timeOutCount.get());
        assertThat("数据校验：生产次数超出预期", produceCount.get(), lessThanOrEqualTo(assertProduceCount));
        assertClearedMapCache(singleFlight);
        assertThat((int)resList.stream().filter(i -> i == null).count() - timeOutCount.get(), equalTo(ErrorProduceCount.get()));
    }

    @Test
    void how_to_use() {
        SingleFlight<String, String> singleFlight = new SingleFlight<>();
        int count = 100;
        CountDownLatch countDownLatch = new CountDownLatch(count);
        Task task = () -> {
            Result<String> re = null;
            try {
                re = singleFlight.setResult("1", () -> {
                            long startTime = System.currentTimeMillis();
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            return String.format("生产所用时间为 %d", System.currentTimeMillis() - startTime);
                        }
                );
            } catch (Throwable e) {
                e.printStackTrace();
            }
            countDownLatch.countDown();
            return "Result";
        };
        for (int i = 0; i < count; ++i) {
            try {
                singleFlight.setResult("1", task);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
