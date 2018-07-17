package com.cclearning.imgclass.server;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.cclearning.imgclass.AppEntry;
import com.cclearning.imgclass.Config;
import com.cclearning.imgclass.data.Sqs;
import com.cclearning.imgclass.data.SqsManager;
import com.cclearning.imgclass.utils.Utils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class Server implements AppEntry{
	public static final String QUEUE_NAME_REQUEST = "requestqueue";
	public static final String QUEUE_NAME_RESULT = "resultqueue";
	public static Map<String, String> sConfigurations;
	private Sqs mRequestQueue;
	private Sqs mResultQueue;
	private Map<String, HttpExchange> mWaitMap;
	private Scaler mScaler;
	private final AtomicInteger mIdGenerator;
	private int InitialDelay = 1 * 30 * 1000;
	private int checkInterval =  3 * 1000;
	private AtomicInteger mHeartBeat;
	private ScheduledExecutorService mScalerService;
	private int mLastValue = 0;
	
	public Server() {
		mIdGenerator = new AtomicInteger(0);
		mHeartBeat = new AtomicInteger(mLastValue + 1);
	}

	@Override
	public void start() {
		Utils.log("Start the server.");
		init();
		scheduleWorkerCheck();
		startWatchDog();
		try {
			startHttpServer();
		} catch (Exception e) {
			e.printStackTrace();
		}
		handleResults();
	}
	
	public void terminate() {
		mScaler.destroy();
		mRequestQueue.destory();
		mResultQueue.destory();
		System.exit(0);
	}
	
	private void init() {
		sConfigurations = readConfig();

		// create the request queue with Sqs
		mRequestQueue = SqsManager.getSqs(QUEUE_NAME_REQUEST);
		// create result queue with Sqs
		mResultQueue = SqsManager.getSqs(QUEUE_NAME_RESULT);
		mWaitMap = new HashMap<>();

		mScaler = new Scaler();
		
	}
	
	private Map<String, String> readConfig() {
		Utils.log("Read the configuration.");
		String fileName = "webserver_config";
		Map<String, String> configMap = new HashMap<>();
		BufferedReader br = null;
		try {
			File file = new File(fileName);
			if(file.isFile()) {
				FileReader fr = new FileReader(fileName);
				br = new BufferedReader(fr);
				String line = null;
				while((line = br.readLine()) != null) {
					String[] kv = line.split("=");
					if(kv.length == 2 && kv[0] != null && kv[1] != null) {
						kv[0] = kv[0].trim();
						kv[1] = kv[1].trim();
						if(kv[0].length() > 0 && kv[1].length() >0) {
							configMap.put(kv[0], kv[1]);
						}
					}
				}
				fr.close();
			}else {
				Utils.log("No configuration file.");
			}

		}catch(Exception e) {
			e.printStackTrace();
		}finally {
			if(br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		for(String key : configMap.keySet()) {
			Utils.log(key + " = " + configMap.get(key));
		}
		
		return configMap;
	}
	
	private void scheduleWorkerCheck() {
		if(sConfigurations != null) {
			try {
				int interval = Integer.parseInt(sConfigurations.get("Scaler_Check_Interval"));
				if(interval >= 1000) {
					checkInterval = interval;
				}
			}catch(Exception e) {
				
			}
		}
		startScalerService(InitialDelay);
	}
	
	private void startScalerService(int initialDelay) {
		mScalerService = Executors.newSingleThreadScheduledExecutor();
		mScalerService.scheduleWithFixedDelay(new Runnable() {
			
			@Override
			public void run() {
				// heart beat
				mHeartBeat.incrementAndGet();
				mScaler.balance(mRequestQueue.getQueueLength());
			}
		}, initialDelay, checkInterval, TimeUnit.MILLISECONDS);
	}
	
	private void startWatchDog() {
		ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
		service.scheduleWithFixedDelay(new Runnable() {
			
			@Override
			public void run() {
				final int current = mHeartBeat.get();
	
				if(mLastValue < current) {
					// alive
					mLastValue = current;
					return;
				}
				
				if(mLastValue > 0 && current < 0) {
					// reach the negative part
					mLastValue = current;
					return;
				}
				
                // dead
				try {
					mScalerService.shutdown();
					startScalerService(0);
					Utils.log("Restart the scaler service.");
				}catch(Exception e) {
					e.printStackTrace();
				}
			}
		}, InitialDelay + 10 * 1000, checkInterval * 3, TimeUnit.MILLISECONDS);
	}
	
    private void startHttpServer() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(Config.SERVER_PORT), 0);
        server.createContext("/", new MyHandler());
        server.setExecutor(ThreadPool.getExecutor());
        server.start();
        Utils.log("Server started.");
    }
    
    private void handleResults() {
    	while(true) {
    		String msg = mResultQueue.receiveMsg();
    		mResultQueue.deleteMsg();
    		if(msg != null && msg.length() > 0) {
    			sendResponse(msg);
    		}else {
    			Utils.sleep(1);
    		}
    	}
    }
            
    private String getId() {
    	return String.valueOf(mIdGenerator.incrementAndGet());
    }
    
    private void addRequest(String msg) {
        mRequestQueue.sendMsg(msg);
    }
    
    private boolean isValideInput(String input) {
    	return input != null && input.length() > 0;
    }
    
    private synchronized void waitForResult(String input, HttpExchange t) {
    	mWaitMap.put(input, t);
    }
    
    private synchronized HttpExchange getResponseHandle(String key) {
    	return mWaitMap.remove(key);
    }
    
	private void sendResponse(String msg) {
		String[] strs = Utils.splitResultMessage(msg);
		if (strs == null || strs.length == 0) {
			return;
		}
		HttpExchange t = getResponseHandle(strs[0]);
		if (t == null) {
			return;
		}
		if (strs.length == 2) {
			try {
				sendResponse(t, strs[1], 200);
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else if (strs.length == 1) {
			try {
				sendResponse(t, "Internal error.", 412);
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			if (Config.DEBUG) {
				Utils.log("Error: the result format is not correct: " + msg);
			}
		}
	}
    
    private void sendResponse(HttpExchange t, String content, int code) throws IOException {
    	t.sendResponseHeaders(code, content.length());
        OutputStream os = t.getResponseBody();
        os.write(content.getBytes());
        os.close();
    }

    class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
        	// The input must be the image url
        	String input = null;
        	try {
        		input = t.getRequestURI().getQuery().split("=")[1];
        	}catch(Exception e) {
        		
        	}

//        	String input = readInputStream(t.getRequestBody());
        	if(Config.DEBUG) {
            	Utils.log("Got input: " + input);
        	}
        	
        	if(!isValideInput(input)) {
        		Utils.log("Error: received wrong input: " + input);
        		sendResponse(t, "Wrong parameter.", 411);
        		return;
        	}
        	
        	String id = getId();
        	// change to id + separator + URL
        	input = Utils.createRequestMessage(id, input);
        	
            addRequest(input);
        	
        	waitForResult(input, t);
        	
        }
        
     
        private String readInputStream(InputStream inputStream) throws IOException {
        	ByteArrayOutputStream result = new ByteArrayOutputStream();
        	byte[] buffer = new byte[1024];
        	int length;
        	while ((length = inputStream.read(buffer)) != -1) {
        	    result.write(buffer, 0, length);
        	}
        	return result.toString(StandardCharsets.UTF_8.name());

        }
    }

}
