package com.example.indoorlocation.constant;

public interface HttpConstant {
//    String SERVER = "http://10.8.13.51";
//    String SERVER = "http://192.168.110.28";
    String SERVER = "http://110.42.190.112";
    String PORT = "50555";

    String BASE_URL = SERVER + ":" + PORT;


    // Controller
    String SPACE_PATH = "/space";
    String SPACE_URL = BASE_URL + SPACE_PATH;

    String COLLECTION_PATH = "/collection";
    String COLLECTION_URL = BASE_URL + COLLECTION_PATH;

    String LOCATION_PATH = "/location";
    String LOCATION_URL = BASE_URL + LOCATION_PATH;

    String DATA_PATH = "/data";
    String DATA_URL = BASE_URL + DATA_PATH;
}
