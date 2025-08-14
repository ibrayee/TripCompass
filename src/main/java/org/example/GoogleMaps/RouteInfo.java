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

public class RouteInfo { // använder Google maps Distance API
    //Distans från en startdestination till en slutdestination
    //Kanske visa vägbeskrivning från startdestination till flygplats?
    //hämta longitude och latitude från amadeus


   private static final String apiKey = Dotenv.load().get("GOOGLE_MAPS_API_KEY");
    String startPlace = "";
    String endPlace = "";
    String jSonRoute = "";

    public RouteInfo(String startPlace, String endPlace) {

        this.startPlace = startPlace;
        this.endPlace = endPlace;

    }

    public void fetchRoute() {
        HttpClient httpClient = HttpClient.newHttpClient();

        String encodeStart = URLEncoder.encode(startPlace, StandardCharsets.UTF_8);
        String encodeEnd   = URLEncoder.encode(endPlace, StandardCharsets.UTF_8);

        HttpRequest httpRequest =
                HttpRequest.newBuilder()
                        .uri(URI.create("https://maps.googleapis.com/maps/api/directions/json?origin=" + encodeStart + "&destination=" + encodeEnd + "&key=" + apiKey))
                        .build();

        HttpResponse<String> response = null;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        jSonRoute = response.body();
        // System.out.println(response.body());

    }


    public String getRouteTimeAndDist() {

        JsonObject responseJObj = JsonParser.parseString(jSonRoute).getAsJsonObject();  //converts Json String answer to JsonObject

        JsonArray routesArr = responseJObj.getAsJsonArray("routes");

        if (routesArr.size() > 0) {
            JsonObject answerArrFirst = routesArr.get(0).getAsJsonObject();

            JsonArray legs = answerArrFirst.getAsJsonArray("legs");

            if (legs.size() > 0) {

                JsonObject answerLegsArr = legs.get(0).getAsJsonObject();

                JsonObject distanceKm = answerLegsArr.getAsJsonObject("distance"); //distansen

                JsonObject timeTrip = answerLegsArr.getAsJsonObject("duration");

                return "Distansen är : " + distanceKm.get("text").getAsString() + " Tiden är: " + timeTrip.get("text").getAsString();

            } else {

                return "Ingen väg hittades";


            }
        }
        return  "ingen väg hittade";
    }


        public String getPolyline () {

            JsonObject polylineObj = JsonParser.parseString(jSonRoute).getAsJsonObject();

            JsonArray polyArr = polylineObj.getAsJsonArray("routes");

            if (polyArr.size() > 0) {
                JsonObject routeTo = polyArr.get(0).getAsJsonObject();
                JsonObject overViewLine = routeTo.getAsJsonObject("overview_polyline");

                String thePolyline = overViewLine.get("points").getAsString();

                System.out.println("The polyline code " + thePolyline);

                return thePolyline;

            } else {
                return "No polyline could be found";
            }


        }

    }


