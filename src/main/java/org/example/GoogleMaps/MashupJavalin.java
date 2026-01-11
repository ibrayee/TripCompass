package org.example.GoogleMaps;

import com.amadeus.exceptions.ResponseException;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.example.AmadeusService;
import org.example.AppConfig;
import org.example.ValidationUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.List;
import java.util.Map;

public class MashupJavalin {

    private final AppConfig config;
    private final AmadeusService amadeusService;

    public MashupJavalin(AppConfig config, AmadeusService amadeusService) {
        this.config = config;
        this.amadeusService = amadeusService;
    }

    private boolean ensureAmadeusConfigured(Context ctx) {
        if (amadeusService == null) {
            ctx.status(503).json(Map.of(
                    "error", "Amadeus API not configured",
                    "message", "Set AMADEUS_API_KEY and AMADEUS_API_SECRET to enable mashup endpoints."
            ));
            return false;
        }
        return true;
    }

    private Optional<String> getMapsApiKey(Context ctx) {
        if (config.googleMapsApiKey().isEmpty()) {
            ctx.status(503).json(Map.of(
                    "error", "Google Maps API not configured",
                    "message", "Set GOOGLE_MAPS_API_KEY to enable mashup endpoints."
            ));
            return Optional.empty();
        }
        return config.googleMapsApiKey();
    }


    // Polylinjen (google maps) ritas ut mellan två platser (amadeus)
    public void flightsAndPolyline(Javalin app) {

        app.get("/mashupJavalin/flightsAndPolyline", ctx -> {
            if (!ensureAmadeusConfigured(ctx)) {
                return;
            }

            String startPlace = ctx.queryParam("startPlace");
            String endPlace = ctx.queryParam("endPlace");

            if (startPlace == null || startPlace.isBlank() || endPlace == null || endPlace.isBlank()) {
                ctx.status(400).result("startPlace and endPlace has to be typed");
                return;
            }

            double[] startCityCoords = amadeusService.geocodeCityToCoords(startPlace);
            double[] endCityCoords = amadeusService.geocodeCityToCoords(endPlace);
            if (startCityCoords == null || endCityCoords == null) {
                ctx.status(404).result("Could not find coordinates for given cities");
                return;
            }

            String startAirport;
            String endAirport;
            try {
                startAirport = amadeusService.getNearestAirport(startCityCoords[0], startCityCoords[1]);
                endAirport = amadeusService.getNearestAirport(endCityCoords[0], endCityCoords[1]);
            } catch (ResponseException e) {
                ctx.status(500).result("Failed to find nearest airports");
                return;
            }
            if (startAirport == null || endAirport == null) {
                ctx.status(404).result("Could not find airport codes for given cities");
                return;
            }

            double[] startAirportCoords = amadeusService.geocodeCityToCoords(startAirport);
            double[] endAirportCoords = amadeusService.geocodeCityToCoords(endAirport);

            if (startAirportCoords == null || endAirportCoords == null) {
                ctx.status(404).result("Could not geocode airports");
                return;
            }

            String polyline = encodePolyline(new double[][]{startAirportCoords, endAirportCoords});
            JsonObject routeObj = new JsonObject();
            routeObj.addProperty("startAirport", startAirport);
            routeObj.addProperty("endAirport", endAirport);
            routeObj.addProperty("polyline", polyline);


            ctx.result(routeObj.toString())
                    .contentType("application/json");        //returnerar polyline

        });

    }
    private static String encodePolyline(double[][] coords) {
        StringBuilder result = new StringBuilder();
        long lastLat = 0;
        long lastLng = 0;
        for (double[] coord : coords) {
            long lat = Math.round(coord[0] * 1e5);
            long lng = Math.round(coord[1] * 1e5);
            long dLat = lat - lastLat;
            long dLng = lng - lastLng;
            encode(dLat, result);
            encode(dLng, result);
            lastLat = lat;
            lastLng = lng;
        }
        return result.toString();
    }

    private static void encode(long value, StringBuilder sb) {
        value <<= 1;
        if (value < 0) value = ~value;
        while (value >= 0x20) {
            int next = (int) ((0x20 | (value & 0x1f)) + 63);
            sb.append((char) next);
            value >>= 5;
        }
        sb.append((char) (value + 63));
    }


