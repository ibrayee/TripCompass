package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public class ValidationUtils {
    private static final Logger logger = LoggerFactory.getLogger(ValidationUtils.class);
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
        if (dateStr == null) {
            logger.warn("Date string is null");
            return false;
        }
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

    public static boolean isValidDateRange(String checkIn, String checkOut) {
        if (checkIn == null || checkOut == null) {
            return false;
        }
        try {
            LocalDate in = LocalDate.parse(checkIn);
            LocalDate out = LocalDate.parse(checkOut);
            return out.isAfter(in) && in.isAfter(LocalDate.now());
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
