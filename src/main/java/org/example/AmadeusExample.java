package org.example;
import com.amadeus.Amadeus;
import com.amadeus.Params;

import com.amadeus.exceptions.ResponseException;
import com.amadeus.referencedata.Locations;
import com.amadeus.resources.Location;

public class AmadeusExample {
    public static void main(String[] args) throws ResponseException {
        Amadeus amadeus = Amadeus
                .builder("eTdKQujuFVOs99c7P9994YmIs8DifAYZ", "Id7QaRgawca5XKKM")
                .build();

        Location[] locations = amadeus.referenceData.locations.get(Params
                .with("keyword", "LON")
                .and("subType", Locations.ANY));

        System.out.println(locations);
    }
}