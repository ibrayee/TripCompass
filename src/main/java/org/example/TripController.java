package org.example;

import com.amadeus.exceptions.ResponseException;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.cdimascio.dotenv.Dotenv;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.example.GoogleMaps.MashupJavalin;

import org.slf4j.LoggerFactory;

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
    private static final String GOOGLE_MAPS_API_KEY = dotenv.get("GOOGLE_MAPS_API_KEY");

    private static final Logger logger = LoggerFactory.getLogger(TripController.class);

    static {
        if (AMADEUS_API_KEY == null || AMADEUS_API_KEY.isBlank()
                || AMADEUS_API_SECRET == null || AMADEUS_API_SECRET.isBlank()) {
            logger.error("Amadeus API credentials are missing. Please set AMADEUS_API_KEY and AMADEUS_API_SECRET.");
            throw new IllegalStateException("Missing Amadeus API credentials");
        }
    }

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
        app.get("/config/maps-key", ctx -> {
            if (GOOGLE_MAPS_API_KEY == null || GOOGLE_MAPS_API_KEY.isBlank()) {
                ctx.status(404).json(Map.of("error", "Google Maps API key not configured"));
            } else {
                ctx.json(Map.of("apiKey", GOOGLE_MAPS_API_KEY));
            }
        });

        // Other endpoints kept as before
        app.get("/search/flights", TripController::handleFlightSearch);
        app.get("/search/nearby", TripController::handleNearbySearch);
        app.get("/search/locations", TripController::handleLocationSearch);

        // New: delegate to TripInfoService
        app.get("/trip-info", TripController::handleTripInfo);

        app.get("/nearby-airports", TripController::handleNearbyAirports);
        MashupJavalin mashup = new MashupJavalin();
        mashup.flightsAndPolyline(app);
        mashup.hotelsAndSights(app);
        mashup.distToHotel(app);
        mashup.distToAirport(app);

    }

    /**
     * Handle /trip-info route:
     * 1) Validate query parameters
     * 2) Parse values
     * 3) Call service
     * 4) Map exceptions to HTTP codes
     */
    private static void handleTripInfo(Context ctx) {
        String latStr = ctx.queryParam("lat");
        String lngStr = ctx.queryParam("lng");
        String origin = ctx.queryParam("origin");
        String originLatStr = ctx.queryParam("originLat");
        String originLngStr = ctx.queryParam("originLng");
        String checkInDate = ctx.queryParam("checkInDate");
        String adultsStr = ctx.queryParam("adults");
        String roomQuantityStr = ctx.queryParam("roomQuantity");

        boolean hasOriginCoords = originLatStr != null && originLngStr != null
                && ValidationUtils.isValidCoordinates(originLatStr, originLngStr);

        boolean hasOrigin = origin != null && !origin.isEmpty();

        if (!ValidationUtils.isValidCoordinates(latStr, lngStr)
                || !ValidationUtils.isFutureDate(checkInDate)
                || !ValidationUtils.isPositiveInteger(adultsStr)
                || !ValidationUtils.isPositiveInteger(roomQuantityStr)
                || (!hasOrigin && !hasOriginCoords)) {
            ctx.status(400).json(Map.of(
                    "error", "Missing or invalid parameters"
            ));
            return;
        }

        try {
            double lat = Double.parseDouble(latStr.trim().replace(",", "."));
            double lng = Double.parseDouble(lngStr.trim().replace(",", "."));
            int adults = Integer.parseInt(adultsStr.trim());
            int rooms = Integer.parseInt(roomQuantityStr.trim());

            double oLat = 0;
            double oLng = 0;
            if (hasOriginCoords) {
                oLat = Double.parseDouble(originLatStr.trim().replace(",", "."));
                oLng = Double.parseDouble(originLngStr.trim().replace(",", "."));
            }

            String resolvedOrigin = null;
            if (hasOrigin) {
                resolvedOrigin = origin.trim();
            } else if (hasOriginCoords) {
                resolvedOrigin = amadeusService.findNearestAirportCode(oLat, oLng);
            }

            if (resolvedOrigin == null || resolvedOrigin.isEmpty()) {
                ctx.status(404).json(Map.of(
                        "error", "No airport close to you"
                ));
                return;
            }
            System.out.println("Origin used for airport search: " + resolvedOrigin);

            Map<String, Object> result;
            if (hasOriginCoords) {
                result = tripInfoService.getTripInfo(
                        lat, lng, resolvedOrigin, oLat, oLng, checkInDate, adults, rooms
                );
            } else {
                result = tripInfoService.getTripInfo(
                        lat, lng, resolvedOrigin, checkInDate, adults, rooms
                );
            }

            ctx.contentType("application/json");
            ctx.json(result);

        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Map.of(
                    "error", e.getMessage()
            ));
        } catch (IllegalStateException e) {
            ctx.status(404).json(Map.of(
                    "error", e.getMessage()
            ));
        } catch (ResponseException e) {
            ctx.status(502).json(Map.of(
                    "error", "Upstream service error",
                    "details", e.getMessage()
            ));
        } catch (Exception e) {
            ctx.status(500).json(Map.of(
                    "error", "Internal Server Error",
                    "message", e.getMessage()
            ));
        }

    }

    private static void handleLocationSearch(Context ctx) {
        String keyword = ctx.queryParam("keyword");
        if (keyword == null || keyword.isBlank()) {
            ctx.status(400).json(Map.of("error", "Missing keyword"));
            return;
        }
        try {
            List<Map<String, Object>> locations = amadeusService.searchLocations(keyword);
            ctx.json(locations);
        } catch (ResponseException e) {
            ctx.status(502).json(Map.of(
                    "error", "Upstream service error",
                    "details", e.getMessage()
            ));
        } catch (Exception e) {
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
                var segments = itinerary.getAsJsonArray("segments");
                var firstSegment = segments.get(0).getAsJsonObject();
                var lastSegment = segments.get(segments.size() - 1).getAsJsonObject();
                var departure = firstSegment.getAsJsonObject("departure");
                var arrival = lastSegment.getAsJsonObject("arrival");
                JsonObject simplified = new JsonObject();
                simplified.addProperty("origin", departure.get("iataCode").getAsString());
                simplified.addProperty("destination", arrival.get("iataCode").getAsString());
                simplified.addProperty("departure", departure.get("at").getAsString());
                simplified.addProperty("arrival", arrival.get("at").getAsString());
                simplified.addProperty("duration", itinerary.get("duration").getAsString());
                simplified.addProperty("price", offer.getAsJsonObject("price").get("total").getAsString());
                simplified.addProperty("currency", offer.getAsJsonObject("price").get("currency").getAsString());
                simplified.addProperty("airline", firstSegment.get("carrierCode").getAsString());

                JsonArray legs = new JsonArray();
                JsonArray stopovers = new JsonArray();
                for (int i = 0; i < segments.size(); i++) {
                    var seg = segments.get(i).getAsJsonObject();
                    var segDep = seg.getAsJsonObject("departure");
                    var segArr = seg.getAsJsonObject("arrival");

                    JsonObject leg = new JsonObject();
                    leg.addProperty("origin", segDep.get("iataCode").getAsString());
                    leg.addProperty("destination", segArr.get("iataCode").getAsString());
                    leg.addProperty("departure", segDep.get("at").getAsString());
                    leg.addProperty("arrival", segArr.get("at").getAsString());
                    leg.addProperty("airline", seg.get("carrierCode").getAsString());
                    legs.add(leg);

                    if (i < segments.size() - 1) {
                        stopovers.add(segArr.get("iataCode").getAsString());
                    }
                }
                simplified.add("segments", legs);
                if (stopovers.size() > 0) {
                    simplified.add("stopovers", stopovers);
                }                simplifiedArray.add(simplified);
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
    private static void handleNearbyAirports(Context ctx) {
        String latStr = ctx.queryParam("lat");
        String lngStr = ctx.queryParam("lng");
        String limitStr = ctx.queryParam("limit");
        String radiusStr = ctx.queryParam("radius");
        int limit = 5; // default
        if (limitStr != null && !limitStr.isEmpty()) {
            try {
                limit = Integer.parseInt(limitStr);
            } catch (NumberFormatException ignored) {

            }
        }
        int radius = 200; // default radius in km
        if (radiusStr != null && !radiusStr.isEmpty()) {
            try {
                radius = Integer.parseInt(radiusStr);
            } catch (NumberFormatException ignored) {

            }
        }
        if (!ValidationUtils.isValidCoordinates(latStr, lngStr)) {
            ctx.status(400).json(Map.of("error", "Invalid coordinates"));            return;
        }
        try {
            double lat = Double.parseDouble(latStr);
            double lng = Double.parseDouble(lngStr);

            List<Map<String, Object>> airports =
                    amadeusService.getNearbyAirportDetails(lat, lng, radius, limit);
            if (airports.isEmpty()) {
                String nearestCode = amadeusService.findNearestAirportCode(lat, lng, radius);
                if (nearestCode != null) {
                    List<Map<String, Object>> fallback =
                            amadeusService.getNearbyAirportDetails(lat, lng, 1000, 1);
                    if (!fallback.isEmpty()) {
                        ctx.json(fallback);
                    } else {
                        ctx.json(List.of(Map.of("iata", nearestCode)));
                    }
                    return;
                }

                ctx.status(404).json(
                        Map.of("error", "Dataset di test limitato a US/ES/UK/DE/IN")
                );
                return;
            }

            ctx.json(airports);
        } catch (ResponseException e) {
            ctx.status(502).json(Map.of("error", "Upstream service error"));
        } catch (Exception e) {
            ctx.status(500).json(
                    Map.of("error", "Internal Server Error", "message", e.getMessage())
            );
        }
    }
}

