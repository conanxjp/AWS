package com.cclearning.imgclass;

import com.amazonaws.regions.Regions;

public class Config {
	public static final boolean DEBUG = true;
    public static final Regions REGIONS = Regions.US_WEST_1;
    public static final String WORKER_AMI = "ami-fa11009a";
    public static final String SECURITY_GROUP_ID = "sg-b3aedeca";
    public static final String BUCKET_NAME = "ccprojdata";
    public static final int INIT_WORKER_COUNT = 1;
    public static final int MAX_WORKER_COUNT = 19;
    public static final int SERVER_PORT = 8080;
}
