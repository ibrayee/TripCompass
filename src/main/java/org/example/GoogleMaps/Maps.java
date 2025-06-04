package org.example.GoogleMaps;

import io.github.cdimascio.dotenv.Dotenv;

//sammanställer från geocodings och skickar svar till en integrationsklass och sen till frontend
public class Maps {


    private static final Dotenv dotenv = Dotenv.configure().load();
    private static final String GOOGLE_MAPS_API_KEY = dotenv.get("GOOGLE_MAPS_API_KEY");


    public static String getGoogleApiKey() {
        return dotenv.get("GOOGLE_MAPS_API_KEY");
    }




    public static void main (String [] args) {
        System.out.println("API-nyckel: " + getGoogleApiKey());
       // Geocodes geocodes = new Geocodes("Malmö");
        RouteInfo routeInfo = new RouteInfo("Malmö", "Stockholm");
        routeInfo.fetchRoute();
        System.out.println(routeInfo.getRouteTimeDist());

        routeInfo.getPolyline();


    }


}
