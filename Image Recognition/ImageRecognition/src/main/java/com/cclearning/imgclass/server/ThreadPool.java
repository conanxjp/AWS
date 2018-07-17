package com.cclearning.imgclass.server;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadPool {
	
    public static ExecutorService getExecutor() {
    	int corePoolSize = 3;
    	int maxPoolSize = 5;
    	long keepAliveTime = 5000;
    	ExecutorService executor = 
    			new ThreadPoolExecutor(
    					corePoolSize, 
    					maxPoolSize, 
    					keepAliveTime, 
    					TimeUnit.MILLISECONDS, 
    					new LinkedBlockingQueue<Runnable>());
    	return executor;
    }
    
}
