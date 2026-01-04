package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class HotelSearchService {
    private static final String AUTH_URL = "https://test.api.amadeus.com/v1/security/oauth2/token";
    private static final String HOTEL_SEARCH_URL = "https://test.api.amadeus.com/v2/shopping/hotel-offers";

    private final AppConfig config;
    private final OkHttpClient client;
    private final ObjectMapper mapper;
    private final Map<String, CacheEntry> hotelCache = new ConcurrentHashMap<>();
    private volatile TokenCache tokenCache;

    public HotelSearchService(AppConfig config) {
        this.config = config;
        this.mapper = new ObjectMapper();
        this.client = new OkHttpClient.Builder()
                .callTimeout(15, TimeUnit.SECONDS)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    public List<HotelSummary> searchHotels(HotelQuery query) throws IOException {
        if (!config.hasAmadeusCredentials()) {
            return Collections.emptyList();
        }

        // reuse cached hotels so we do not hammer the API
        CacheEntry cached = hotelCache.get(query.cacheKey());
        if (cached != null && !cached.isExpired(config.cacheTtl())) {
            return cached.results();
        }

        List<HotelSummary> fetched = fetchHotels(query);
        hotelCache.put(query.cacheKey(), new CacheEntry(fetched, Instant.now()));
        return fetched;
    }

    private List<HotelSummary> fetchHotels(HotelQuery query) throws IOException {
        String accessToken = ensureToken();
        HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(HOTEL_SEARCH_URL)).newBuilder()
                .addQueryParameter("latitude", String.valueOf(query.lat()))
                .addQueryParameter("longitude", String.valueOf(query.lng()))
                .addQueryParameter("radius", String.valueOf(query.radiusKm()))
                .addQueryParameter("radiusUnit", "KM")
                .addQueryParameter("checkInDate", query.checkInDate())
                .addQueryParameter("roomQuantity", String.valueOf(query.roomQuantity()))
                .addQueryParameter("adults", String.valueOf(query.adults()))
                .addQueryParameter("sort", "DISTANCE")
                .addQueryParameter("bestRateOnly", "true")
                .addQueryParameter("view", "FULL")
                .addQueryParameter("page[limit]", String.valueOf(config.maxHotelResults()));
        if (query.checkOutDate() != null && !query.checkOutDate().isBlank()) {
            urlBuilder.addQueryParameter("checkOutDate", query.checkOutDate());
        }

        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .get()
                .header("Authorization", "Bearer " + accessToken)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return Collections.emptyList();
            }

            String body = Objects.requireNonNull(response.body()).string();
            JsonNode root = mapper.readTree(body);
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                return Collections.emptyList();
            }

            List<HotelSummary> summaries = new ArrayList<>();
            for (JsonNode node : data) {
                JsonNode hotelNode = node.path("hotel");
                if (hotelNode.isMissingNode()) {
                    continue;
                }
                double latitude = hotelNode.path("latitude").asDouble();
                double longitude = hotelNode.path("longitude").asDouble();
                String hotelId = hotelNode.path("hotelId").asText("");
                String name = hotelNode.path("name").asText("Unknown Hotel");
                String address = hotelNode.path("address").path("lines").isArray()
                        ? String.join(", ", mapper.convertValue(hotelNode.path("address").path("lines"), List.class))
                        : hotelNode.path("address").path("lines").asText("");
                String cityName = hotelNode.path("address").path("cityName").asText("");
                String rating = hotelNode.path("rating").asText("");

                JsonNode offers = node.path("offers");
                JsonNode firstOffer = offers.isArray() && offers.size() > 0 ? offers.get(0) : null;
                String currency = firstOffer != null ? firstOffer.path("price").path("currency").asText("") : "";
                String priceTotal = firstOffer != null ? firstOffer.path("price").path("total").asText("") : "";

                String formattedAddress = address.isBlank() && cityName.isBlank()
                        ? ""
                        : (address.isBlank() ? cityName : address + (cityName.isBlank() ? "" : ", " + cityName));

                summaries.add(new HotelSummary(
                        hotelId,
                        name,
                        formattedAddress,
                        latitude,
                        longitude,
                        rating,
                        priceTotal,
                        currency,
                        googleMapsLink(latitude, longitude, name)
                ));
            }
            return summaries;
        }
    }

    private synchronized String ensureToken() throws IOException {
        if (tokenCache != null && !tokenCache.isExpired()) {
            return tokenCache.accessToken();
        }

        // quick auth call to Amadeus, nothing fancy
        RequestBody body = new FormBody.Builder()
                .add("grant_type", "client_credentials")
                .add("client_id", config.amadeusApiKey())
                .add("client_secret", config.amadeusApiSecret())
                .build();

        Request request = new Request.Builder()
                .url(AUTH_URL)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unable to authenticate with Amadeus API: " + response.code());
            }

            String json = Objects.requireNonNull(response.body()).string();
            JsonNode node = mapper.readTree(json);
            String token = node.path("access_token").asText("");
            long expiresIn = node.path("expires_in").asLong(0);
            if (token.isBlank() || expiresIn == 0) {
                throw new IOException("Invalid token response from Amadeus API");
            }
            Instant expiry = Instant.now().plusSeconds(Math.max(60, expiresIn - 60));
            tokenCache = new TokenCache(token, expiry);
            return token;
        }
    }

    private String googleMapsLink(double lat, double lng, String name) {
        String query = name == null || name.isBlank()
                ? lat + "," + lng
                : name + " @" + lat + "," + lng;
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse("https://www.google.com/maps/search/"))
                .newBuilder()
                .addQueryParameter("api", "1")
                .addQueryParameter("query", query)
                .build();
        return url.toString();
    }

    public record HotelSummary(
            String id,
            String name,
            String address,
            double lat,
            double lng,
            String rating,
            String priceTotal,
            String currency,
            String mapsLink
    ) {
    }

    public record HotelQuery(double lat, double lng, String checkInDate, String checkOutDate,
                             int adults, int roomQuantity, double radiusKm) {
        public String cacheKey() {
            return lat + ":" + lng + ":" + checkInDate + ":" + (checkOutDate == null ? "" : checkOutDate) + ":" + adults + ":" + roomQuantity + ":" + radiusKm;
        }
    }

    private record CacheEntry(List<HotelSummary> results, Instant storedAt) {
        boolean isExpired(java.time.Duration ttl) {
            return storedAt.plus(ttl).isBefore(Instant.now());
        }
    }

    private record TokenCache(String accessToken, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}