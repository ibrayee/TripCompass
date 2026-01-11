package org.example.GoogleMaps;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class PlacesNearby {

    private final String apiKey;
    private final String lat;
    private final String lng;
    private String jSonPlaces;
    private int radius = 2000;

    public PlacesNearby(String lat, String lng, String apiKey) {
        this.lat = lat;
        this.lng = lng;
        this.apiKey = apiKey;
    }

    public String fetchPlaces(String placeType) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Google Maps API key missing");
        }
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest httpRequest =
                HttpRequest.newBuilder()
                        .uri(URI.create("https://maps.googleapis.com/maps/api/place/nearbysearch/json" +
                                "?location=" + lat + "," + lng +
                                "&radius=" + radius +
                                "&type=" + placeType +
                                "&key=" + apiKey))
                        .build();

        HttpResponse<String> response = null;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        if (response == null || response.body() == null) {
            jSonPlaces = "{}";
            return jSonPlaces;
        }
        jSonPlaces = response.body();

        return jSonPlaces;
    }

    public String getPlaceNameAndAdress(String placeType) {

        fetchPlaces(placeType);
        JsonObject responseJObj = JsonParser.parseString(jSonPlaces).getAsJsonObject();  //converts Json String answer to JsonObject

        JsonArray placesResArr = responseJObj.getAsJsonArray("results");

        JsonArray resultPlacesArr = new JsonArray();

        if (placesResArr == null) {
            return resultPlacesArr.toString();
        }

        for (int i = 0; i < placesResArr.size(); i++) {   //Flera platser inom radien
            JsonObject placeJson = placesResArr.get(i).getAsJsonObject();

            JsonObject placeObject = new JsonObject();

            placeObject.addProperty("Name of place ", placeJson.get("name").getAsString());
            placeObject.addProperty("adress ", placeJson.get("vicinity").getAsString());
            placeObject.addProperty("type of place", placeType);

            resultPlacesArr.add(placeObject);
        }

        return resultPlacesArr.toString();
    }
}