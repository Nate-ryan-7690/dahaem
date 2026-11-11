package de.dahaem.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** The client address in the audit log is believed only from the PHP front end, and a log line cannot be forged. */
class AuditLogTest {

    @Test
    void theClientIpHeaderIsBelievedOnlyWithTheInternalToken() {
        assertEquals("203.0.113.7", AuditLog.clientIp("203.0.113.7", "172.18.0.5", true));
        assertEquals("172.18.0.5", AuditLog.clientIp("203.0.113.7", "172.18.0.5", false), "no token: not believed");
        assertEquals("2001:db8::1", AuditLog.clientIp(" 2001:db8::1 ", "172.18.0.5", true));
    }

    @Test
    void anythingThatIsNotAnIpLiteralFallsBackToTheConnectionAddress() {
        for (String bad : new String[]{"evil.example.com", "1.2.3.4\nlogin ok user=x", "1.2.3.4, 5.6.7.8", "", "  ", "999",
                "1.2.3.4 ; DROP", "a".repeat(60), null}) {
            assertEquals("172.18.0.5", AuditLog.clientIp(bad, "172.18.0.5", true), String.valueOf(bad));
        }
        assertEquals("-", AuditLog.clientIp(null, null, false));
    }

    @Test
    void controlCharactersAreTakenOutOfLoggedValuesAndLongOnesAreShortened() {
        assertFalse(AuditLog.clean("/api/x\nlogin ok user=admin", 200).contains("\n"));
        assertEquals("/api/x?login ok", AuditLog.clean("/api/x\nlogin ok", 200));
        assertEquals("abcde...", AuditLog.clean("abcdefghij", 5));
        assertEquals("-", AuditLog.clean(null, 5));
    }
}
