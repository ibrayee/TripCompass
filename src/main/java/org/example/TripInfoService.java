package org.example;

import com.amadeus.exceptions.ResponseException;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeoutException;

/**
 * Service to orchestrate the trip-info business logic:
 * - find nearest airports
 * - search hotels
 * - search flights (with fallback)
 * - assemble final response map
 */
public class TripInfoService {
    private static final Logger logger = LoggerFactory.getLogger(TripInfoService.class);
    private final AmadeusService amadeusService;

    public TripInfoService(AmadeusService amadeusService) {
        this.amadeusService = amadeusService;
    }

    /**
     Fetches combined trip information using an origin city name.
     */
    public Map<String, Object> getTripInfo(
            double lat,
            double lng,
            String originCity,
            String checkInDate,
            String checkOutDate,
            int adults,
            int rooms
    ) throws ResponseException, TimeoutException {
        double[] originCoords = amadeusService.geocodeCityToCoords(originCity);
        if (originCoords == null) throw new IllegalArgumentException("Could not geolocate origin city");

        String originAirport = amadeusService.getNearestAirport(originCoords[0], originCoords[1]);
        if (originAirport == null) throw new IllegalStateException("No origin airport found");

        return getTripInfoInternal(lat, lng, originAirport, originCoords, checkInDate, checkOutDate, adults, rooms);
    }

    /**
     * Fetches combined trip information when origin coordinates and/or airport are already known.
     */
    public Map<String, Object> getTripInfo(
            double lat,
            double lng,
            String originAirport,
            double originLat,
            double originLng,
            String checkInDate,
            String checkOutDate,
            int adults,
            int rooms
    ) throws ResponseException, TimeoutException {
        double[] originCoords = new double[]{originLat, originLng};
        if (originAirport == null || originAirport.isBlank()) {
            originAirport = amadeusService.getNearestAirport(originLat, originLng);
            if (originAirport == null) throw new IllegalStateException("No origin airport found");
        }
        return getTripInfoInternal(lat, lng, originAirport, originCoords, checkInDate, checkOutDate, adults, rooms);
    }

