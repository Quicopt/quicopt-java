// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.solver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

import com.quicopt.planner.wire.Error;
import com.quicopt.planner.wire.ErrorKind;
import com.quicopt.planner.wire.SolveStats;

/**
 * The connection to the planner service: problems go out as planner wire bytes over HTTP, answers come back the
 * same way.
 * <p>
 * The service's address is the system property {@code quicopt.baseUrl}, else the environment variable
 * {@code QUICOPT_BASE_URL}, else the Quicopt service at {@value #DEFAULT_BASE_URL}. Requests carry the API key
 * from the system property {@code quicopt.apiKey}, else the environment variable {@code QUICOPT_API_KEY}; the
 * Quicopt service refuses requests without one.
 */
final class PlannerService {

    /** The address used when none is configured: the Quicopt service. */
    static final String DEFAULT_BASE_URL = "https://quicoptapi.pgi.fz-juelich.de";

    private static final System.Logger LOGGER = System.getLogger(PlannerService.class.getName());

    private static final String CONTENT_TYPE = "application/x-protobuf";

    private final String baseUrl;
    private final String apiKey;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    /**
     * @param baseUrl the service's address, without a trailing slash
     * @param apiKey the API key, or null to send none
     */
    private PlannerService(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    /**
     * @return the connection to the configured service, with the configured key
     */
    static PlannerService configured() {
        var url = setting("quicopt.baseUrl", "QUICOPT_BASE_URL");
        if (url == null) {
            url = DEFAULT_BASE_URL;
        }
        return new PlannerService(url.endsWith("/") ? url.substring(0, url.length() - 1) : url,
                setting("quicopt.apiKey", "QUICOPT_API_KEY"));
    }

    /**
     * @return the system property, else the environment variable, else null when neither is set to text
     */
    private static String setting(String property, String variable) {
        var value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(variable);
        }
        return value == null || value.isBlank() ? null : value.strip();
    }

    /**
     * Sends a request and waits for the answer, however long the solve takes.
     *
     * @param path the endpoint, such as {@code /v1/planner/solve}
     * @param body the request's planner wire bytes
     * @return the answer's planner wire bytes
     * @throws UncheckedIOException when the service cannot be reached
     * @throws IllegalStateException when the service answers with an HTTP error
     */
    byte[] post(String path, byte[] body) {
        return send(request(path).header("Content-Type", CONTENT_TYPE)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build());
    }

    /**
     * Asks for something and waits for the answer.
     *
     * @param path the endpoint with its query, such as {@code /v1/planner/jobs/7/events?after=3}
     * @return the answer's planner wire bytes
     * @throws UncheckedIOException when the service cannot be reached
     * @throws IllegalStateException when the service answers with an HTTP error
     */
    byte[] get(String path) {
        return send(request(path).GET().build());
    }

    /**
     * @return a request to an endpoint of the service that accepts planner wire bytes and carries the key, if any
     */
    private HttpRequest.Builder request(String path) {
        var builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).header("Accept", CONTENT_TYPE);
        if (apiKey != null) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        return builder;
    }

    /**
     * @return the answer's body
     * @throws UncheckedIOException when the service cannot be reached
     * @throws IllegalStateException when the service answers with an HTTP error, or the wait is interrupted
     */
    private byte[] send(HttpRequest request) {
        HttpResponse<byte[]> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot reach the planner service at " + baseUrl + ".", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the planner service.", e);
        }
        if (response.statusCode() != 200) {
            throw refusal(response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
        }
        return response.body();
    }

    /**
     * @param status the answer's HTTP status
     * @param body the answer's body; the Quicopt service answers a JSON object with the fields {@code error} (what
     *        went wrong) and {@code reason} (a stable code)
     * @return the exception for an answer other than 200, with the service's own message when it gave one
     */
    private IllegalStateException refusal(int status, String body) {
        var error = JsonField.string(body, "error");
        var reason = JsonField.string(body, "reason");
        var message = new StringBuilder("The planner service at ").append(baseUrl).append(" answered ").append(status);
        message.append(": ").append(error != null ? error : body.strip());
        if ("missing_key".equals(reason) || "invalid_key".equals(reason)) {
            message.append(". Set the system property quicopt.apiKey, or the environment variable QUICOPT_API_KEY, "
                    + "to your API key. Register at https://quicopt.com/signup to receive one by email.");
        }
        return new IllegalStateException(message.toString());
    }

    /**
     * Logs a warning when the service ended a solve sooner than its termination allowed, because the service limits
     * how long one solve may search.
     *
     * @param stats the statistics of a finished solve
     */
    static void warnIfCapped(SolveStats stats) {
        if (stats.getTimeCapSeconds() > 0) {
            LOGGER.log(System.Logger.Level.WARNING, String.format(Locale.ROOT,
                    "The planner service ended the search after %.0f s, the most it allows for one solve; the "
                            + "solver configuration allowed longer.", stats.getTimeCapSeconds()));
        }
    }

    /**
     * @return the exception that stands for an error the service reported, of the kind Java code would expect
     */
    static RuntimeException failure(Error error) {
        var message = error.getMessage();
        return switch (error.getKind()) {
            case UNSUPPORTED -> new UnsupportedOperationException("The planner service cannot solve this model yet: "
                    + message);
            case USER_CODE -> new IllegalStateException("A constraint function failed: " + message);
            case INVALID -> new IllegalStateException("The planner service rejected the problem: " + message);
            default -> new IllegalStateException("The planner service failed (" + kindName(error.getKind()) + "): "
                    + message);
        };
    }

    /**
     * @return a readable name of an error kind
     */
    private static String kindName(ErrorKind kind) {
        return kind.name().toLowerCase(java.util.Locale.ROOT);
    }
}
