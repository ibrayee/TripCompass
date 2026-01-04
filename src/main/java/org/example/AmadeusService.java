package org.example;

import com.amadeus.Amadeus;
import com.amadeus.Params;
import com.amadeus.exceptions.ResponseException;
import com.amadeus.resources.Location;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

public class AmadeusService {
    private static final Logger logger = LoggerFactory.getLogger(AmadeusService.class);
    private final Gson gson = new Gson();

    private final Amadeus amadeus;
    private final ExecutorService executor = Executors.newFixedThreadPool(6);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_RETRIES = 2;

    public AmadeusService(String apiKey, String apiSecret) {
        this.amadeus = Amadeus.builder(apiKey, apiSecret).build();
    }

    private <T> T executeWithRetry(String operation, Callable<T> action) throws ResponseException, TimeoutException {
        int attempt = 0;
        long delayMillis = 300;
        while (true) {
            try {
                // small helper so every call has timeout and retry
                return runWithTimeout(action, REQUEST_TIMEOUT);
            } catch (TimeoutException e) {
                logger.error("{} timed out on attempt {}", operation, attempt + 1);
                if (attempt >= MAX_RETRIES) throw e;
            } catch (ResponseException e) {
                logger.warn("{} failed with status {} attempt {}", operation, e.getCode(), attempt + 1);
                if (attempt >= MAX_RETRIES) throw e;
            } catch (Exception e) {
                logger.error("{} unexpected error on attempt {}", operation, attempt + 1, e);
                if (attempt >= MAX_RETRIES) {
                    if (e instanceof ResponseException re) {
                        throw re;
                    }
                    throw new RuntimeException(e);
                }
            }
            attempt++;
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted", ie);
            }
            delayMillis = Math.min(delayMillis * 2, 2000);
        }
    }

    private <T> T runWithTimeout(Callable<T> task, Duration timeout) throws TimeoutException, ResponseException {
        Future<T> future = executor.submit(task);
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof ResponseException re) {
                throw re;
            }
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted", e);
        }
    }

    public String getNearestAirport(double lat, double lng) throws ResponseException, TimeoutException {
        String latStr = String.format(Locale.US, "%.6f", lat);
        String lngStr = String.format(Locale.US, "%.6f", lng);

        logger.debug("NearestAirport: lat={} lng={}", latStr, lngStr);

        Params params = Params.with("latitude", latStr)
                .and("longitude", lngStr)
                .and("radius", 100)
                .and("page[limit]", 1);

        Location[] locations = executeWithRetry("Nearest airport", () ->
                amadeus.referenceData.locations.airports.get(params));
        if (locations != null && locations.length > 0) {
            return locations[0].getIataCode();
        } else {
            return null;
        }
    }


    public List<String> getNearbyAirports(double lat, double lng, int radiusKm, int limit) throws ResponseException, TimeoutException {
        List<String> airportCodes = new ArrayList<>();
        String latStr = String.format(Locale.US, "%.6f", lat);
        String lngStr = String.format(Locale.US, "%.6f", lng);

        Params params = Params.with("latitude", latStr)
                .and("longitude", lngStr)
                .and("radius", radiusKm)
                .and("page[limit]", limit);

        Location[] results = executeWithRetry("Nearby airports", () ->
                amadeus.referenceData.locations.airports.get(params));
        if (results != null) {
            for (Location loc : results) {
                if (loc.getIataCode() != null) {
                    airportCodes.add(loc.getIataCode());
                }
            }
        }
        return airportCodes;
    }

    public List<Map<String, Object>> getNearbyAirportDetails(double lat, double lng, int radiusKm, int limit) throws ResponseException, TimeoutException {
        List<Map<String, Object>> airports = new ArrayList<>();
        String latStr = String.format(Locale.US, "%.6f", lat);
        String lngStr = String.format(Locale.US, "%.6f", lng);

        Params params = Params.with("latitude", latStr)
                .and("longitude", lngStr)
                .and("radius", radiusKm)
                .and("page[limit]", limit);

        Location[] results = executeWithRetry("Nearby airport details", () ->
                amadeus.referenceData.locations.airports.get(params));
        if (results != null) {
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

    public String getFlightOffers(String origin, String destination, String departureDate, String returnDate, int adults) throws ResponseException, TimeoutException {
        Params params = Params.with("originLocationCode", origin)
                .and("destinationLocationCode", destination)
                .and("departureDate", departureDate)
                .and("adults", adults);
        if (returnDate != null && !returnDate.isEmpty()) {
            params.and("returnDate", returnDate);
        }
        var response = executeWithRetry("Flight offers", () ->
                amadeus.shopping.flightOffersSearch.get(params));
        return gson.toJson(response);
    }

    public String getHotelsByGeocode(double lat, double lng, int radiusKm) throws ResponseException, TimeoutException {
        var response = executeWithRetry("Hotels by geocode", () ->
                amadeus.referenceData.locations.hotels.byGeocode.get(
                        Params.with("latitude", lat)
                                .and("longitude", lng)
                                .and("radius", radiusKm)
                                .and("radiusUnit", "KM")
                ));
        return gson.toJson(response);
    }

    public String getHotelOffers(String hotelIds, int adults, String checkInDate, int roomQuantity, String checkOutDate) throws ResponseException, TimeoutException {
        Params params = Params.with("hotelIds", hotelIds)
                .and("adults", adults)
                .and("checkInDate", checkInDate)
                .and("roomQuantity", roomQuantity);
        if (checkOutDate != null && !checkOutDate.isBlank()) {
            params.and("checkOutDate", checkOutDate);
        }
        var response = executeWithRetry("Hotel offers", () ->
                amadeus.shopping.hotelOffersSearch.get(params));
        return gson.toJson(response);
    }

    public double[] geocodeCityToCoords(String cityName) {
        try {
            Params params = Params.with("keyword", cityName)
                    .and("subType", "CITY,AIRPORT")
                    .and("page[limit]", 1);
            Location[] results = executeWithRetry("Geocoding city", () ->
                    amadeus.referenceData.locations.get(params));

            if (results != null && results.length > 0 && results[0].getGeoCode() != null) {
                Location.GeoCode geo = results[0].getGeoCode();
                logger.info("Geocoded {} to: {} , {}", cityName, geo.getLatitude(), geo.getLongitude());
                return new double[]{geo.getLatitude(), geo.getLongitude()};
            } else {
                logger.warn("No geocode result for: {}", cityName);
            }
        } catch (Exception e) {
            logger.error("Failed to geocode city: {}", cityName, e);
            e.printStackTrace();
        }
        return null;
    }

    public List<Map<String, Object>> searchLocations(String keyword) throws ResponseException, TimeoutException {
        List<Map<String, Object>> suggestions = new ArrayList<>();
        Params params = Params.with("keyword", keyword)
                .and("subType", "AIRPORT,CITY")
                .and("page[limit]", 5);
        Location[] results = executeWithRetry("Search locations", () ->
                amadeus.referenceData.locations.get(params));
        if (results != null) {
            for (Location loc : results) {
                Map<String, Object> info = new HashMap<>();
                if (loc.getName() != null) {
                    info.put("name", loc.getName());
                }
                if (loc.getIataCode() != null) {
                    info.put("iataCode", loc.getIataCode());
                }
                if (loc.getGeoCode() != null) {
                    info.put("lat", loc.getGeoCode().getLatitude());
                    info.put("lng", loc.getGeoCode().getLongitude());
                }
                suggestions.add(info);
            }
        }
        return suggestions;
    }


    public String findNearestAirportCode(double lat, double lng) {
        return findNearestAirportCode(lat, lng, 200);
    }

    public String findNearestAirportCode(double lat, double lng, int initialRadiusKm) {
        try {
            String latStr = String.format(Locale.US, "%.6f", lat);
            String lngStr = String.format(Locale.US, "%.6f", lng);

            int radius = initialRadiusKm;
            final int maxRadius = 1000;
            while (radius <= maxRadius) {
                logger.debug("Find airport with lat={}, lng={}, radius={}", latStr, lngStr, radius);
                Params params = Params.with("latitude", latStr)
                        .and("longitude", lngStr)
                        .and("radius", radius)
                        .and("page[limit]", 1);

                Location[] locations = executeWithRetry("Find nearest airport", () ->
                        amadeus.referenceData.locations.airports.get(params));
                logger.debug("Locations result for radius {}: {}", radius, Arrays.toString(locations));
                if (locations != null && locations.length > 0) {
                    return locations[0].getIataCode();
                }
                radius *= 2;
            }
        } catch (Exception e) {
            logger.error("Failed to get nearest airport", e);
        }
        return null;
    }

}