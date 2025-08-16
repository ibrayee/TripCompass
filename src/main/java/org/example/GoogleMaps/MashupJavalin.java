package org.example.GoogleMaps;

import com.google.gson.JsonObject;
import io.github.cdimascio.dotenv.Dotenv;
import io.javalin.Javalin;
import org.example.AmadeusService;

public class MashupJavalin {


    private static final Dotenv dotenv = Dotenv.load();

    private static final AmadeusService amadeusService = new AmadeusService(
            dotenv.get("AMADEUS_API_KEY"),
            dotenv.get("AMADEUS_API_SECRET")
    );


    // Polylinjen (google maps)  ritas ut mellan två platser (amadeus)
    public void flightsAndPolyline(Javalin app) {

        app.get("/mashupJavalin/flightsAndPolyline", ctx -> {

            String startPlace = ctx.queryParam("startPlace");
            String endPlace = ctx.queryParam ("endPlace");

            if (startPlace == null || startPlace.isBlank() || endPlace == null || endPlace.isBlank()) {
                ctx.status(400).result("starPlace and endPlace has to be typed");
                return;
            }

            double[] startCoords =  amadeusService.geocodeCityToCoords(startPlace); //från amadeus
            double[] endCoords =  amadeusService.geocodeCityToCoords(endPlace);

            if (startCoords == null || endCoords == null) {
                ctx.status(404).result("Could not find coordinates for given cities");
                return;
            }

            String startLngLong = startCoords [0] + "," + startCoords [1]; //omvandlas till strängar
            String endLngLong = endCoords [0] + "," +  endCoords [1];

            RouteInfo routeInfo = new RouteInfo(startLngLong, endLngLong); //google använder för att hämta polyline

            routeInfo.fetchRoute();
            String polyline = routeInfo.getPolyline();

            JsonObject routeObj = new JsonObject();
            routeObj.addProperty("startPlace", startPlace);
            routeObj.addProperty("endPlace", endPlace);
            routeObj.addProperty("polyline", polyline);


            ctx.result(routeObj.toString())
                    .contentType("application/json");        //returnerar polyline

        });

    }


    //En metod som visar sevärdheter runtomkring(google maps) när man väljer ett hotell (amadeus)
    //kanske använda citycoords från amadeus?
    public void hotelsAndSights(Javalin app) {
        app.get("/mashupJavalin/hotelsAndSights", ctx -> {

            String hotelName;
            String placeType = ctx.queryParam("placeType");

            //Geocoding code:

                    if (lat == null || lng == null || placeType == null) {
                        ctx.status(400).result("There is no lat, lng and/or placeType");
                        return;
                    }


            ctx.result(results)
                    .contentType("application/json");
        });


    }

    //En metod som ritar polyline och visar distans från flygplats till hotell
    public void distToHotel (Javalin app) {
        app.get("/mashupJavalin/distToHotel", ctx -> {

        String destAirport = ctx.queryParam ("endPlace");
        String hotell = ctx.queryParam ("hotell");

        String airportLngLat = airportCoords[0] + "," + airportCoords[1]; //från amadeus
        String hotelLngLat = hotelCoords[0] + "," + hotelCoords[1];

        RouteInfo route = new RouteInfo(airportLngLat, hotelLngLat);   //från google maps
        route.fetchRoute();
        String polyline = route.getPolyline();




    });
    }

    //En metod som ritar polyline (google maps) från stad till flygplats (amadeus) och visar distans och tid bil
    public void distToAirport () {

    }



}


