package com.cclearning.imgclass.data;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.List;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.Bucket;
import com.cclearning.imgclass.Config;
import com.cclearning.imgclass.utils.Utils;


public class S3Manager {
	
	public static final String BUCKET_NAME = Config.BUCKET_NAME;
	
	public static Bucket getBucket(String bucket_name) {
		final AmazonS3 s3 = AmazonS3ClientBuilder.defaultClient();
		Bucket named_bucket = null;
		List<Bucket> buckets = s3.listBuckets();
		for(Bucket b : buckets) {
			if(b.getName().equals(BUCKET_NAME)) {
				named_bucket = b;
			}
		}
		return named_bucket;
	}
	
	public static Bucket createBucket(String name) {
		final AmazonS3 s3 = AmazonS3ClientBuilder.defaultClient();
		Bucket b;
		if(s3.doesBucketExist(BUCKET_NAME)) {
			b  =getBucket(BUCKET_NAME);
		}else {
			b = s3.createBucket(BUCKET_NAME);
		}
		
		return b;
	}
	
	
	public static void putObject(String key_name, String result) {
		final AmazonS3 s3 = AmazonS3ClientBuilder.defaultClient();
		try {
			s3.putObject(BUCKET_NAME, key_name, result);
		}catch(Exception e) {
			if(Config.DEBUG) {
				log("[S3Manager: ]" + e.toString());
			}
		}
		if(Config.DEBUG) {
			log("[S3Manager: ] Result for image: " + key_name + " is saved in " + BUCKET_NAME);
		}
	}
	
	public static String getResult(String key_name) {
		final AmazonS3 s3 = AmazonS3ClientBuilder.defaultClient();
		String result = null;
		try {
			result = s3.getObjectAsString(BUCKET_NAME, key_name);
		}catch(Exception e) {
			if(Config.DEBUG) {
				log("[S3Manager: ] " + e.toString());
			}
		}
		return result;
	}
	
	public static boolean deleteObject(String bucket_name, String key_name) {
		final AmazonS3 s3 = AmazonS3ClientBuilder.defaultClient();
		s3.deleteObject(BUCKET_NAME, key_name);
		return s3.doesObjectExist(BUCKET_NAME, key_name);
	}
	
	
	public static void log(String msg) {
		Utils.log(msg);
	}
	
}
