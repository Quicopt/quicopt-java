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

import com.quicopt.planner.wire.Error;
import com.quicopt.planner.wire.ErrorKind;

/**
 * The connection to the planner service: problems go out as planner wire bytes over HTTP, answers come back the
 * same way. The service's address is the system property {@code quicopt.baseUrl}, else the environment variable
 * {@code QUICOPT_BASE_URL}, else a planner service on this machine at {@value #DEFAULT_BASE_URL}.
 */
final class PlannerService {

    /** The address used when none is configured: a planner service on this machine. */
    static final String DEFAULT_BASE_URL = "http://127.0.0.1:8199";

    private static final String CONTENT_TYPE = "application/x-protobuf";

    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    /**
     * @param baseUrl the service's address, without a trailing slash
     */
    private PlannerService(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * @return the connection to the configured service
     */
    static PlannerService configured() {
        var url = System.getProperty("quicopt.baseUrl");
        if (url == null || url.isBlank()) {
            url = System.getenv("QUICOPT_BASE_URL");
        }
        if (url == null || url.isBlank()) {
            url = DEFAULT_BASE_URL;
        }
        return new PlannerService(url.endsWith("/") ? url.substring(0, url.length() - 1) : url);
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
        var request = HttpRequest.newBuilder(URI.create(baseUrl + path)).header("Content-Type", CONTENT_TYPE)
                .header("Accept", CONTENT_TYPE).POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
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
            throw new IllegalStateException("The planner service at " + baseUrl + " answered " + response.statusCode()
                    + ": " + new String(response.body(), StandardCharsets.UTF_8));
        }
        return response.body();
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
