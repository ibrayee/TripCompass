package org.example;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public class ValidationUtils {
    public static boolean isValidCoordinates(String lat, String lng) {
        try {
            double latitude = Double.parseDouble(lat);
            double longitude = Double.parseDouble(lng);
            return latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isFutureDate(String dateStr) {
        try {
            LocalDate date = LocalDate.parse(dateStr);
            return date.isAfter(LocalDate.now());
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    public static boolean isPositiveInteger(String numberStr) {
        try {
            return Integer.parseInt(numberStr) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
