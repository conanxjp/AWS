package com.cclearning.imgclass;

import com.cclearning.imgclass.server.Server;
import com.cclearning.imgclass.utils.Utils;
import com.cclearning.imgclass.worker.Worker;

public class Main {

	public static void main(String[] args) {
        if(args.length < 1 || args[0] == null || args[0].length() == 0) {
        	Utils.log("Error: wrong parameter.");
        	return;
        }
        
        String param = args[0];
        AppEntry entry = null;
        if("server".equals(param)) {
        	entry = new Server();
        }else if("worker".equals(param)){
        	entry = new Worker();
        }else {
        	Utils.log("Wrong parameter: " + args[1]);
        }
        
        if(entry != null) {
        	entry.start();
        }
	}

}
