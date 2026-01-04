package org.example;

import com.amadeus.exceptions.ResponseException;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.cdimascio.dotenv.Dotenv;
import io.javalin.Javalin;
import org.example.HotelSearchService.HotelSummary;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;



/**
 * Controller that handles HTTP routes and delegates business logic.
 */
public class TripController {

    // Load environment variables for Amadeus API
    private static final Dotenv dotenv = Dotenv.configure().load();
    private static final AppConfig config = new AppConfig(dotenv);
    private static final String AMADEUS_API_KEY = config.amadeusApiKey();
    private static final String AMADEUS_API_SECRET = config.amadeusApiSecret();

    private static final Logger logger = LoggerFactory.getLogger(TripController.class);

    private static final boolean AMADEUS_ENABLED = AMADEUS_API_KEY != null && !AMADEUS_API_KEY.isBlank()
            && AMADEUS_API_SECRET != null && !AMADEUS_API_SECRET.isBlank();

    // Service instances
    private static final AmadeusService amadeusService = AMADEUS_ENABLED
            ? new AmadeusService(AMADEUS_API_KEY, AMADEUS_API_SECRET)
            : null;
    private static final HotelSearchService hotelSearchService = AMADEUS_ENABLED
            ? new HotelSearchService(config)
            : null;
    private static final TripInfoService tripInfoService = amadeusService != null
            ? new TripInfoService(amadeusService, hotelSearchService)
            : null;

    private static boolean ensureAmadeusConfigured(Context ctx) {
        if (!AMADEUS_ENABLED || amadeusService == null) {
            ctx.status(503).json(Map.of(
                    "error", "Amadeus API not configured",
                    "message", "Set AMADEUS_API_KEY and AMADEUS_API_SECRET to enable travel search endpoints."
            ));
            return false;
        }
        return true;
    }

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

        if (!AMADEUS_ENABLED) {
            logger.warn("Amadeus API credentials are missing. Endpoints depending on Amadeus will return 503 until configured.");
        }

        // Health check
        app.get("/hello", ctx -> ctx.result("Hello TripCompass!"));
        app.get("/config/maps-key", ctx -> {
            if (config.googleMapsApiKey().isEmpty()) {
                ctx.status(404).json(Map.of("error", "Google Maps API key not configured"));
            } else {
                ctx.json(Map.of("apiKey", config.googleMapsApiKey().orElse("")));
            }
        });

        // Other endpoints kept as before
        app.get("/search/flights", TripController::handleFlightSearch);
        app.get("/search/nearby", TripController::handleNearbySearch);
        app.get("/search/locations", TripController::handleLocationSearch);

        // New: delegate to TripInfoService
        app.get("/trip-info", TripController::handleTripInfo);

