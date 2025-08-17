package org.example.GoogleMaps;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class PlacesNearby {

    String lat;
    String lng;
    String jSonPlaces;

    Dotenv dotenv = Dotenv.load();
    String apiKey = dotenv.get("GOOGLE_MAPS_API_KEY");
    String placeType;
    int radius = 2000;

    public PlacesNearby(String lat, String lng) {
        this.lat = lat;
        this.lng = lng;
    }

    public String fetchPlaces(String placeType) {
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
        jSonPlaces = response.body();

        return jSonPlaces;
    }

    public String getPlaceNameAndAdress(String placeType) {

        fetchPlaces(placeType);
        JsonObject responseJObj = JsonParser.parseString(jSonPlaces).getAsJsonObject();  //converts Json String answer to JsonObject

        JsonArray placesResArr = responseJObj.getAsJsonArray("results");

        JsonArray resultPlacesArr = new JsonArray();

        for (int i = 0; i < placesResArr.size(); i++) {   //Flera platser inom radien
            JsonObject placeJson = placesResArr.get(i).getAsJsonObject();

            JsonObject placeObject = new JsonObject();

            placeObject.addProperty("Name of place ", placeJson.get("name").getAsString());
            placeObject.addProperty("adress ", placeJson.get("vicinity").getAsString());
            placeObject.addProperty("type of place", placeType);

            String name = placeJson.get("name").getAsString();
            String address = placeJson.get("vicinity").getAsString();

            resultPlacesArr.add(placeObject);
        }

        return resultPlacesArr.toString();
    }
}