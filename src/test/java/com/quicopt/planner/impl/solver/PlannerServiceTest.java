// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: (c) 2026 Tim Bode, PGI-12, Forschungszentrum Jülich
package com.quicopt.planner.impl.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/**
 * The connection to the planner service: the key it sends and how it reports the service's refusals, against a
 * stand-in service on this machine.
 */
class PlannerServiceTest {

    /** A refusal as the Quicopt service writes it, with a framed message for terminals beside the plain one. */
    private static final String MISSING_KEY = """
            {"error":"planner requests need an API key","reason":"missing_key",\
            "display":"\\n  Quicopt \\u00b7 No API key supplied\\n  \\u2514\\u2500\\u2500 \\"error\\": none"}""";

    @Test
    void readsStringFieldsOfTheServicesErrors() {
        assertEquals("planner requests need an API key", JsonField.string(MISSING_KEY, "error"));
        assertEquals("missing_key", JsonField.string(MISSING_KEY, "reason"));
        assertEquals("\n  Quicopt · No API key supplied\n  └── \"error\": none", JsonField.string(MISSING_KEY,
                "display"));
        assertNull(JsonField.string(MISSING_KEY, "contact"));
        assertNull(JsonField.string("{\"error\": 3}", "error"));
        assertNull(JsonField.string("{\"error\": \"unterminated", "error"));
        assertNull(JsonField.string("not json", "error"));
    }

    @Test
    void sendsTheKeyAndReportsARefusalInTheServicesWords() throws IOException {
        var authorization = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            var body = MISSING_KEY.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(401, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        var baseUrl = System.getProperty("quicopt.baseUrl");
        try {
            System.setProperty("quicopt.baseUrl", "http://127.0.0.1:" + server.getAddress().getPort() + "/");
            System.setProperty("quicopt.apiKey", " the-key ");
            var refusal = assertThrows(IllegalStateException.class,
                    () -> PlannerService.configured().post("/v1/planner/solve", new byte[] { 1 }));
            assertEquals("Bearer the-key", authorization.get());
            assertTrue(refusal.getMessage().contains("answered 401: planner requests need an API key"),
                    refusal::getMessage);
            assertTrue(refusal.getMessage().contains("quicopt.apiKey"), refusal::getMessage);
        } finally {
            System.clearProperty("quicopt.apiKey");
            if (baseUrl == null) {
                System.clearProperty("quicopt.baseUrl");
            } else {
                System.setProperty("quicopt.baseUrl", baseUrl);
            }
            server.stop(0);
        }
    }
}
