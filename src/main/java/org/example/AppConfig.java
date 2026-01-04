package org.example;

import io.github.cdimascio.dotenv.Dotenv;

import java.time.Duration;
import java.util.Optional;

public class AppConfig {
    private final String amadeusApiKey;
    private final String amadeusApiSecret;
    private final String googleMapsApiKey;
    private final Duration cacheTtl;
    private final int maxHotelResults;
    private final double searchRadiusKm;

    public AppConfig(Dotenv dotenv) {
        this.amadeusApiKey = dotenv.get("AMADEUS_API_KEY", "");
        this.amadeusApiSecret = dotenv.get("AMADEUS_API_SECRET", "");
        this.googleMapsApiKey = dotenv.get("GOOGLE_MAPS_API_KEY", "");
        this.cacheTtl = Duration.ofMinutes(10);
        this.maxHotelResults = Integer.parseInt(dotenv.get("MAX_HOTEL_RESULTS", "25"));
        this.searchRadiusKm = Double.parseDouble(dotenv.get("HOTEL_RADIUS_KM", "15"));
    }

    public Optional<String> googleMapsApiKey() {
        return googleMapsApiKey == null || googleMapsApiKey.isBlank()
                ? Optional.empty()
                : Optional.of(googleMapsApiKey);
    }

    public boolean hasAmadeusCredentials() {
        return amadeusApiKey != null && !amadeusApiKey.isBlank()
                && amadeusApiSecret != null && !amadeusApiSecret.isBlank();
    }

    public String amadeusApiKey() {
        return amadeusApiKey;
    }

    public String amadeusApiSecret() {
        return amadeusApiSecret;
    }

    public Duration cacheTtl() {
        return cacheTtl;
    }

    public int maxHotelResults() {
        return maxHotelResults;
    }

    public double searchRadiusKm() {
        return searchRadiusKm;
    }
}