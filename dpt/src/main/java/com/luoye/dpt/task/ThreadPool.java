package com.luoye.dpt.task;

import com.luoye.dpt.config.Const;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author luoyesiqiu
 */
public class ThreadPool {
    private static final ThreadPool sInst = new ThreadPool();
    private static final ThreadPoolExecutor sThreadPoolExecutor = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().availableProcessors() * 2,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(),
            new CustomThreadFactory());
    private ThreadPool(){

    }
    public static ThreadPool getInstance() {
        return sInst;
    }

    public static class CustomThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName(Const.DEFAULT_THREAD_NAME + "-" + t.getId());
            return t;
        }
    }

    public void execute(Runnable task){
        sThreadPoolExecutor.execute(task);
    }

    public void shutdown(){
        sThreadPoolExecutor.shutdown();
    }
}
