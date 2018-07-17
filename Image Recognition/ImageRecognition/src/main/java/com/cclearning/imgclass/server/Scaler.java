package com.cclearning.imgclass.server;

import com.cclearning.imgclass.Config;
import com.cclearning.imgclass.utils.Utils;

public class Scaler {
    private WorkerManager mWorkerManager;
    // The estimate execution time of each task in second.
    private final int TIME_PER_TASK = 4;
    // The estimate start time of each new worker instance.
    private final int TIME_START_WORKER = 20;
    // The threshold of the load factor.
    private final int LF_THRESHOLD = 1;
    
    public Scaler() {
    	mWorkerManager = WorkerManager.getInstance();
    	if(Config.INIT_WORKER_COUNT > 0) {
    		mWorkerManager.createWorkers(Config.INIT_WORKER_COUNT);
    	}
    }
    
    public void balance(int requestCount) {
    	final int workerCount = mWorkerManager.getInstanceNumber();
    	
    	if(Config.DEBUG) {
    		Utils.log("The app instance count: " + workerCount);
    	}
    	
//    	int lf = 0;
//    	if(workerCount >= 1) {
//    		lf = requestCount / workerCount;
//    	}else {
//    		lf = Integer.MAX_VALUE;
//    	}

//    	if(lf < 1 || requestCount <= 0) {
//    		scaleDown(requestCount, workerCount);
//    	}else if(lf > LF_THRESHOLD){
//    		simpleScaleUp(requestCount, workerCount);
//    	}
    	if(requestCount < workerCount) {
    		scaleDown(requestCount, workerCount);
    	}else if(requestCount > workerCount){
    		simpleScaleUp(requestCount, workerCount);
    	}
    }
    
    public void destroy() {
    	mWorkerManager.terminateAll();
    }
    
    private void scaleDown(int requestCount, int workerCount) {
    	if(requestCount <= 0) {
    		if(workerCount > 1) {
        		mWorkerManager.terminateWorkers(workerCount - 1);
        		return;
    		}
    	}else {
    		int removeCount = workerCount - requestCount;
    		if(removeCount > 0) {
    			// Terminate one app-instance each time if there are requests.
        		mWorkerManager.terminateWorkers(removeCount);
    		}
    	}
    }
    
    private void simpleScaleUp(int requestCount, int workerCount) {
    	int addCount = requestCount - workerCount;
    	if(addCount > 0) {
    		mWorkerManager.createWorkers(addCount);
    	}
    }
    
    private void scaleUp(int requestCount, int workerCount) {
    	int addCount = 0;
    	if(workerCount == 0) {
    		addCount = requestCount / LF_THRESHOLD;
    		// Add one worker if there is no worker.
    		if(addCount == 0) {
    			addCount = 1;
    		}
    	}else {
    		addCount = (requestCount - (TIME_START_WORKER * workerCount / TIME_PER_TASK) 
    				- (LF_THRESHOLD * workerCount)) 
    				/ LF_THRESHOLD; 
    	}
    	
    	if(addCount > 0) {
    		mWorkerManager.createWorkers(addCount);
    	}
    }
    
}
