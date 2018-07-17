package com.cclearning.imgclass.server;
import java.util.ArrayList;
import java.util.List;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.CreditSpecificationRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.StartInstancesRequest;
import com.amazonaws.services.ec2.model.StopInstancesRequest;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.cclearning.imgclass.Config;
import com.cclearning.imgclass.utils.Utils;


public class WorkerManager {
    private List<String> mInstanceIds = new ArrayList<>();
    private String mAmiId = Config.WORKER_AMI;
    private final int mMaxCount = Config.MAX_WORKER_COUNT;
    private String mSecurityGroupId = Config.SECURITY_GROUP_ID;
    private static WorkerManager sWorkerManager;
    private final String KEY_NAME = "Name";
    private final String NAME_PREFIX = "app-instance";
    
    public static WorkerManager getInstance() {
    	if(sWorkerManager == null) {
    		synchronized(WorkerManager.class) {
    			if(sWorkerManager == null) {
    				sWorkerManager = new WorkerManager();
    			}
    		}
    	}
    	return sWorkerManager;
    }
    
    private WorkerManager() {
    	if(Server.sConfigurations != null) {
    		String amiId = Server.sConfigurations.get("AMI_ID");
    		if(amiId != null && amiId.length() > 0) {
    			mAmiId = amiId;
    		}
    		String secGroupId = Server.sConfigurations.get("Security_Group_ID");
    		if(secGroupId != null && secGroupId.length() > 0) {
    			mSecurityGroupId = secGroupId;
    		}
    	}
    }
    
    private AmazonEC2 createAmazonEC2() {
    	return AmazonEC2ClientBuilder.standard().withRegion(Config.REGIONS).build();
    }
	
	public void createWorkers(int count) {
		if(count < 1) {
			return;
		}
		
		if (mInstanceIds.size() == mMaxCount) {
			log("Max instances have reached, cannot create more instances");
			return;
		}
		else if(count + mInstanceIds.size() > mMaxCount) {
			count = mMaxCount - mInstanceIds.size();
		}
		
		final AmazonEC2 ec2 = createAmazonEC2();
		log("create instance.");
		int minInstanceCount = 1;
		int maxInstanceCount = count;
		RunInstancesRequest request = new RunInstancesRequest(mAmiId, minInstanceCount, maxInstanceCount);
        request.setInstanceType("t2.micro");
        
        request.setCreditSpecification(new CreditSpecificationRequest().withCpuCredits("unlimited"));
        
        List<String> secGroupIds = new ArrayList<String>();
        secGroupIds.add(mSecurityGroupId);
        request.setSecurityGroupIds(secGroupIds);
        RunInstancesResult result = ec2.runInstances(request);
        List<Instance> instances = result.getReservation().getInstances();
        int index = mInstanceIds.size();
        for(Instance ins : instances) {
        	String id = ins.getInstanceId();
        	log("Instance has been created: " + id);
        	mInstanceIds.add(id);
        	setInstanceName(id, index);
        	index++;
        }
        log("Creating instances finished.");
	}
	
	private void setInstanceName(String id, int index) {
		Tag tag = new Tag();
		tag.setKey(KEY_NAME);
		tag.setValue(NAME_PREFIX + index);
		final AmazonEC2 ec2 = createAmazonEC2();
		CreateTagsRequest request = new CreateTagsRequest().withResources(id).withTags(tag);
		ec2.createTags(request);
	}
	
	public void terminateWorkers(int count) {
		if(count < 1) {
			return;
		}
		
		int total = mInstanceIds.size();
		if(count >= total) {
			terminateAll();
		}else {
			// Create a sublist view on the id list
			List<String> ids = mInstanceIds.subList(total - count, total);
			terminateInstances(ids);
			// remove the id of the instances which have been terminated.
			ids.clear();
		}
	}
	
	public void startInstance(String instanceId) {
		if (!mInstanceIds.contains(instanceId)) {
			log("InstanceId is not found");
		}
		else {
			final AmazonEC2 ec2 = createAmazonEC2();
			StartInstancesRequest request = new StartInstancesRequest().withInstanceIds(instanceId);
			ec2.startInstances(request);
			log("start instance: " + instanceId);
		}
	}
	
	public void addWorkers(int count) {
		if (count < 1) return;
		this.createWorkers(count);
	}
	
	public void stopInstance(String instanceId) {
		if (!mInstanceIds.contains(instanceId)) {
			log("InstanceId is not found");
		}
		else {
			final AmazonEC2 ec2 = createAmazonEC2();
			StopInstancesRequest request = new StopInstancesRequest().withInstanceIds(instanceId);
			ec2.stopInstances(request);
			log("stop instance: " + instanceId);
		}	
	}
	
	public void removeWorkers(int count) {
		if (count < 1) return;
		// maintain the init_worker_count number of instances running
		// may consider refresh the remaining workers if the test is long and stressful
		else if (count + Config.INIT_WORKER_COUNT > mInstanceIds.size()) {
			removeWorkers(mInstanceIds.size() - Config.INIT_WORKER_COUNT);
		}else {
			for (String id : mInstanceIds) {
				if (count != 0) {
					terminateInstance(id);
					count --;
				}
			}
		}
	}
	
	public int getInstanceNumber() {
		return mInstanceIds.size();
	}
	
	public void stopAll() {
		final AmazonEC2 ec2 = createAmazonEC2();
		StopInstancesRequest request = new StopInstancesRequest().withInstanceIds(mInstanceIds);
		ec2.stopInstances(request);
		log("Stop instances");
	}
	
	public void terminateInstance(String instanceId) {
		if (!mInstanceIds.contains(instanceId)) {
			log("InstanceId is not found");
		}
		else {
			final AmazonEC2 ec2 = createAmazonEC2();
			TerminateInstancesRequest request = new TerminateInstancesRequest().withInstanceIds(instanceId);
			ec2.terminateInstances(request);
			mInstanceIds.remove(instanceId);
			log("terminate instance: " + instanceId);
		}
	}
	
	public void terminateInstances(List<String> ids) {
		final AmazonEC2 ec2 = createAmazonEC2();
		TerminateInstancesRequest request = new TerminateInstancesRequest().withInstanceIds(ids);
		ec2.terminateInstances(request);
	}
	
	public void terminateAll() {
		if(mInstanceIds.size() == 0) {
			return;
		}
		final AmazonEC2 ec2 = createAmazonEC2();
		TerminateInstancesRequest request = new TerminateInstancesRequest().withInstanceIds(mInstanceIds);
		ec2.terminateInstances(request);
		log("Terminate all instances");
		mInstanceIds.clear();
	}
	
	public void checkInstanceState(String instanceId) {
		final AmazonEC2 ec2 = createAmazonEC2();
		DescribeInstancesRequest request = new DescribeInstancesRequest().withInstanceIds(instanceId);
		DescribeInstancesResult result = ec2.describeInstances(request);
		log("The desp of instance " + instanceId + ":  " + result.getSdkResponseMetadata().toString());
	}
	
	public void startAllInstances() {
		for(String id : mInstanceIds) {
			startInstance(id);
		}
	}
	
	private static void log(String msg) {
		Utils.log(msg);
	}
	
}
