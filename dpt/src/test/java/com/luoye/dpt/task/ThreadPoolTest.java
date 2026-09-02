package com.luoye.dpt.task;

import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ThreadPoolTest {
    @Test
    public void canExecuteAnotherBatchAfterAProtectionBatchShutsDown() throws Exception {
        ThreadPool pool = ThreadPool.getInstance();
        CountDownLatch first = new CountDownLatch(1);
        pool.execute(first::countDown);
        pool.shutdown();
        Assert.assertTrue("第一批任务未完成", first.await(5, TimeUnit.SECONDS));

        CountDownLatch second = new CountDownLatch(1);
        pool.execute(second::countDown);
        pool.shutdown();
        Assert.assertTrue("第二批任务未完成", second.await(5, TimeUnit.SECONDS));
    }
}
