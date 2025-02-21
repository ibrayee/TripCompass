package org.example;

import io.javalin.Javalin;
import io.javalin.http.Context;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.HttpUrl;
import java.io.IOException;

public class TripCompassServer {

    // Configurazione Amadeus
    private static final String AMADEUS_BASE_URL = "https://test.api.amadeus.com/v2";
    // Inserisci qui il tuo token di accesso (in una versione completa, implementa la logica per ottenerlo e rinnovarlo)
    private static final String AMADEUS_ACCESS_TOKEN = "YOUR_VALID_AMADEUS_ACCESS_TOKEN";

    // Configurazione Google Maps (se necessaria per ulteriori funzionalità come il geocoding)
    private static final String GOOGLE_MAPS_BASE_URL = "https://maps.googleapis.com/maps/api";
    private static final String GOOGLE_MAPS_API_KEY = "YOUR_GOOGLE_MAPS_API_KEY";

    private static final OkHttpClient httpClient = new OkHttpClient();

    public static void main(String[] args) {
        // Avvio del server Javalin su porta 7000
        Javalin app = Javalin.create(config -> {
            // Qui puoi aggiungere configurazioni globali come CORS, error handling, ecc.
        }).start(7000);

        // Endpoint di test
        app.get("/hello", ctx -> ctx.result("Hello TripCompass!"));

        // Endpoint per la ricerca voli
        app.get("/search/flights", TripCompassServer::handleFlightSearch);
    }

    /**
     * Gestisce la ricerca dei voli.
     * Richiede i seguenti parametri in query:
     * - origin: codice IATA del luogo di partenza (obbligatorio)
     * - destination: codice IATA del luogo di arrivo (obbligatorio)
     * - departureDate: data di partenza in formato YYYY-MM-DD (obbligatorio)
     * - returnDate: data di ritorno in formato YYYY-MM-DD (opzionale)
     * - adults: numero di adulti (default: 1)
     */
    private static void handleFlightSearch(Context ctx) {
        String origin = ctx.queryParam("origin");
        String destination = ctx.queryParam("destination");
        String departureDate = ctx.queryParam("departureDate");
        String returnDate = ctx.queryParam("returnDate"); // opzionale
        String adults = ctx.queryParam("adults"); // default 1

        if (origin == null || destination == null || departureDate == null) {
            ctx.status(400).result("Missing required parameters: origin, destination, departureDate");
            return;
        }

        // Costruzione dell'URL per la chiamata GET a Amadeus Flight Offers Search
        HttpUrl.Builder urlBuilder = HttpUrl.parse(AMADEUS_BASE_URL + "/shopping/flight-offers").newBuilder();
        urlBuilder.addQueryParameter("originLocationCode", origin);
        urlBuilder.addQueryParameter("destinationLocationCode", destination);
        urlBuilder.addQueryParameter("departureDate", departureDate);
        urlBuilder.addQueryParameter("adults", adults);
        if (returnDate != null && !returnDate.isEmpty()) {
            urlBuilder.addQueryParameter("returnDate", returnDate);
        }
        // Se necessario, puoi aggiungere altri parametri, ad esempio "nonStop", "currencyCode", "maxPrice", ecc.
        String url = urlBuilder.build().toString();

        // Creazione della richiesta HTTP con OkHttp
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + AMADEUS_ACCESS_TOKEN)
                .header("Accept", "application/vnd.amadeus+json")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                ctx.contentType("application/json");
                ctx.result(responseBody);
            } else {
                ctx.status(response.code()).result("Error from Amadeus API: " + response.code());
            }
        } catch (IOException e) {
            ctx.status(500).result("Internal Server Error: " + e.getMessage());
        }
    }
}
