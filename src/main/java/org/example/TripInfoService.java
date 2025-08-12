package org.example;

import com.amadeus.exceptions.ResponseException;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.*;

/**
 * Service to orchestrate the trip-info business logic:
 * - find nearest airports
 * - search hotels
 * - search flights (with fallback)
 * - assemble final response map
 */
public class TripInfoService {
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
            int adults,
            int rooms
    ) throws ResponseException {
        double[] originCoords = amadeusService.geocodeCityToCoords(originCity);
        if (originCoords == null) throw new IllegalArgumentException("Could not geolocate origin city");

        String originAirport = amadeusService.getNearestAirport(originCoords[0], originCoords[1]);
        if (originAirport == null) throw new IllegalStateException("No origin airport found");

        return getTripInfoInternal(lat, lng, originAirport, originCoords, checkInDate, adults, rooms);
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
            int adults,
            int rooms
    ) throws ResponseException {
        double[] originCoords = new double[]{originLat, originLng};
        if (originAirport == null || originAirport.isBlank()) {
            originAirport = amadeusService.getNearestAirport(originLat, originLng);
            if (originAirport == null) throw new IllegalStateException("No origin airport found");
        }
        return getTripInfoInternal(lat, lng, originAirport, originCoords, checkInDate, adults, rooms);
    }

    private Map<String, Object> getTripInfoInternal(
            double lat,
            double lng,
            String originAirport,
            double[] originCoords,
            String checkInDate,
            int adults,
            int rooms
    ) throws ResponseException {
        System.out.println("STARTING TRIP INFO...");
        System.out.println("Lat: " + lat + ", Lng: " + lng);
        System.out.println("Origin airport: " + originAirport + ", Check-in: " + checkInDate);
        System.out.println("→ Looking up nearest airport for destination...");
        String destinationAirport = amadeusService.getNearestAirport(lat, lng);
        if (destinationAirport == null) {
            System.out.println("[WARN] No main destination airport found. Trying nearby airports...");
            List<String> nearby = amadeusService.getNearbyAirports(lat, lng, 200, 3);
            if (!nearby.isEmpty()) {
                destinationAirport = nearby.get(0);
                System.out.println("→ Using fallback destination airport: " + destinationAirport);
            } else {
                throw new IllegalStateException("No destination airport found within 200km.");
            }
        }

       System.out.println("→ Origin coords: " + Arrays.toString(originCoords));


        // 3. Search hotel offers (limit to top 3)
        List<Map<String, Object>> hotelOffers = new ArrayList<>();
        JsonArray hotelArray = JsonParser
                .parseString(amadeusService.getHotelsByGeocode(lat, lng, 10))
                .getAsJsonArray();
        for (var element : hotelArray) {
            JsonObject hotelObj = element.getAsJsonObject();
            if (!hotelObj.has("hotelId")) continue;
            String hotelId = hotelObj.get("hotelId").getAsString();
            try {
                String offersJson = amadeusService.getHotelOffers(hotelId, adults, checkInDate, rooms);
                Object offers = new Gson().fromJson(offersJson, Object.class);
                Map<String, Object> hotelData = new HashMap<>();
                hotelData.put("hotelId", hotelId);
                hotelData.put("offers", offers);
                hotelOffers.add(hotelData);
                if (hotelOffers.size() >= 3) break;
            } catch (ResponseException e) {
                // skip unavailable hotels
            }
        }

        // 4. Search flight offers (with fallback)
        JsonArray flightArray = JsonParser
                .parseString(amadeusService.getFlightOffers(originAirport, destinationAirport, checkInDate, null, adults))
                .getAsJsonArray();

        if (flightArray.size() == 0) {
            System.out.println("[WARN] No direct flights found, trying fallback airports...");
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
                            System.out.println("→ Fallback flight found: " + altOrig + " → " + altDest);
                            break outer;
                        }
                    } catch (ResponseException ignored) {}
                }
            }
        }


        // 5. Simplify flight offers (limit to top 3)
        JsonArray simplifiedFlights = new JsonArray();
        for (var offerElement : flightArray) {
            JsonObject offer = offerElement.getAsJsonObject();
            JsonObject itinerary = offer.getAsJsonArray("itineraries").get(0).getAsJsonObject();
            JsonObject segment   = itinerary.getAsJsonArray("segments").get(0).getAsJsonObject();
            JsonObject dep       = segment.getAsJsonObject("departure");
            JsonObject arr       = segment.getAsJsonObject("arrival");

            JsonObject simple = new JsonObject();
            simple.addProperty("origin", dep.get("iataCode").getAsString());
            simple.addProperty("destination", arr.get("iataCode").getAsString());
            simple.addProperty("departure", dep.get("at").getAsString());
            simple.addProperty("arrival", arr.get("at").getAsString());
            simple.addProperty("duration", itinerary.get("duration").getAsString());
            simple.addProperty("price", offer.getAsJsonObject("price").get("total").getAsString());
            simple.addProperty("currency", offer.getAsJsonObject("price").get("currency").getAsString());
            simple.addProperty("airline", segment.get("carrierCode").getAsString());

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
