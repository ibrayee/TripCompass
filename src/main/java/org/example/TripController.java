package org.example;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.cdimascio.dotenv.Dotenv;
import io.javalin.Javalin;
import io.javalin.http.Context;
import com.amadeus.exceptions.ResponseException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



/**
 * Controller that handles HTTP routes and delegates business logic.
 */
public class TripController {

    // Load environment variables for Amadeus API
    private static final Dotenv dotenv = Dotenv.configure().load();
    private static final String AMADEUS_API_KEY = dotenv.get("AMADEUS_API_KEY");
    private static final String AMADEUS_API_SECRET = dotenv.get("AMADEUS_API_SECRET");

    // Service instances
    private static final AmadeusService amadeusService = new AmadeusService(
            AMADEUS_API_KEY, AMADEUS_API_SECRET
    );
    private static final TripInfoService tripInfoService = new TripInfoService(amadeusService);

    public static void main(String[] args) {
        // Create and configure Javalin app
        Javalin app = Javalin.create(config -> {
            config.staticFiles.add(staticFileConfig -> {
                staticFileConfig.hostedPath = "/";
                staticFileConfig.directory = "public";
                staticFileConfig.location = io.javalin.http.staticfiles.Location.CLASSPATH;
            });
            config.plugins.enableCors(cors -> cors.add(it -> it.anyHost()));
        }).start(7000);

        // Health check
        app.get("/hello", ctx -> ctx.result("Hello TripCompass!"));

        // Other endpoints kept as before
        app.get("/search/flights", TripController::handleFlightSearch);
        app.get("/search/nearby", TripController::handleNearbySearch);

        // New: delegate to TripInfoService
        app.get("/trip-info", TripController::handleTripInfo);
    }

    /**
     * Handle /trip-info route:
     * 1) Validate query parameters
     * 2) Parse values
     * 3) Call service
     * 4) Map exceptions to HTTP codes
     */
    private static void handleTripInfo(Context ctx) {
        // 1. Validate presence and basic format of parameters
        String latStr = ctx.queryParam("lat");
        String lngStr = ctx.queryParam("lng");
        String originCity = ctx.queryParam("origin");
        String checkInDate = ctx.queryParam("checkInDate");
        String adultsStr = ctx.queryParam("adults");
        String roomQuantityStr = ctx.queryParam("roomQuantity");

        if (!ValidationUtils.isValidCoordinates(latStr, lngStr)
                || originCity == null || originCity.isEmpty()
                || !ValidationUtils.isFutureDate(checkInDate)
                || !ValidationUtils.isPositiveInteger(adultsStr)
                || !ValidationUtils.isPositiveInteger(roomQuantityStr)) {
            ctx.status(400).json(Map.of(
                    "error", "Missing or invalid parameters"
            ));
            return;
        }

        try {
            // 2. Parse numeric values
            double lat = Double.parseDouble(latStr.trim());
            double lng = Double.parseDouble(lngStr.trim());
            int adults = Integer.parseInt(adultsStr.trim());
            int rooms = Integer.parseInt(roomQuantityStr.trim());
            System.out.println("Calling /trip-info with: " + lat + ", " + lng + ", " + originCity + ", " + checkInDate);

            // 3. Delegate to TripInfoService
            Map<String, Object> result = tripInfoService.getTripInfo(
                    lat, lng, originCity, checkInDate, adults, rooms
            );

            // 4a. Success → return JSON
            ctx.contentType("application/json");
            ctx.json(result);

        } catch (IllegalArgumentException e) {
            // Parameter parsing or geocoding error
            ctx.status(400).json(Map.of(
                    "error", e.getMessage()
            ));
        } catch (IllegalStateException e) {
            // Missing resources (e.g. no airport found)
            ctx.status(404).json(Map.of(
                    "error", e.getMessage()
            ));
        } catch (ResponseException e) {
            // Upstream API failure
            ctx.status(502).json(Map.of(
                    "error", "Upstream service error",
                    "details", e.getMessage()
            ));
        } catch (Exception e) {
            // Fallback for any other errors
            ctx.status(500).json(Map.of(
                    "error", "Internal Server Error",
                    "message", e.getMessage()
            ));
        }

    }
    private static void handleFlightSearch(Context ctx) {
        String origin = ctx.queryParam("origin");
        String destination = ctx.queryParam("destination");
        String departureDate = ctx.queryParam("departureDate");
        String returnDate = ctx.queryParam("returnDate");
        String adultsStr = ctx.queryParam("adults");

        if (origin == null || destination == null || departureDate == null || !ValidationUtils.isPositiveInteger(adultsStr)) {
            ctx.status(400).result("Invalid or missing parameters.");
            return;
        }

        try {
            int adults = Integer.parseInt(adultsStr);
            String flightsJson = amadeusService.getFlightOffers(origin, destination, departureDate, returnDate, adults);
            JsonArray originalArray = JsonParser.parseString(flightsJson).getAsJsonArray();

            JsonArray simplifiedArray = new JsonArray();

            for (var offerElement : originalArray) {
                var offer = offerElement.getAsJsonObject();

                var itinerary = offer.getAsJsonArray("itineraries").get(0).getAsJsonObject();
                var segment = itinerary.getAsJsonArray("segments").get(0).getAsJsonObject();

                var departure = segment.getAsJsonObject("departure");
                var arrival = segment.getAsJsonObject("arrival");

                JsonObject simplified = new JsonObject();
                simplified.addProperty("origin", departure.get("iataCode").getAsString());
                simplified.addProperty("destination", arrival.get("iataCode").getAsString());
                simplified.addProperty("departure", departure.get("at").getAsString());
                simplified.addProperty("arrival", arrival.get("at").getAsString());
                simplified.addProperty("duration", itinerary.get("duration").getAsString());
                simplified.addProperty("price", offer.getAsJsonObject("price").get("total").getAsString());
                simplified.addProperty("currency", offer.getAsJsonObject("price").get("currency").getAsString());
                simplified.addProperty("airline", segment.get("carrierCode").getAsString());

                simplifiedArray.add(simplified);
            }

            ctx.contentType("application/json");
            ctx.result(new Gson().toJson(simplifiedArray));

        } catch (Exception e) {
            ctx.status(500).result("Internal Server Error: " + e.getMessage());
        }
    }


