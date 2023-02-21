package com.luoye.dpt.task;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author luoyesiqiu
 */
public class ThreadPool {
    private static ThreadPool sInst;
    private static final ThreadPoolExecutor sThreadPoolExecutor = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().availableProcessors() * 2,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>());
    private ThreadPool(){

    }
    public static ThreadPool getInstance() {
        if(sInst == null){
            sInst = new ThreadPool();
        }
        return sInst;
    }

    public void execute(Runnable task){
        sThreadPoolExecutor.execute(task);
    }

    public void shutdown(){
        sThreadPoolExecutor.shutdown();
    }
}