    private Map<String, Object> getTripInfoInternal(
            double lat,
            double lng,
            String originAirport,
            double[] originCoords,
            String checkInDate,
            String checkOutDate,
            int adults,
            int rooms
    ) throws ResponseException, TimeoutException {
        logger.info("Starting trip-info for destination ({}, {}) from {} at {}", lat, lng, originAirport, checkInDate);
        logger.debug("Looking up nearest airport for destination");
        String destinationAirport = amadeusService.getNearestAirport(lat, lng);
        if (destinationAirport == null) {
            logger.warn("No main destination airport found. Trying nearby airports...");
            List<String> nearby = amadeusService.getNearbyAirports(lat, lng, 200, 3);
            if (!nearby.isEmpty()) {
                destinationAirport = nearby.get(0);
                logger.info("Using fallback destination airport: {}", destinationAirport);
            } else {
                throw new IllegalStateException("No destination airport found within 200km.");
            }
        }

        logger.debug("Origin coords: {}", Arrays.toString(originCoords));


        // 3. Search hotel offers (limit to top 3)
        List<Map<String, Object>> hotelOffers = new ArrayList<>();
        JsonArray hotelArray = JsonParser
                .parseString(amadeusService.getHotelsByGeocode(lat, lng, 10))
                .getAsJsonArray();
        for (JsonElement element : hotelArray) {
            if (!element.isJsonObject()) continue;
            JsonObject hotelObj = element.getAsJsonObject();
            if (!hotelObj.has("hotelId")) continue;
            String hotelId = hotelObj.get("hotelId").getAsString();
            try {
                String offersJson = amadeusService.getHotelOffers(hotelId, adults, checkInDate, rooms, checkOutDate);
                JsonElement parsedOffers = JsonParser.parseString(offersJson);
                if (!parsedOffers.isJsonArray() || parsedOffers.getAsJsonArray().size() == 0) {
                    continue;
                }
                Map<String, Object> hotelData = new HashMap<>();
                hotelData.put("hotelId", hotelId);
                hotelData.put("offers", new Gson().fromJson(parsedOffers, Object.class));
                hotelOffers.add(hotelData);
                if (hotelOffers.size() >= 3) break;
            } catch (ResponseException e) {
                logger.debug("Skipping unavailable hotel {}: {}", hotelId, e.getMessage());
            }
        }

        // 4. Search flight offers (with fallback)
        JsonArray flightArray = JsonParser
                .parseString(amadeusService.getFlightOffers(originAirport, destinationAirport, checkInDate, null, adults))
                .getAsJsonArray();

        if (flightArray.size() == 0) {
            logger.warn("No direct flights found, trying fallback airports...");
            List<String> altOrigins = amadeusService.getNearbyAirports(originCoords[0], originCoords[1], 100, 3);
            List<String> altDests = amadeusService.getNearbyAirports(lat, lng, 100, 3);
            outer:
            for (String altOrig : altOrigins) {
                for (String altDest : altDests) {
                    if (altOrig.equals(originAirport) && altDest.equals(destinationAirport)) continue;
                    try {
                        JsonArray altArray = JsonParser
                                .parseString(amadeusService.getFlightOffers(altOrig, altDest, checkInDate, null, adults))
                                .getAsJsonArray();
                        if (altArray.size() > 0) {
                            flightArray = altArray;
                            originAirport = altOrig;
                            destinationAirport = altDest;
                            logger.info("Fallback flight found: {} -> {}", altOrig, altDest);
                            break outer;
                        }
                    } catch (ResponseException ignored) {}
                }
            }
        }


        // 5. Simplify flight offers (limit to top 3)
        JsonArray simplifiedFlights = new JsonArray();
        Set<String> seenFlights = new HashSet<>();
        for (JsonElement offerElement : flightArray) {
            if (!offerElement.isJsonObject()) continue;
            JsonObject offer = offerElement.getAsJsonObject();
            JsonArray itineraries = offer.has("itineraries") ? offer.getAsJsonArray("itineraries") : null;
            if (itineraries == null || itineraries.size() == 0) continue;
            JsonObject itinerary = itineraries.get(0).getAsJsonObject();
            JsonArray segments = itinerary.getAsJsonArray("segments");
            if (segments == null || segments.size() == 0) continue;
            JsonObject firstSegment = segments.get(0).getAsJsonObject();
            JsonObject lastSegment = segments.get(segments.size() - 1).getAsJsonObject();
            JsonObject dep = firstSegment.getAsJsonObject("departure");
            JsonObject arr = lastSegment.getAsJsonObject("arrival");
            if (dep == null || arr == null || !dep.has("iataCode") || !arr.has("iataCode") || !dep.has("at") || !arr.has("at")) {
                continue;
            }

            String originCode = dep.get("iataCode").getAsString();
            String destCode = arr.get("iataCode").getAsString();
            String depTime = dep.get("at").getAsString();
            String key = originCode + destCode + depTime;
            if (seenFlights.contains(key)) {
                continue;
            }
            seenFlights.add(key);
            JsonObject simple = new JsonObject();
            simple.addProperty("origin", originCode);
            simple.addProperty("destination", destCode);
            simple.addProperty("departure", depTime);
            simple.addProperty("arrival", arr.get("at").getAsString());
            if (itinerary.has("duration")) {
                simple.addProperty("duration", itinerary.get("duration").getAsString());
            }
            if (offer.has("price") && offer.get("price").isJsonObject()) {
                JsonObject price = offer.getAsJsonObject("price");
                if (price.has("total")) simple.addProperty("price", price.get("total").getAsString());
                if (price.has("currency")) simple.addProperty("currency", price.get("currency").getAsString());
            }
            if (firstSegment.has("carrierCode")) {
                simple.addProperty("airline", firstSegment.get("carrierCode").getAsString());
            }

            JsonArray legs = new JsonArray();
            JsonArray stopovers = new JsonArray();
            for (int i = 0; i < segments.size(); i++) {
                JsonObject seg = segments.get(i).getAsJsonObject();
                JsonObject segDep = seg.getAsJsonObject("departure");
                JsonObject segArr = seg.getAsJsonObject("arrival");
                if (segDep == null || segArr == null || !segDep.has("iataCode") || !segArr.has("iataCode")) continue;

                JsonObject leg = new JsonObject();
                leg.addProperty("origin", segDep.get("iataCode").getAsString());
                leg.addProperty("destination", segArr.get("iataCode").getAsString());
                if (segDep.has("at")) leg.addProperty("departure", segDep.get("at").getAsString());
                if (segArr.has("at")) leg.addProperty("arrival", segArr.get("at").getAsString());
                if (seg.has("carrierCode")) leg.addProperty("airline", seg.get("carrierCode").getAsString());
                legs.add(leg);

                if (i < segments.size() - 1 && segArr.has("iataCode")) {
                    stopovers.add(segArr.get("iataCode").getAsString());
                }
            }
            simple.add("segments", legs);
            if (stopovers.size() > 0) {
                simple.add("stopovers", stopovers);
            }

            simplifiedFlights.add(simple);
            if (simplifiedFlights.size() >= 3) break;
        }
        Object simplifiedFlightObject = new Gson().fromJson(simplifiedFlights, Object.class);
        // 6. Assemble response
        Map<String, Object> response = new HashMap<>();
        response.put("coordinates", Map.of("lat", lat, "lng", lng));
        response.put("originAirport", originAirport);
        response.put("destinationAirport", destinationAirport);
        response.put("hotels", hotelOffers);
        response.put("flights", simplifiedFlightObject);
        return response;
    }
}