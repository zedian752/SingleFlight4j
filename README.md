# SingleFlight4j
防缓存击穿工具类-Java版本

### **原理：**

使用关键`key`值对计算行为或缓存内容进行分段，内部使用Map做对应关系，当有多次计算请求时，把多次结果汇聚成一次计算，并通过线程等待结果到来，最终把结果返回给单一生产者和多个消费者。

### 需要注意的地方：

#### Key值

所以当`key`的类型非`常用类型`时，请注意重写`hashCode()`; 

#### 错误处理：

生产中的错误通过抛出`Throwable`进行处理，所以在使用前需要提前对细分的`异常`进行捕获。

### 适用场景：

#### 场景1：缓存失效场景

当缓存数据库缓存失效的一瞬间，会有多个请求打入数据库中。

![](https://user-images.githubusercontent.com/49610236/168263866-97ce4274-41f4-49eb-b5c4-cd323c3295e0.jpg)

当热点数据被频繁请求的某一瞬间，缓存失效，本工具类适用于返回缓存的下一层操作。

![](https://user-images.githubusercontent.com/49610236/168263933-6e3c559f-0a10-479c-a351-2dbaeb9acb05.jpg)

在使用`SingleFlight`后，`SingleFlight`会将让最早到达的请求，发送到数据库中拉取数据，而其他请求线程会进入waiting，等到数据库数据请求完成后，全部线程进行请求的数据返回

#### 场景2：复杂计算场景

场景1解决的是IO密集型下的场景，而场景2解决的是计算密集型下的场景，当有复杂计算需要耗费大量CPU资源，且此计算可以通过多维度确认唯一关系后(如使用摘要算法)，可以通过SingleFlight对多次计算进行合并

![](https://user-images.githubusercontent.com/49610236/168266186-f8b09472-3874-4944-ab7d-917af209af01.jpg) 

![](https://user-images.githubusercontent.com/49610236/168266192-c8c582d5-6623-4dd1-b8f2-ce32b0a74f1d.jpg)  

### 使用方法

```java
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
```

### 缺陷：

### 1. 当生产者出错时会阻塞消费者

解决方法：

```java
// 在构造singleFlight的时候加入定时参数
// 如 
SingleFlight<String, String> singleFlight = new SingleFlight<>(1000, TimeUnit.MILLISECONDS);
```