    //En metod som visar sevärdheter runtomkring(google maps) när man väljer ett hotell (amadeus)
    //kanske använda citycoords från amadeus?
    /**
     * JSON response structure:
     * {
     *   "hotels": [{
     *       "name": String,
     *       "lat": double,
     *       "lng": double,
     *       "thumbnailUrl": String,
     *       "nightlyPrice": double
     *   }, ...],
     *   "sights": [ {"Name of place ": ..., "adress ": ..., "type of place": ...}, ... ]
     * }
     */
    public void hotelsAndSights(Javalin app) {
        app.get("/mashupJavalin/hotelsAndSights", ctx -> {
            if (!ensureAmadeusConfigured(ctx)) {
                return;
            }
            Optional<String> apiKey = getMapsApiKey(ctx);
            if (apiKey.isEmpty()) {
                return;
            }

            String hotelName = ctx.queryParam("hotelName");
            String city = ctx.queryParam("city");
            String placeType = ctx.queryParam("placeType");


            if (placeType == null || placeType.isBlank()) {
                ctx.status(400).result("placeType has to be typed");
                return;
            }
            double[] coords = null;
            if (hotelName != null && !hotelName.isBlank()) {
                coords = geocodeToCoords(hotelName, apiKey.get()).orElse(null);
            } else if (city != null && !city.isBlank()) {
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

            PlacesNearby placesNearby = new PlacesNearby(lat, lng, apiKey.get());

            JsonArray sightsArr;
            try {
                String results = placesNearby.getPlaceNameAndAdress(placeType);
                sightsArr = JsonParser.parseString(results).getAsJsonArray();
            } catch (Exception e) {
                ctx.status(500).result("Failed to get nearby places");
                return;
            }

            JsonArray hotelsArr = new JsonArray();
            try {
                JsonArray hotels = JsonParser
                        .parseString(amadeusService.getHotelsByGeocode(coords[0], coords[1], 5))
                        .getAsJsonArray();

                HttpClient client = HttpClient.newHttpClient();
                String checkInDate = ctx.queryParam("checkInDate");
                if (checkInDate == null || checkInDate.isBlank()) {
                    checkInDate = LocalDate.now().plusDays(1).toString();
                }
                String checkOutDate = ctx.queryParam("checkOutDate");
                int adults = 1;
                try { adults = Integer.parseInt(ctx.queryParam("adults")); } catch (Exception ignored) {}
                int rooms = 1;
                try { rooms = Integer.parseInt(ctx.queryParam("roomQuantity")); } catch (Exception ignored) {}

                for (var elem : hotels) {
                    JsonObject hObj = elem.getAsJsonObject();
                    if (!hObj.has("hotelId") || !hObj.has("name") || !hObj.has("geoCode")) {
                        continue;
                    }
                    String hId = hObj.get("hotelId").getAsString();
                    String hName = hObj.get("name").getAsString();
                    JsonObject geo = hObj.getAsJsonObject("geoCode");
                    double hLat = geo.get("latitude").getAsDouble();
                    double hLng = geo.get("longitude").getAsDouble();

                    // Fetch price
                    double price;
                    try {
                        JsonArray offerRoot = JsonParser
                                .parseString(amadeusService.getHotelOffers(hId, adults, checkInDate, rooms, checkOutDate))                                .getAsJsonArray();
                        if (offerRoot.size() == 0) continue;
                        JsonArray offerList = offerRoot.get(0).getAsJsonObject().getAsJsonArray("offers");
                        if (offerList == null || offerList.size() == 0) continue;
                        price = offerList.get(0).getAsJsonObject()
                                .getAsJsonObject("price")
                                .get("total").getAsDouble();
                    } catch (Exception e) {
                        continue; // skip if pricing unavailable
                    }

                    // Fetch thumbnail from Google Places
                    String thumb = null;
                    try {
                        String encoded = URLEncoder.encode(hName + (city != null ? ", " + city : ""), StandardCharsets.UTF_8);
                        String url = "https://maps.googleapis.com/maps/api/place/findplacefromtext/json?input=" +
                                encoded + "&inputtype=textquery&fields=photos&key=" + apiKey.get();
                        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).build();
                        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                        JsonObject gObj = JsonParser.parseString(resp.body()).getAsJsonObject();
                        JsonArray candidates = gObj.getAsJsonArray("candidates");
                        if (candidates != null && candidates.size() > 0) {
                            JsonArray photos = candidates.get(0).getAsJsonObject().getAsJsonArray("photos");
                            if (photos != null && photos.size() > 0) {
                                String ref = photos.get(0).getAsJsonObject().get("photo_reference").getAsString();
                                thumb = "https://maps.googleapis.com/maps/api/place/photo?maxwidth=400&photo_reference=" +
                                        ref + "&key=" + apiKey.get();
                            }
                        }
                    } catch (Exception e) {
                        thumb = null;
                    }
                    if (thumb == null) continue;

                    JsonObject out = new JsonObject();
                    out.addProperty("name", hName);
                    out.addProperty("lat", hLat);
                    out.addProperty("lng", hLng);
                    out.addProperty("thumbnailUrl", thumb);
                    out.addProperty("nightlyPrice", price);
                    hotelsArr.add(out);
                }
            } catch (Exception e) {
                ctx.status(500).result("Failed to get hotels");
                return;
            }

            JsonObject response = new JsonObject();
            response.add("hotels", hotelsArr);
            response.add("sights", sightsArr);

            ctx.result(response.toString()).contentType("application/json");
        });


    }

