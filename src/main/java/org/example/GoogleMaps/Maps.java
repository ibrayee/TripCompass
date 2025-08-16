package org.example.GoogleMaps;

import io.github.cdimascio.dotenv.Dotenv;
import io.javalin.Javalin;
import org.example.AmadeusService;

//sammanställer från geocodings och skickar svar till en integrationsklass och sen till frontend
public class Maps {


    private static final Dotenv dotenv = Dotenv.configure().load();
    private static final String GOOGLE_MAPS_API_KEY = dotenv.get("GOOGLE_MAPS_API_KEY");
    private static final String AMADEUS_API_KEY = dotenv.get("AMADEUS_API_KEY");
    private static final String AMADEUS_API_SECRET = dotenv.get("AMADEUS_API_SECRET");





    public static void main (String [] args) {
     /*   System.out.println("API-nyckel: " + getGoogleApiKey());
       // Geocodes geocodes = new Geocodes("Malmö");
        RouteInfo routeInfo = new RouteInfo("Malmö", "Stockholm");
        routeInfo.fetchRoute();
        System.out.println(routeInfo.getRouteTimeAndDist());


        PlacesNearby places = new PlacesNearby("59.3293", "18.0686"); // Stockholm

        String restaurantInfo = places.getPlaceNameAndAdress("restaurant", "restaurang");
        String attractionInfo = places.getPlaceNameAndAdress("tourist_attraction", "Sevärdheter");
        System.out.println(restaurantInfo + " "+ attractionInfo);

        routeInfo.getPolyline();

*/
        Javalin app = Javalin.create().start(7000); // servern körs på port 7000

        // Lägg till Google Maps endpoints
       // GoogleJavalin googleJavalin = new GoogleJavalin();
       // googleJavalin.theRouteTimeAndDistance(app);
        MashupJavalin mashup = new MashupJavalin();
        mashup.flightsAndPolyline(app);
        mashup.hotelsAndSights(app);
        mashup.distToHotel(app);
        mashup.distToAirport(app);
        System.out.println("Server igång på http://localhost:7000");
    }
    }



