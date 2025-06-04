package org.example.GoogleMaps;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class Geocodes {   //använder google maps geocodes API

    private String place;
    private double lng;  //longitude
    private double lat;  //latitude
    Dotenv dotenv = Dotenv.load();
    String apiKey = dotenv.get("GOOGLE_MAPS_API_KEY");




    public Geocodes (String place) {

        String encodedPlace = URLEncoder.encode(place, StandardCharsets.UTF_8); //encoded place in case the place contains å,ö,ä


        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest httpRequest =
                HttpRequest.newBuilder()
                .uri(URI.create("https://maps.googleapis.com/maps/api/geocode/json?address=" +encodedPlace+"&key="+apiKey))
                 .build();

        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            System.out.println(response.body());     //recieving answer in Json
            JsonObject responseJObj= JsonParser.parseString(response.body()).getAsJsonObject();  //converts Json String answer to JsonObject

            JsonArray answerArr = responseJObj.getAsJsonArray("results");

            JsonObject answerArrFirst =  answerArr.get(0).getAsJsonObject();
            JsonObject geometryRes = answerArrFirst.getAsJsonObject("geometry");
            JsonObject locatRes = geometryRes.getAsJsonObject("location");
            double jlat = locatRes.get("lat").getAsDouble();
            double jLong = locatRes.get("lng").getAsDouble();
            System.out.println("Latitude: " + jlat);
            System.out.println("Longitude: " + jLong);

            this.lat = jlat;
            this.lng = jLong;



        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }



    public void setLng(double lng) {
        System.out.println("this is the longitude " +lng);
        this.lng = lng;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }


    public double getLat() {

        System.out.println("this is the latitutde "+lat);
        return lat;
    }

    public double getLng() {
        System.out.println("this is the longitude " +lng);
        return lng;
    }
}
