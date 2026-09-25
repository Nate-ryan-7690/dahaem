package de.dahaem.stores;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small null-safe helpers for reading store JSON. Package-private: only the store clients use it. */
final class JsonUtil {

    /** Store dates are German dates, whatever time zone the server runs in. */
    static final ZoneId GERMANY = ZoneId.of("Europe/Berlin");

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)?");

    private JsonUtil() {
    }

    /** Text of a field, or null if missing / empty. */
    static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String s = value.asText().strip();
        return s.isEmpty() ? null : s;
    }

    /** A price-like value: real JSON numbers, or strings such as "3.99", "1.99²" or "1,5". */
    static Double number(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return node.isNumber() ? node.asDouble() : firstNumber(node.asText());
    }

    static Double firstNumber(String s) {
        if (s == null) {
            return null;
        }
        Matcher m = NUMBER.matcher(s);
        return m.find() ? Double.valueOf(m.group().replace(',', '.')) : null;
    }

    /** Date from a "yyyy-MM-dd" text field, falling back to an epoch-seconds field. */
    static LocalDate date(JsonNode node, String isoField, String epochField) {
        String iso = text(node, isoField);
        if (iso != null) {
            return LocalDate.parse(iso);
        }
        JsonNode epoch = node.path(epochField);
        if (!epoch.canConvertToLong() || epoch.asLong() == 0) {
            return null;
        }
        return Instant.ofEpochSecond(epoch.asLong()).atZone(GERMANY).toLocalDate();
    }
}
