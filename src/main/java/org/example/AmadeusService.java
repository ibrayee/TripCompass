package org.example;

import com.amadeus.resources.Location;
import com.amadeus.Amadeus;
import com.amadeus.Params;
import com.amadeus.exceptions.ResponseException;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

public class AmadeusService {
    private static final Logger logger = LoggerFactory.getLogger(AmadeusService.class);
    private final Gson gson = new Gson();

    private final Amadeus amadeus;

    public AmadeusService(String apiKey, String apiSecret) {
        this.amadeus = Amadeus.builder(apiKey, apiSecret).build();
    }

    public String getNearestAirport(double lat, double lng) throws ResponseException {
        String latStr = String.format(Locale.US, "%.6f", lat);
        String lngStr = String.format(Locale.US, "%.6f", lng);

        logger.debug("NearestAirport: lat={} lng={}", latStr, lngStr);

        Params params = Params.with("latitude", latStr)
                .and("longitude", lngStr)
                .and("radius", 100)
                .and("page[limit]", 1);

        Location[] locations = amadeus.referenceData.locations.airports.get(params);
        if (locations != null && locations.length > 0) {
            return locations[0].getIataCode();
        } else {
            return null;
        }
    }



    public List<String> getNearbyAirports(double lat, double lng, int radiusKm, int limit) throws ResponseException {
        List<String> airportCodes = new ArrayList<>();
        String latStr = String.format(Locale.US, "%.6f", lat);
        String lngStr = String.format(Locale.US, "%.6f", lng);

        Params params = Params.with("latitude", latStr)
                .and("longitude", lngStr)
                .and("radius", radiusKm)
                .and("page[limit]", limit);

        Location[] results = amadeus.referenceData.locations.airports.get(params);        if (results != null) {
            for (Location loc : results) {
                if (loc.getIataCode() != null) {
                    airportCodes.add(loc.getIataCode());
                }
            }
        }
        return airportCodes;
    }

    public List<Map<String, Object>> getNearbyAirportDetails(double lat, double lng, int radiusKm, int limit) throws ResponseException {
        List<Map<String, Object>> airports = new ArrayList<>();
        String latStr = String.format(Locale.US, "%.6f", lat);
        String lngStr = String.format(Locale.US, "%.6f", lng);

        Params params = Params.with("latitude", latStr)
                .and("longitude", lngStr)
                .and("radius", radiusKm)
                .and("page[limit]", limit);

        Location[] results = amadeus.referenceData.locations.airports.get(params);        if (results != null) {
            for (Location loc : results) {
                if (loc.getIataCode() != null && loc.getGeoCode() != null) {
                    Map<String, Object> info = new HashMap<>();
                    info.put("iata", loc.getIataCode());
                    info.put("lat", loc.getGeoCode().getLatitude());
                    info.put("lng", loc.getGeoCode().getLongitude());
                    if (loc.getName() != null) {
                        info.put("name", loc.getName());
                    }
                    airports.add(info);
                }
            }
        }
        return airports;
    }

    public String getFlightOffers(String origin, String destination, String departureDate, String returnDate, int adults) throws ResponseException {
        Params params = Params.with("originLocationCode", origin)
                .and("destinationLocationCode", destination)
                .and("departureDate", departureDate)
                .and("adults", adults);
        if (returnDate != null && !returnDate.isEmpty()) {
            params.and("returnDate", returnDate);
        }
        var response = amadeus.shopping.flightOffersSearch.get(params);
        return gson.toJson(response);
    }

    public String getHotelsByGeocode(double lat, double lng, int radiusKm) throws ResponseException {
        var response = amadeus.referenceData.locations.hotels.byGeocode.get(
                Params.with("latitude", lat)
                        .and("longitude", lng)
                        .and("radius", radiusKm)
                        .and("radiusUnit", "KM")
        );
        return gson.toJson(response);
    }

    public String getHotelOffers(String hotelIds, int adults, String checkInDate, int roomQuantity) throws ResponseException {
        var response = amadeus.shopping.hotelOffersSearch.get(
                Params.with("hotelIds", hotelIds)
                        .and("adults", adults)
                        .and("checkInDate", checkInDate)
                        .and("roomQuantity", roomQuantity)
        );
        return gson.toJson(response);
    }

    public double[] geocodeCityToCoords(String cityName) {
        try {
            Params params = Params.with("keyword", cityName)
                    .and("subType", "CITY,AIRPORT")
                    .and("page[limit]", 1);
            Location[] results = amadeus.referenceData.locations.get(params);

            if (results != null && results.length > 0 && results[0].getGeoCode() != null) {
                Location.GeoCode geo = results[0].getGeoCode();
                logger.info("Geocoded {} to: {} , {}", cityName, geo.getLatitude(), geo.getLongitude());                return new double[]{geo.getLatitude(), geo.getLongitude()};
            } else {
                logger.warn("No geocode result for: {}", cityName);            }
        } catch (Exception e) {
            logger.error("Failed to geocode city: {}", cityName, e);            e.printStackTrace();
        }
        return null;
    }

    public String findNearestAirportCode(double lat, double lng) {
        try {
            String latStr = String.format(Locale.US, "%.6f", lat);
            String lngStr = String.format(Locale.US, "%.6f", lng);

            logger.debug("Find airport with lat={}, lng={}", latStr, lngStr);
            Params params = Params.with("latitude", latStr)
                    .and("longitude", lngStr)
                    .and("radius", 200)
                    .and("page[limit]", 1);

            Location[] locations = amadeus.referenceData.locations.airports.get(params);
            logger.debug("Locations result: {}", Arrays.toString(locations));        if (locations != null && locations.length > 0) {
                return locations[0].getIataCode();
            }
            throw new IllegalArgumentException("No nearby airport found for your location");
        } catch (Exception e) {
            logger.error("Failed to get nearest airport", e);
            throw new RuntimeException("Failed to get nearest airport: " + e.getMessage());
        }

    }



}

