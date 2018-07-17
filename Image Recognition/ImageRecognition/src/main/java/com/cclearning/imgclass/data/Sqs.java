package com.cclearning.imgclass.data;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.ChangeMessageVisibilityRequest;
import com.amazonaws.services.sqs.model.CreateQueueResult;
import com.amazonaws.services.sqs.model.DeleteQueueRequest;
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SetQueueAttributesRequest;
import com.cclearning.imgclass.Config;
import com.cclearning.imgclass.utils.Utils;

public class Sqs {
	private final String mQueueName;
	private AmazonSQS mAmazonSQS;
	private Message currentMsg;
	
	public Sqs(String name) {
		this.mQueueName = name;
		this.currentMsg = null;
		create();
		setup();
	}
	
	private AmazonSQS createAmazonSQS() {
		if(mAmazonSQS == null) {
			mAmazonSQS = AmazonSQSClientBuilder.standard().withRegion(Config.REGIONS).build();
		}
		return mAmazonSQS;
	}

	private void create() {
		final AmazonSQS sqs = createAmazonSQS();
		CreateQueueResult result = sqs.createQueue(mQueueName);
		log("create a queue, result: " + result.toString());
	}
	
	private void setup() {
		final AmazonSQS sqs = createAmazonSQS();
		String queueUrl = sqs.getQueueUrl(mQueueName).getQueueUrl();
		SetQueueAttributesRequest request = new SetQueueAttributesRequest();
		request.setQueueUrl(queueUrl);
		// Set the message visibility timeout to 15s.
		request.addAttributesEntry("VisibilityTimeout", "15");
		sqs.setQueueAttributes(request);
	}
	
	public void destory() {
		final AmazonSQS sqs = createAmazonSQS();
		String queueUrl = sqs.getQueueUrl(mQueueName).getQueueUrl();
        DeleteQueueRequest request = new DeleteQueueRequest(queueUrl);
        sqs.deleteQueue(request);
        log("Delete the queue: " + mQueueName);
	}
	
	public void sendMsg(String msg) {
		final AmazonSQS sqs = createAmazonSQS();
		String queueUrl = sqs.getQueueUrl(mQueueName).getQueueUrl();
		SendMessageRequest send_msg_request = new SendMessageRequest()
				.withQueueUrl(queueUrl)
				.withMessageBody(msg)
				.withDelaySeconds(0);
		sqs.sendMessage(send_msg_request);
		log("sent a message.");
	}
	
	/**
	 * Receive one message each time
	 * @return
	 */
	public String receiveMsg() {
		final AmazonSQS sqs = createAmazonSQS();
		String queueUrl = sqs.getQueueUrl(mQueueName).getQueueUrl();
		// for more configuration
		ReceiveMessageRequest request = new ReceiveMessageRequest();
		request.setMaxNumberOfMessages(1);
		request.setQueueUrl(queueUrl);
		List<Message> messages = sqs.receiveMessage(request).getMessages();
		if(messages.size() > 0) {
			Message msg = messages.get(0);
			currentMsg = msg;
			return msg.getBody();
		}
		
		return null;
	}
	
	public String receiveMsg(int visibilityTimeout) {
		final AmazonSQS sqs = createAmazonSQS();
		String queueUrl = sqs.getQueueUrl(mQueueName).getQueueUrl();
		// for more configuration
		ReceiveMessageRequest request = new ReceiveMessageRequest();
		request.setMaxNumberOfMessages(1);
		request.setQueueUrl(queueUrl);
		List<Message> messages = sqs.receiveMessage(request).getMessages();
		if(messages.size() > 0) {
			Message msg = messages.get(0);
			currentMsg = msg;
			
			if(visibilityTimeout > 0) {
				// change visibility timeout
				ChangeMessageVisibilityRequest cmvr = new ChangeMessageVisibilityRequest(queueUrl,
						msg.getReceiptHandle(), visibilityTimeout);
				sqs.changeMessageVisibility(cmvr);
			}
			
			return msg.getBody();
		}
		
		return null;
	}
	
	public boolean deleteMsg() {
		final AmazonSQS sqs = createAmazonSQS();
		String queueUrl = sqs.getQueueUrl(mQueueName).getQueueUrl();
		if (currentMsg != null) {
			sqs.deleteMessage(queueUrl, currentMsg.getReceiptHandle());
			currentMsg = null;
			return true;
		}else {
			return false;
		}	
	}
	
	public int getQueueLength() {
		final AmazonSQS sqs = createAmazonSQS();
		String queueUrl = sqs.getQueueUrl(mQueueName).getQueueUrl();
		GetQueueAttributesRequest sqsr = new GetQueueAttributesRequest(queueUrl).withAttributeNames("ApproximateNumberOfMessages", 
				"ApproximateNumberOfMessagesNotVisible");
		Map<String, String> results = sqs.getQueueAttributes(sqsr).getAttributes();
		String visibleMsgCount = results.get("ApproximateNumberOfMessages");
		String invisibleMsgCount = results.get("ApproximateNumberOfMessagesNotVisible");
		int count = 1;
		try {
			count = Integer.parseInt(visibleMsgCount) + Integer.parseInt(invisibleMsgCount);
		}catch(Exception e) {
			e.printStackTrace();
		}
		if(Config.DEBUG) {
			Utils.log("The sqs length: " + count);
		}
		return count;
	}

	private void log(String msg) {
		Utils.log(msg);
	}

}
