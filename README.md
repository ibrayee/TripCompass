# TripCompass

TripCompass is a small travel planning app that combines flight, hotel, and map data.
It exposes a REST API with Javalin and serves a simple front-end from `index.html`.

## Tech stack
- Java 17+ with Javalin (REST API)
- Maven (build/run)
- JavaScript, HTML, CSS (frontend)
- Amadeus API (flights/hotels)
- Google Maps API (maps, routes, nearby places)

## How to run
1. Install dependencies:
   ```bash
   npm install
   ```
2. Set your API keys in a `.env` file at the project root:
   ```env
   AMADEUS_API_KEY=your_key
   AMADEUS_API_SECRET=your_secret
   GOOGLE_MAPS_API_KEY=your_key
   ```
3. Start the server:
   ```bash
   mvn clean compile exec:java
   ```

The app starts on `http://localhost:7000`.

## Main endpoints
- `GET /hello` — simple health check.
- `GET /config/maps-key` — returns the Google Maps API key for the frontend.
- `GET /search/locations` — autocomplete for cities/airports (Amadeus).
- `GET /search/flights` — flight offers (Amadeus).
- `GET /search/nearby` — nearby hotels around coordinates (Amadeus).
- `GET /trip-info` — combined flights + hotels for a destination.
- `GET /nearby-airports` — airports close to coordinates.
- `GET /route/info` — driving time/distance between two places (Google Maps).
- `GET /route/polyline` — polyline between two places (Google Maps).
- `GET /places/nearby` — nearby places around coordinates (Google Maps).
- `GET /mashupJavalin/*` — older mashup endpoints for flights/hotels + maps.

Example:
```bash
curl "http://localhost:7000/trip-info?lat=48.8566&lng=2.3522&origin=FCO&checkInDate=2025-12-01&adults=2&roomQuantity=1&maxFlights=5"
```

## Folder structure
- `src/main/java` — Javalin server, services, and validation.
- `src/main/resources/public` — static frontend (`index.html`, CSS, JS).
- `pom.xml` — Maven configuration.
- `package.json` — frontend tooling.

## Notes about Google Maps and Amadeus
- If `AMADEUS_API_KEY` or `AMADEUS_API_SECRET` is missing, Amadeus endpoints return `503`.
- If `GOOGLE_MAPS_API_KEY` is missing, map endpoints and the frontend map will not work.
- Optional envs: `MAX_HOTEL_RESULTS` (default 25) and `HOTEL_RADIUS_KM` (default 15).

## Troubleshooting
- **503 from `/trip-info`, `/search/*`, `/nearby-airports`**: check Amadeus keys in `.env`.
- **Map does not load**: check `GOOGLE_MAPS_API_KEY` and browser console.
- **Upstream errors (502/504)**: the external APIs are rate-limited or slow.
- **Port already in use**: stop the process on port 7000 or change the port in `TripController`.