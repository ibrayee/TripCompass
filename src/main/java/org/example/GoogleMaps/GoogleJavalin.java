package org.example.GoogleMaps;

import io.javalin.Javalin;
import io.javalin.http.Context;
import org.example.AppConfig;
import org.example.ValidationUtils;

import java.util.Map;

public class GoogleJavalin {

    private final AppConfig config;

    public GoogleJavalin(AppConfig config) {
        this.config = config;
    }

    public void registerRoutes(Javalin app) {
        theRouteTimeAndDistance(app);
        polyLine(app);
        thePlaces(app);
    }

    private boolean ensureMapsConfigured(Context ctx) {
        if (config.googleMapsApiKey().isEmpty()) {
            ctx.status(503).json(Map.of(
                    "error", "Google Maps API not configured",
                    "message", "Set GOOGLE_MAPS_API_KEY to enable maps endpoints."
            ));
            return false;
        }
        return true;
    }

    public void theRouteTimeAndDistance(Javalin app) {

        app.get("/route/info", ctx -> {     //visar både tid och distans men bil och inte flyg
            if (!ensureMapsConfigured(ctx)) {
                return;
            }

            String startPlace = ctx.queryParam("startPlace");
            String endPlace = ctx.queryParam("endPlace");

            if (startPlace == null || startPlace.isBlank() || endPlace == null || endPlace.isBlank()) {
                ctx.status(400).result("startplatsen och slutplatsen måste anges");
                return;
            }

            RouteInfo routeInfo = new RouteInfo(startPlace, endPlace, config.googleMapsApiKey().orElse(""));
            routeInfo.fetchRoute();

            String routeInfoRes =routeInfo.getRouteTimeAndDist();

            ctx.result(routeInfoRes);

        });


    }

    public void polyLine(Javalin app) {

        app.get("/route/polyline", ctx -> {
            if (!ensureMapsConfigured(ctx)) {
                return;
            }
            String startPlace = ctx.queryParam("startPlace");
            String endPlace = ctx.queryParam("endPlace");

            if (startPlace == null || startPlace.isBlank() || endPlace == null || endPlace.isBlank()) {
                ctx.status(400).result("startplatsen och slutplatsen måste anges");
                return;
            }

            RouteInfo routeInfo = new RouteInfo(startPlace, endPlace, config.googleMapsApiKey().orElse(""));
            routeInfo.fetchRoute();

            String polyline = routeInfo.getPolyline();

            ctx.result(polyline);

        });
    }


    public void thePlaces (Javalin app) {

        app.get("/places/nearby", ctx -> {
            if (!ensureMapsConfigured(ctx)) {
                return;
            }
            String lat = ctx.queryParam("lat");
            String lng = ctx.queryParam("lng");
            String placeType = ctx.queryParam("type");

            if (!ValidationUtils.isValidCoordinates(lat, lng)) {
                ctx.status(400).result("lat and lng must be provided");
                return;
            }
            if (placeType == null || placeType.isBlank()) {
                ctx.status(400).result("type must be provided");
                return;
            }

            PlacesNearby placesNearby = new PlacesNearby(lat, lng, config.googleMapsApiKey().orElse(""));

            String placesRes = placesNearby.getPlaceNameAndAdress(placeType);

            ctx.result(placesRes);
        });

    }

}