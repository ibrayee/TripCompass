package org.example.GoogleMaps;

import io.javalin.Javalin;

public class GoogleJavalin {


    public void theRouteTimeAndDistance (Javalin javalin) {

      javalin.get("/route/info", ctx -> {     //visar både tid och distans men bil och inte flyg

          String startPlace = ctx.queryParam("startPlace");
          String endPlace = ctx.queryParam("endPlace");

          RouteInfo routeInfo = new RouteInfo(startPlace, endPlace);
          routeInfo.fetchRoute();

          String routeInfoRes =routeInfo.getRouteTimeAndDist();

          ctx.result(routeInfoRes);

        });


    }

    public void polyLine (Javalin javalin) {

        javalin.get("/route/polyline", ctx -> {
            String startPlace = ctx.queryParam("startPlace");
            String endPlace = ctx.queryParam("endPlace");

            if (startPlace == null || startPlace.isBlank() || endPlace == null || endPlace.isBlank()) {
                ctx.status(400).result("startplatsen och slutplatsen måste anges");
                return;
            }

            RouteInfo routeInfo = new RouteInfo(startPlace, endPlace);
            routeInfo.fetchRoute();

            String polyline = routeInfo.getPolyline();

            ctx.result(polyline);

        });
    }


    public void thePlaces (Javalin javalin) {

        javalin.get("/places/nearby", ctx -> {
            String lat = ctx.queryParam("lat");
            String lng = ctx.queryParam("lng");
            String placeType = ctx.queryParam("type");


        PlacesNearby placesNearby = new PlacesNearby(lat, lng);

        String placesRes = placesNearby.getPlaceNameAndAdress(placeType, placeType);

        ctx.result(placesRes);
    });

    }

}
