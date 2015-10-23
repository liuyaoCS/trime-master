package com.osfans.net;


import retrofit.RestAdapter;

public class NetworkService {
	static NetworkServiceAPI instance;
	
	static public NetworkServiceAPI getInstance(){
		if (instance != null)
			return instance;
		
		RestAdapter restAdapter = new RestAdapter.Builder()
	    .setEndpoint("http://mob.chinaso.com")  
	    .build();
		instance = restAdapter.create(NetworkServiceAPI.class);
		
		return instance;
	}
	
}
