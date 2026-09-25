package de.dahaem.stores;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Fetches public web pages / JSON politely: a normal browser User-Agent, timeouts,
 * a few retries with growing backoff, and a pause after every successful request.
 * A 404 is not retried; it throws FileNotFoundException so callers can skip it.
 */
public class HttpFetcher {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)";
    private static final int ATTEMPTS = 4;

    // An in-memory cookie store so a session (e.g. the aktionspreis PHPSESSID that
    // remembers a PLZ) survives across requests made through this fetcher.
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .cookieHandler(new CookieManager())
            .build();
    private final Duration delay;

    public HttpFetcher(Duration delay) {
        this.delay = delay;
    }

    public String get(String url) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .header("User-Agent", USER_AGENT)
                        .GET()
                        .build();
                HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() == 404) {
                    throw new FileNotFoundException("404 " + url);
                }
                if (response.statusCode() >= 400) {
                    throw new IOException("HTTP " + response.statusCode() + " " + url);
                }
                pause(delay);
                return response.body();
            } catch (FileNotFoundException e) {
                throw e;                              // missing page: retrying won't help
            } catch (IOException e) {
                last = e;
                pause(Duration.ofSeconds(3L * attempt));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while fetching " + url, e);
            }
        }
        throw last;
    }

    /** Form-urlencoded POST, sharing the cookie session with get(). Returns the response body. */
    public String post(String url, Map<String, String> form) throws IOException {
        String body = form.entrySet().stream()
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .collect(Collectors.joining("&"));
        IOException last = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .header("User-Agent", USER_AGENT)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() >= 400) {
                    throw new IOException("HTTP " + response.statusCode() + " " + url);
                }
                pause(delay);
                return response.body();
            } catch (IOException e) {
                last = e;
                pause(Duration.ofSeconds(3L * attempt));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while posting " + url, e);
            }
        }
        throw last;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
