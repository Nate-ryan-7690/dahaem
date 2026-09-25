package de.dahaem.stores;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JsonUtilTest {

    private final ObjectMapper mapper = new ObjectMapper();

    // ---- text ----

    @Test
    void textReturnsFieldValue() {
        ObjectNode node = mapper.createObjectNode().put("name", "  Butter  ");
        assertEquals("Butter", JsonUtil.text(node, "name"));
    }

    @Test
    void textReturnsNullForMissingField() {
        ObjectNode node = mapper.createObjectNode();
        assertNull(JsonUtil.text(node, "missing"));
    }

    @Test
    void textReturnsNullForJsonNullField() {
        ObjectNode node = mapper.createObjectNode().putNull("name");
        assertNull(JsonUtil.text(node, "name"));
    }

    @Test
    void textReturnsNullForBlankField() {
        ObjectNode node = mapper.createObjectNode().put("name", "   ");
        assertNull(JsonUtil.text(node, "name"));
    }

    // ---- number ----

    @Test
    void numberFromRealJsonNumber() {
        ObjectNode node = mapper.createObjectNode().put("price", 3.99);
        assertEquals(3.99, JsonUtil.number(node.get("price")), 1e-9);
    }

    @Test
    void numberFromStringWithDecimalComma() {
        ObjectNode node = mapper.createObjectNode().put("price", "1,5");
        assertEquals(1.5, JsonUtil.number(node.get("price")), 1e-9);
    }

    @Test
    void numberFromStringWithTrailingFootnoteMarker() {
        ObjectNode node = mapper.createObjectNode().put("price", "1.99²");
        assertEquals(1.99, JsonUtil.number(node.get("price")), 1e-9);
    }

    @Test
    void numberIsNullForMissingNode() {
        ObjectNode node = mapper.createObjectNode();
        assertNull(JsonUtil.number(node.get("missing")));
    }

    @Test
    void numberIsNullForNullNode() {
        ObjectNode node = mapper.createObjectNode().putNull("price");
        assertNull(JsonUtil.number(node.get("price")));
    }

    // ---- firstNumber ----

    @Test
    void firstNumberParsesDecimalComma() {
        assertEquals(3.99, JsonUtil.firstNumber("3,99"));
    }

    @Test
    void firstNumberIgnoresNonNumericSuffix() {
        assertEquals(1.99, JsonUtil.firstNumber("1.99²"));
    }

    @Test
    void firstNumberReturnsNullWhenNoDigits() {
        assertNull(JsonUtil.firstNumber("kein Preis"));
    }

    @Test
    void firstNumberReturnsNullForNullInput() {
        assertNull(JsonUtil.firstNumber(null));
    }

    // ---- date ----

    @Test
    void dateFromIsoField() {
        ObjectNode node = mapper.createObjectNode().put("validFrom", "2024-05-01");
        assertEquals(LocalDate.of(2024, 5, 1), JsonUtil.date(node, "validFrom", "validFromEpoch"));
    }

    @Test
    void dateFallsBackToEpochField() {
        ObjectNode node = mapper.createObjectNode().put("validFromEpoch", 1714521600L); // 2024-05-01T00:00:00Z
        LocalDate expected = java.time.Instant.ofEpochSecond(1714521600L).atZone(JsonUtil.GERMANY).toLocalDate();
        assertEquals(expected, JsonUtil.date(node, "validFrom", "validFromEpoch"));
    }

    @Test
    void dateIsNullWhenNeitherFieldPresent() {
        ObjectNode node = mapper.createObjectNode();
        assertNull(JsonUtil.date(node, "validFrom", "validFromEpoch"));
    }

    @Test
    void dateIsNullWhenEpochIsZero() {
        ObjectNode node = mapper.createObjectNode().put("validFromEpoch", 0L);
        assertNull(JsonUtil.date(node, "validFrom", "validFromEpoch"));
    }
}