    private static void handleNearbySearch(Context ctx) {
        String latStr = ctx.queryParam("lat");
        String lngStr = ctx.queryParam("lng");
        String checkInDate = ctx.queryParam("checkInDate");
        String adultsStr = ctx.queryParam("adults") != null ? ctx.queryParam("adults").trim() : null;
        String roomQuantityStr = ctx.queryParam("roomQuantity") != null ? ctx.queryParam("roomQuantity").trim() : null;

        System.out.println("[DEBUG] lat=" + latStr + ", lng=" + lngStr + ", checkInDate=" + checkInDate + ", adults=" + adultsStr + ", roomQuantity=" + roomQuantityStr);

        if (!ValidationUtils.isValidCoordinates(latStr, lngStr)) {
            ctx.status(400).result("Invalid parameters: coordinates are not valid.");
            return;
        }
        if (!ValidationUtils.isFutureDate(checkInDate)) {
            ctx.status(400).result("Invalid parameters: check-in date is not valid.");
            return;
        }
        if (!ValidationUtils.isPositiveInteger(adultsStr)) {
            ctx.status(400).result("Invalid parameters: number of adults is not valid.");
            return;
        }
        if (!ValidationUtils.isPositiveInteger(roomQuantityStr)) {
            ctx.status(400).result("Invalid parameters: room quantity is not valid.");
            return;
        }

        try {
            double lat = Double.parseDouble(latStr);
            double lng = Double.parseDouble(lngStr);
            int adults = Integer.parseInt(adultsStr);
            int rooms = Integer.parseInt(roomQuantityStr);

            String hotelResponseJson = amadeusService.getHotelsByGeocode(lat, lng, 10);
            JsonArray hotelArray = JsonParser.parseString(hotelResponseJson).getAsJsonArray();

            List<Map<String, Object>> validOffers = new ArrayList<>();

            for (var element : hotelArray) {
                var hotelObj = element.getAsJsonObject();
                if (hotelObj.has("hotelId")) {
                    String hotelId = hotelObj.get("hotelId").getAsString();
                    try {
                        String offerJson = amadeusService.getHotelOffers(hotelId, adults, checkInDate, rooms);
                        Map<String, Object> hotelData = new HashMap<>();
                        hotelData.put("hotelId", hotelId);
                        hotelData.put("offers", JsonParser.parseString(offerJson));
                        validOffers.add(hotelData);
                    } catch (ResponseException e) {
                        System.out.println("[SKIP] " + hotelId + " => " + e.getMessage());
                    }
                }
                if (validOffers.size() >= 5) break;
            }

            if (validOffers.isEmpty()) {
                ctx.status(404).result("No available hotel offers for the selected location and date.");
                return;
            }

            Map<String, Object> response = new HashMap<>();
            response.put("coordinates", Map.of("lat", lat, "lng", lng));
            response.put("offers", validOffers);

            ctx.contentType("application/json");
            ctx.result(new Gson().toJson(response));
        } catch (Exception e) {
            ctx.status(500).result("Internal Server Error: " + e.getMessage());
        }
    }
}

