package org.example;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;

public class RouteDistance {   //Distans från en startdestination till en slutdestination

public RouteDistance () {


    HttpClient httpClient = HttpClient.newHttpClient();
    HttpRequest httpRequest =
            HttpRequest.newBuilder()
                    .uri(URI.create("https://maps.googleapis.com/maps/api/geocode/json?address=" +encodedPlace+"&key="+apiKey))
                    .build();
}



}