        app.get("/nearby-airports", TripController::handleNearbyAirports);

    }

    /**
     * Handle /trip-info route:
     * 1) Validate query parameters
     * 2) Parse values
     * 3) Call service
     * 4) Map exceptions to HTTP codes
     */
    private static void handleTripInfo(Context ctx) {
        if (!ensureAmadeusConfigured(ctx)) return;
        String latStr = ctx.queryParam("lat");
        String lngStr = ctx.queryParam("lng");
        String origin = ctx.queryParam("origin");
        String originLatStr = ctx.queryParam("originLat");
        String originLngStr = ctx.queryParam("originLng");
        String checkInDate = ctx.queryParam("checkInDate");
        String checkOutDate = ctx.queryParam("checkOutDate");
        String adultsStr = ctx.queryParam("adults");
        String roomQuantityStr = ctx.queryParam("roomQuantity");

        boolean hasOriginCoords = originLatStr != null && originLngStr != null
                && ValidationUtils.isValidCoordinates(originLatStr, originLngStr);

        boolean hasOrigin = origin != null && !origin.isEmpty();
        boolean hasCheckout = checkOutDate != null && !checkOutDate.isBlank();

        if (!ValidationUtils.isValidCoordinates(latStr, lngStr)
                || !ValidationUtils.isFutureDate(checkInDate)
                || !ValidationUtils.isPositiveInteger(adultsStr)
                || !ValidationUtils.isPositiveInteger(roomQuantityStr)
                || (hasCheckout && !ValidationUtils.isValidDateRange(checkInDate, checkOutDate))
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
            logger.info("Origin used for airport search: {}", resolvedOrigin);

            Map<String, Object> result;
            if (hasOriginCoords) {
                result = tripInfoService.getTripInfo(
                        lat, lng, resolvedOrigin, oLat, oLng, checkInDate, checkOutDate, adults, rooms
                );
            } else {
                result = tripInfoService.getTripInfo(
                        lat, lng, resolvedOrigin, checkInDate, checkOutDate,adults, rooms
                );
            }

            ctx.contentType("application/json");
            ctx.json(result);

        } catch (IllegalArgumentException e) {
            logger.warn("Trip-info validation error: {}", e.getMessage());
            ctx.status(400).json(Map.of(
                    "error", e.getMessage()
            ));
        } catch (IllegalStateException e) {
            logger.warn("Trip-info state error: {}", e.getMessage());
            ctx.status(404).json(Map.of(
                    "error", e.getMessage()
            ));
        } catch (ResponseException e) {
            ctx.status(502).json(Map.of(
                    "error", "Upstream service error",
                    "details", e.getMessage()
            ));
        } catch (TimeoutException e) {
            logger.error("Trip-info timed out", e);
            ctx.status(504).json(Map.of(
                    "error", "Request timed out",
                    "message", "The upstream service did not respond in time."
            ));
        } catch (Exception e) {
            logger.error("Unexpected error while handling trip-info", e);
            ctx.status(500).json(Map.of(
                    "error", "Internal Server Error",
                    "message", e.getMessage()
            ));
        }

    }

    private static void handleLocationSearch(Context ctx) {
        if (!ensureAmadeusConfigured(ctx)) return;
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
        } catch (TimeoutException e) {
            ctx.status(504).json(Map.of(
                    "error", "Request timed out",
                    "message", "Location search exceeded timeout"
            ));
        } catch (Exception e) {
            ctx.status(500).json(Map.of(
                    "error", "Internal Server Error",
                    "message", e.getMessage()
            ));
        }
    }

    private static void handleFlightSearch(Context ctx) {
        if (!ensureAmadeusConfigured(ctx)) return;
        String origin = ctx.queryParam("origin");
        String destination = ctx.queryParam("destination");
        String departureDate = ctx.queryParam("departureDate");
        String returnDate = ctx.queryParam("returnDate");
        String adultsStr = ctx.queryParam("adults");

        if (origin == null || origin.isBlank()
                || destination == null || destination.isBlank()
                || departureDate == null || !ValidationUtils.isFutureDate(departureDate)
                || !ValidationUtils.isPositiveInteger(adultsStr)) {
            ctx.status(400).json(Map.of("error", "Invalid or missing parameters."));
            return;
        }

        try {
            int adults = Integer.parseInt(adultsStr);
            String flightsJson = amadeusService.getFlightOffers(origin, destination, departureDate, returnDate, adults);
            JsonArray originalArray = JsonParser.parseString(flightsJson).getAsJsonArray();

            JsonArray simplifiedArray = new JsonArray();

            for (var offerElement : originalArray) {
                if (!offerElement.isJsonObject()) continue;
                JsonObject offer = offerElement.getAsJsonObject();

                JsonArray itineraries = offer.has("itineraries") ? offer.getAsJsonArray("itineraries") : null;
                if (itineraries == null || itineraries.size() == 0) continue;
                JsonObject itinerary = Optional.ofNullable(itineraries.get(0))
                        .filter(JsonElement::isJsonObject)
                        .map(JsonElement::getAsJsonObject)
                        .orElse(null);
                if (itinerary == null || !itinerary.has("segments")) continue;

                JsonArray segments = itinerary.getAsJsonArray("segments");
                if (segments.size() == 0) continue;
                JsonObject firstSegment = Optional.ofNullable(segments.get(0))
                        .filter(JsonElement::isJsonObject)
                        .map(JsonElement::getAsJsonObject)
                        .orElse(null);
                JsonObject lastSegment = Optional.ofNullable(segments.get(segments.size() - 1))
                        .filter(JsonElement::isJsonObject)
                        .map(JsonElement::getAsJsonObject)
                        .orElse(null);
                if (firstSegment == null || lastSegment == null
                        || !firstSegment.has("departure") || !lastSegment.has("arrival")) {
                    continue;
                }
                JsonObject departure = firstSegment.getAsJsonObject("departure");
                JsonObject arrival = lastSegment.getAsJsonObject("arrival");
                if (!departure.has("iataCode") || !arrival.has("iataCode") || !departure.has("at") || !arrival.has("at")) {
                    continue;
                }
                JsonObject simplified = new JsonObject();
                simplified.addProperty("origin", departure.get("iataCode").getAsString());
                simplified.addProperty("destination", arrival.get("iataCode").getAsString());
                simplified.addProperty("departure", departure.get("at").getAsString());
                simplified.addProperty("arrival", arrival.get("at").getAsString());
                simplified.addProperty("duration", itinerary.has("duration") ? itinerary.get("duration").getAsString() : "");
                if (offer.has("price") && offer.get("price").isJsonObject()) {
                    JsonObject price = offer.getAsJsonObject("price");
                    if (price.has("total")) simplified.addProperty("price", price.get("total").getAsString());
                    if (price.has("currency")) simplified.addProperty("currency", price.get("currency").getAsString());
                }
                if (firstSegment.has("carrierCode")) {
                    simplified.addProperty("airline", firstSegment.get("carrierCode").getAsString());
                }

                JsonArray legs = new JsonArray();
                JsonArray stopovers = new JsonArray();
                for (int i = 0; i < segments.size(); i++) {
                    JsonObject seg = Optional.ofNullable(segments.get(i))
                            .filter(JsonElement::isJsonObject)
                            .map(JsonElement::getAsJsonObject)
                            .orElse(null);
                    if (seg == null || !seg.has("departure") || !seg.has("arrival")) continue;
                    JsonObject segDep = seg.getAsJsonObject("departure");
                    JsonObject segArr = seg.getAsJsonObject("arrival");
                    if (!segDep.has("iataCode") || !segArr.has("iataCode")) continue;

                    JsonObject leg = new JsonObject();
                    leg.addProperty("origin", segDep.get("iataCode").getAsString());
                    leg.addProperty("destination", segArr.get("iataCode").getAsString());
                    if (segDep.has("at")) leg.addProperty("departure", segDep.get("at").getAsString());
                    if (segArr.has("at")) leg.addProperty("arrival", segArr.get("at").getAsString());
                    if (seg.has("carrierCode")) leg.addProperty("airline", seg.get("carrierCode").getAsString());
                    legs.add(leg);

                    if (i < segments.size() - 1) {
                        stopovers.add(segArr.get("iataCode").getAsString());
                    }
                }
                simplified.add("segments", legs);
                if (stopovers.size() > 0) {
                    simplified.add("stopovers", stopovers);
                }
                simplifiedArray.add(simplified);
            }

            ctx.contentType("application/json");
            ctx.result(new Gson().toJson(simplifiedArray));

        } catch (TimeoutException e) {
            logger.error("Flight search timed out", e);
            ctx.status(504).json(Map.of(
                    "error", "Request timed out"
            ));
        } catch (Exception e) {
            logger.error("Unexpected error in flight search", e);
            ctx.status(500).json(Map.of(
                    "error", "Internal Server Error",
                    "message", e.getMessage()
            ));
        }
    }


    private static void handleNearbySearch(Context ctx) {
        if (!ensureAmadeusConfigured(ctx)) return;
        String latStr = ctx.queryParam("lat");
        String lngStr = ctx.queryParam("lng");
        String checkInDate = ctx.queryParam("checkInDate");
        String checkOutDate = ctx.queryParam("checkOutDate");
        String adultsStr = ctx.queryParam("adults") != null ? ctx.queryParam("adults").trim() : null;
        String roomQuantityStr = ctx.queryParam("roomQuantity") != null ? ctx.queryParam("roomQuantity").trim() : null;
        String radiusStr = ctx.queryParam("radiusKm");

        logger.debug("Nearby search lat={}, lng={}, checkInDate={}, adults={}, roomQuantity={}", latStr, lngStr, checkInDate, adultsStr, roomQuantityStr);

        if (!ValidationUtils.isValidCoordinates(latStr, lngStr)) {
            ctx.status(400).result("Invalid parameters: coordinates are not valid.");
            return;
        }
        if (!ValidationUtils.isFutureDate(checkInDate)) {
            ctx.status(400).result("Invalid parameters: check-in date is not valid.");
            return;
        }
        if (checkOutDate != null && !checkOutDate.isBlank() && !ValidationUtils.isValidDateRange(checkInDate, checkOutDate)) {
            ctx.status(400).result("Invalid parameters: check-out date is not valid.");
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
            double radiusKm = config.searchRadiusKm();
            if (radiusStr != null && !radiusStr.isBlank()) {
                try {
                    radiusKm = Double.parseDouble(radiusStr);
                } catch (NumberFormatException ignored) {
                }
            }

            if (hotelSearchService != null) {
                try {
                    var fastResults = hotelSearchService.searchHotels(
                            new HotelSearchService.HotelQuery(lat, lng, checkInDate, checkOutDate, adults, rooms, radiusKm));
                    if (!fastResults.isEmpty()) {
                        List<Map<String, Object>> hotels = normalizeHotelSummaries(fastResults);
                        Map<String, Object> response = new HashMap<>();
                        response.put("coordinates", Map.of("lat", lat, "lng", lng));
                        response.put("offers", hotels);
                        response.put("meta", Map.of(
                                "count", hotels.size(),
                                "radiusKm", radiusKm
                        ));
                        ctx.json(response);
                        return;
                    }
                    logger.info("Fast hotel search returned no results, falling back to legacy flow");
                } catch (Exception ex) {
                    logger.warn("Fast hotel search failed, falling back to legacy flow: {}", ex.getMessage());
                }
            }

            runLegacyNearbySearch(ctx, lat, lng, checkInDate, checkOutDate, adults, rooms);
        } catch (Exception e) {
            logger.error("Unexpected error while searching nearby hotels", e);
            ctx.status(500).result("Internal Server Error: " + e.getMessage());
        }
    }

    private static List<Map<String, Object>> normalizeHotelSummaries(List<HotelSummary> summaries) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (HotelSummary summary : summaries) {
            Map<String, Object> hotel = new HashMap<>();
            Map<String, Object> hotelInfo = new HashMap<>();
            hotelInfo.put("name", summary.name());
            hotelInfo.put("latitude", summary.lat());
            hotelInfo.put("longitude", summary.lng());
            hotelInfo.put("address", summary.address());
            if (summary.rating() != null && !summary.rating().isBlank()) {
                hotelInfo.put("rating", summary.rating());
            }

            Map<String, Object> price = new HashMap<>();
            price.put("total", summary.priceTotal());
            price.put("currency", summary.currency());

            Map<String, Object> offerContainer = new HashMap<>();
            offerContainer.put("hotel", hotelInfo);
            offerContainer.put("offers", List.of(Map.of("price", price)));
            offerContainer.put("mapsLink", summary.mapsLink());

            hotel.put("hotelId", summary.id());
            hotel.put("offers", List.of(offerContainer));
            hotel.put("map", Map.of("lat", summary.lat(), "lng", summary.lng(), "link", summary.mapsLink()));
            results.add(hotel);
        }
        return results;
    }

    private static void runLegacyNearbySearch(Context ctx, double lat, double lng, String checkInDate, String checkOutDate, int adults, int rooms) {
        try {
            String hotelResponseJson = amadeusService.getHotelsByGeocode(lat, lng, 10);
            JsonArray hotelArray = JsonParser.parseString(hotelResponseJson).getAsJsonArray();

            List<Map<String, Object>> validOffers = new ArrayList<>();

            for (var element : hotelArray) {
                if (!element.isJsonObject()) continue;
                JsonObject hotelObj = element.getAsJsonObject();
                if (hotelObj.has("hotelId")) {
                    String hotelId = hotelObj.get("hotelId").getAsString();
                    try {
                        String offerJson = amadeusService.getHotelOffers(hotelId, adults, checkInDate, rooms, checkOutDate);
                        Map<String, Object> hotelData = new HashMap<>();
                        hotelData.put("hotelId", hotelId);
                        hotelData.put("offers", JsonParser.parseString(offerJson));
                        validOffers.add(hotelData);
                    } catch (ResponseException e) {
                        if ("429".equals(e.getCode())) {
                            logger.warn("Rate limit exceeded while fetching offers for {}: {}", hotelId, e.getMessage());
                            ctx.status(429).result("Rate limit exceeded, retry later");
                            return;
                        }
                        if ("400".equals(e.getCode())) {
                            logger.info("Skipping hotel {} due to upstream bad request: {}", hotelId, e.getMessage());
                            continue;
                        }
                        logger.warn("Failed to fetch offers for {}: {}", hotelId, e.getMessage());
                    }
                }
                if (validOffers.size() >= 5) break;
            }

            if (validOffers.isEmpty()) {
                ctx.status(404).result("No available hotel offers");
                return;
            }

            Map<String, Object> response = new HashMap<>();
            response.put("coordinates", Map.of("lat", lat, "lng", lng));
            response.put("offers", validOffers);

            ctx.contentType("application/json");
            ctx.result(new Gson().toJson(response));
        } catch (ResponseException e) {
            if ("429".equals(e.getCode())) {
                logger.warn("Rate limit exceeded while searching hotels: {}", e.getMessage());
                ctx.status(429).result("Rate limit exceeded, retry later");
                return;
            }
            logger.error("Upstream service error during nearby search: {}", e.getMessage());
            ctx.status(502).result("Upstream service error: " + e.getMessage());
        } catch (TimeoutException e) {
            logger.error("Nearby hotels request timed out", e);
            ctx.status(504).result("Request timed out");
        } catch (Exception e) {
            logger.error("Unexpected error while searching nearby hotels", e);
            ctx.status(500).result("Internal Server Error: " + e.getMessage());
        }
    }

    private static void handleNearbyAirports(Context ctx) {
        if (!ensureAmadeusConfigured(ctx)) return;
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
            ctx.status(400).json(Map.of("error", "Invalid coordinates"));
            return;
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
        } catch (TimeoutException e) {
            ctx.status(504).json(Map.of("error", "Request timed out"));
        } catch (Exception e) {
            ctx.status(500).json(
                    Map.of("error", "Internal Server Error", "message", e.getMessage())
            );
        }
    }
}