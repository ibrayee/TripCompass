package org.example;

import com.amadeus.resources.Location;
import com.amadeus.Amadeus;
import com.amadeus.Params;
import com.amadeus.exceptions.ResponseException;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AmadeusService {
    private final Gson gson = new Gson();

    private final Amadeus amadeus;

    public AmadeusService(String apiKey, String apiSecret) {
        this.amadeus = Amadeus.builder(apiKey, apiSecret).build();
    }

    public String getNearestAirport(double lat, double lng) throws ResponseException {
        String latStr = String.format(Locale.US, "%.6f", lat);
        String lngStr = String.format(Locale.US, "%.6f", lng);
        System.out.println("Calling Amadeus with lat=" + latStr + ", lng=" + lngStr);


        Params params = Params.with("latitude", latStr)
                .and("longitude", lngStr)
                .and("radius", "200")
                .and("radiusUnit", "KM")
                .and("subType", "AIRPORT")
                .and("page[limit]", "1");

        Location[] locations = amadeus.referenceData.locations.get(params);

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
                .and("radiusUnit", "KM")
                .and("subType", "AIRPORT")
                .and("page[limit]", limit);

        Location[] results = amadeus.referenceData.locations.get(params);
        if (results != null) {
            for (Location loc : results) {
                if (loc.getIataCode() != null) {
                    airportCodes.add(loc.getIataCode());
                }
            }
        }
        return airportCodes;
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
                System.out.println("→ Geocoded " + cityName + " to: " + geo.getLatitude() + ", " + geo.getLongitude());
                return new double[]{geo.getLatitude(), geo.getLongitude()};
            } else {
                System.out.println("[WARN] No geocode result for: " + cityName);
            }
        } catch (Exception e) {
            System.out.println("[ERROR] Failed to geocode city: " + cityName);
            e.printStackTrace();
        }
        return null;
    }

}
