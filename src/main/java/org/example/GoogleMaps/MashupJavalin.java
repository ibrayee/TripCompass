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
                ctx.status(400).result("startPlace and endPlace has to be typed");
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

            String hotelName = ctx.queryParam("hotelName");
            String city = ctx.queryParam("city");            String placeType = ctx.queryParam("placeType");

            //Geocoding code:

            if (placeType == null || placeType.isBlank()) {
                ctx.status(400).result("placeType has to be typed");
                return;
            }
            double[] coords = null;
            if (hotelName != null && !hotelName.isBlank()) {
                Geocodes geo = new Geocodes(hotelName);
                coords = new double[]{geo.getLat(), geo.getLng()};            } else if (city != null && !city.isBlank()) {
                coords = amadeusService.geocodeCityToCoords(city);
            } else {
                ctx.status(400).result("hotelName or city has to be typed");
                return;
            }

            if (coords == null) {
                ctx.status(404).result("Could not find coordinates for given hotel/city");
                return;
            }

            String lat = String.valueOf(coords[0]);
            String lng = String.valueOf(coords[1]);

            PlacesNearby placesNearby = new PlacesNearby(lat, lng);

            String results;
            try {
                results = placesNearby.getPlaceNameAndAdress(placeType);
            } catch (Exception e) {
                ctx.status(500).result("Failed to get nearby places");
                return;
            }

            ctx.result(results)
                    .contentType("application/json");
        });


    }

    //En metod som ritar polyline och visar distans från flygplats till hotell
    public void distToHotel(Javalin app) {
        app.get("/mashupJavalin/distToHotel", ctx -> {

            String airport = ctx.queryParam("airport");
            String hotel = ctx.queryParam("hotel");

            if (airport == null || airport.isBlank() || hotel == null || hotel.isBlank()) {
                ctx.status(400).result("airport and hotel parameters are required");
                return;
            }

            double[] airportCoords = amadeusService.geocodeCityToCoords(airport);
            if (airportCoords == null) {
                ctx.status(404).result("Could not find coordinates for airport");
                return;
            }

            double[] hotelCoords;
            try {
                Geocodes geo = new Geocodes(hotel);
                hotelCoords = new double[]{geo.getLat(), geo.getLng()};
            } catch (Exception e) {
                ctx.status(500).result("Failed to geocode hotel: " + e.getMessage());
                return;
            }
                    String airportLngLat = airportCoords[0] + "," + airportCoords[1];
                    String hotelLngLat = hotelCoords[0] + "," + hotelCoords[1];

                    RouteInfo route = new RouteInfo(airportLngLat, hotelLngLat);
                    route.fetchRoute();

                    String polyline = route.getPolyline();
                    String distance = route.getDistance();
                    String duration = route.getDuration();

                    if (polyline == null || distance == null || duration == null) {
                        ctx.status(500).result("Unable to fetch route information");
                        return;
                    }
            JsonObject routeObj = new JsonObject();
            routeObj.addProperty("airport", airport);
            routeObj.addProperty("hotel", hotel);
            routeObj.addProperty("polyline", polyline);
            routeObj.addProperty("distance", distance);
            routeObj.addProperty("duration", duration);

            ctx.result(routeObj.toString()).contentType("application/json");
        }); }

    //En metod som ritar polyline (google maps) från stad till flygplats (amadeus) och visar distans och tid bil
    public void distToAirport(Javalin app) {

        app.get("/mashupJavalin/distToAirport", ctx -> {

            String city = ctx.queryParam("city");

            if (city == null || city.isBlank()) {
                ctx.status(400).result("city must be provided");
                return;
            }

            try {
                double[] cityCoords = amadeusService.geocodeCityToCoords(city);
                if (cityCoords == null) {
                    ctx.status(404).result("Could not find coordinates for city");
                    return;
                }

                String airportCode;
                try {
                    airportCode = amadeusService.findNearestAirportCode(cityCoords[0], cityCoords[1]);
                } catch (Exception e) {
                    ctx.status(500).result("Failed to locate nearest airport");
                    return;
                }

                double[] airportCoords = amadeusService.geocodeCityToCoords(airportCode);
                if (airportCoords == null) {
                    ctx.status(404).result("Could not find coordinates for airport");
                    return;
                }

                String cityCoordStr = cityCoords[0] + "," + cityCoords[1];
                String airportCoordStr = airportCoords[0] + "," + airportCoords[1];

                RouteInfo routeInfo = new RouteInfo(cityCoordStr, airportCoordStr);
                routeInfo.fetchRoute();

                String polyline = routeInfo.getPolyline();
                String distTime = routeInfo.getRouteTimeAndDist();

                String distance = null;
                String duration = null;
                if (distTime != null) {
                    int timeIdx = distTime.indexOf("Tiden är:");
                    int distIdx = distTime.indexOf("Distansen är :");
                    if (distIdx >= 0 && timeIdx > distIdx) {
                        distance = distTime.substring(distIdx + "Distansen är :".length(), timeIdx).trim();
                        duration = distTime.substring(timeIdx + "Tiden är:".length()).trim();
                    }
                }

                JsonObject result = new JsonObject();
                result.addProperty("city", city);
                result.addProperty("airport", airportCode);
                if (polyline != null) {
                    result.addProperty("polyline", polyline);
                }
                if (distance != null) {
                    result.addProperty("distance", distance);
                }
                if (duration != null) {
                    result.addProperty("duration", duration);
                }

                ctx.result(result.toString()).contentType("application/json");

            } catch (Exception e) {
                ctx.status(500).result("Internal server error");
            }

        });



}}


