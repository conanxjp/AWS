package com.cclearning.imgclass.data;

public class SqsManager {
	
	public static Sqs getSqs(String queueName) {
		return new Sqs(queueName);
	}
	
}
