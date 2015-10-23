package com.osfans.net;

import java.util.List;

import retrofit.Callback;
import retrofit.client.Response;
import retrofit.http.GET;
import retrofit.http.Query;


public interface NetworkServiceAPI {

	//search suggest api
	@GET("/1/suggestionword")
	void getSuggestList(@Query("key") String key, Callback<List<String>> cb);
	
}