    //En metod som ritar polyline och visar distans från flygplats till hotell
    public void distToHotel(Javalin app) {
        app.get("/mashupJavalin/distToHotel", ctx -> {
            if (!ensureAmadeusConfigured(ctx)) {
                return;
            }
            Optional<String> apiKey = getMapsApiKey(ctx);
            if (apiKey.isEmpty()) {
                return;
            }

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

            double[] hotelCoords = geocodeToCoords(hotel, apiKey.get()).orElse(null);
            if (hotelCoords == null) {
                ctx.status(404).result("Could not find coordinates for hotel");
                return;
            }
            String airportLngLat = airportCoords[0] + "," + airportCoords[1];
            String hotelLngLat = hotelCoords[0] + "," + hotelCoords[1];

            RouteInfo route = new RouteInfo(airportLngLat, hotelLngLat, apiKey.get());
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
            if (!ensureAmadeusConfigured(ctx)) {
                return;
            }
            Optional<String> apiKey = getMapsApiKey(ctx);
            if (apiKey.isEmpty()) {
                return;
            }

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

                RouteInfo routeInfo = new RouteInfo(cityCoordStr, airportCoordStr, apiKey.get());
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



    }


    public void nearbyAirports(Javalin app) {

        app.get("/mashupJavalin/nearbyAirports", ctx -> {
            if (!ensureAmadeusConfigured(ctx)) {
                return;
            }

            String latStr = ctx.queryParam("lat");
            String lngStr = ctx.queryParam("lng");
            String radiusStr = ctx.queryParam("radiusKm");
            String limitStr = ctx.queryParam("limit");

            if (!ValidationUtils.isValidCoordinates(latStr, lngStr)) {
                ctx.status(400).result("lat and lng must be provided");
                return;
            }

            double lat;
            double lng;
            try {
                lat = Double.parseDouble(latStr);
                lng = Double.parseDouble(lngStr);
            } catch (NumberFormatException e) {
                ctx.status(400).result("Invalid coordinates");
                return;
            }

            int radiusKm = 200;
            int limit = 5;
            if (radiusStr != null && !radiusStr.isBlank()) {
                try {
                    radiusKm = Integer.parseInt(radiusStr);
                } catch (NumberFormatException ignored) {}
            }
            if (limitStr != null && !limitStr.isBlank()) {
                try {
                    limit = Integer.parseInt(limitStr);
                } catch (NumberFormatException ignored) {}
            }

            try {
                List<Map<String, Object>> airports = amadeusService.getNearbyAirportDetails(lat, lng, radiusKm, limit);
                ctx.json(airports);
            } catch (ResponseException e) {
                ctx.status(502).result("Failed to fetch airports");
            } catch (TimeoutException e) {
                ctx.status(504).result("Request timed out");
            } catch (Exception e) {
                ctx.status(500).result("Internal Server Error");
            }

        });

    }

    private Optional<double[]> geocodeToCoords(String address, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
        try {
            String encoded = URLEncoder.encode(address, StandardCharsets.UTF_8);
            String url = "https://maps.googleapis.com/maps/api/geocode/json?address=" + encoded + "&key=" + apiKey;
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
            JsonArray results = root.getAsJsonArray("results");
            if (results == null || results.size() == 0) {
                return Optional.empty();
            }
            JsonObject location = results.get(0)
                    .getAsJsonObject()
                    .getAsJsonObject("geometry")
                    .getAsJsonObject("location");
            double lat = location.get("lat").getAsDouble();
            double lng = location.get("lng").getAsDouble();
            return Optional.of(new double[]{lat, lng});
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}