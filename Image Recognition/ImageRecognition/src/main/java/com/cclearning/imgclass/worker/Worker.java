package com.cclearning.imgclass.worker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import com.cclearning.imgclass.AppEntry;
import com.cclearning.imgclass.Config;
import com.cclearning.imgclass.data.S3Manager;
import com.cclearning.imgclass.data.Sqs;
import com.cclearning.imgclass.data.SqsManager;
import com.cclearning.imgclass.server.Server;
import com.cclearning.imgclass.utils.Utils;

public class Worker implements AppEntry{
	private final String QUEUE_NAME_REQUEST = Server.QUEUE_NAME_REQUEST;
	private final String QUEUE_NAME_RESULT = Server.QUEUE_NAME_RESULT;
	private Sqs mRequestQueue;
	private Sqs mResultQueue;

	@Override
	public void start() {
	    Utils.log("Start the worker.");
	    init();
	    startWork();
	}

	private void init() {
		mRequestQueue = SqsManager.getSqs(QUEUE_NAME_REQUEST);
		mResultQueue = SqsManager.getSqs(QUEUE_NAME_RESULT);
	}
	
	private String getMessage(int visibilityTimeout) {
		return mRequestQueue.receiveMsg(visibilityTimeout);
	}

	// Get the image url of an request
	private String getRequestImageUrl(String msg) {
		if(msg == null || msg.length() == 0) {
			return msg;
		}
		// 0 - id, 1 - url
		String[] strs = Utils.splitRequestMessage(msg);
		if(strs != null && strs.length == 2) {
			return strs[1];
		}
		return msg;
	}

	// Send the result to the result queue
	private void sendResult(String requestMsg, String result) {
		String msg = Utils.createResultMessage(requestMsg, result);
		mResultQueue.sendMsg(msg);
	}
	
	private boolean deleteMsg() {
		return mRequestQueue.deleteMsg();
	}
	
	private void cacheToS3(String imageName, String result) {
		S3Manager.putObject(imageName, result);
	}
	
	private String getImageName(String imageUrl) {
		String[] image = imageUrl.split("/");
		return image[image.length - 1];
	}

	// check s3 for existing image name.
	private String getS3Result(String imageName) {
		return S3Manager.getResult(imageName);
	}

	// worker does its job
	private void startWork() {
		long startTime = -1;
		int visibilityTimeout = -1;
		boolean valid = false;
		while(true) {
			String msg = null;
			if(valid) {
				msg = getMessage(visibilityTimeout);
			}else {
				// Do not set the visiblity timeout, use default
				msg = getMessage(-1);
			}
			valid = false;
			final String imageUrl = getRequestImageUrl(msg);
			if(imageUrl != null && imageUrl.length() > 0) {
				startTime = System.currentTimeMillis();
				if(Config.DEBUG) {
					Utils.log("Got message: " + msg);
				}
				final String imageName = getImageName(imageUrl);
				String result = imageRecogniton(imageUrl);
				if (result != null && result.length() > 0) {
					cacheToS3(imageName, result);
					valid = true;
				} else {
					result = imageUrl + " --->No result.";
				}
				sendResult(msg, result);
				if (Config.DEBUG) {
					Utils.log("Worker processed image, result: " + result);
				}
//				String cacheResult = getS3Result(imageName);
//				if (cacheResult == null) {
//					String result = imageRecogniton(imageUrl);
//					if(result != null && result.length() > 0) {
//						cacheToS3(imageName, result);
//					}else {
//						result = "No result.";
//					}
//					sendResult(msg, result);
//					if(Config.DEBUG) {
//						Utils.log("Worker processed image, result: " + result);
//					}
//				}else {
//					sendResult(msg, cacheResult);
//					if(Config.DEBUG) {
//						Utils.log("Cache result found, skip to next task");
//					}
//				}
				// Delete the message after processing.
				deleteMsg();
				if(valid) {
					visibilityTimeout = (int)((System.currentTimeMillis() - startTime) / 1000);
					if (Config.DEBUG) {
						Utils.log("Worker processing image time: " + visibilityTimeout);
					}
					visibilityTimeout += 5;
				}
			}else {
				Utils.sleep(1);
				if(Config.DEBUG) {
					Utils.log("No message.");
				}
			}
		}
	}

	private String imageRecogniton(String imageUrl) {
		String srcCmd = "source /home/ubuntu/tensorflow/bin/activate";
		String cdCmd = "cd /home/ubuntu/tensorflow/models/tutorials/image/imagenet";
		String pythonCmd = "python classify_image.py --image_file " 
		                   + imageUrl + " --num_top_predictions 1";

		String cmd = srcCmd + " ; " + cdCmd + " ; " + pythonCmd;

		ProcessBuilder pb = new ProcessBuilder("bash", "-c", cmd);
		BufferedReader br = null;
		String result = "";
		try {
			Process process = pb.start();
//			int errorCode = process.waitFor();
			StringBuilder sb = new StringBuilder();
			br = new BufferedReader(new InputStreamReader
					(process.getInputStream()));
			String line = null;
			while ((line = br.readLine()) != null) {
				sb.append(line);
			}
			result = sb.toString();
		} catch (Exception e) {
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
		
		try {
			result = result.substring(0, result.indexOf('(') -1);
		}catch(Exception e) {
			if(Config.DEBUG) {
				log("The result is not as expected: " + result);
			}
		}
		
		return result;
	}
	
	private void log(String msg) {
		Utils.log(msg);
	}

}
