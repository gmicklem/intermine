package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2022 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * A local stand-in for the ChEBI REST API (https://www.ebi.ac.uk/chebi/backend/api/public/compound/
 * CHEBI:15377/), for tests: it answers with the responses recorded on 16 November 2026 that are
 * in src/test/resources (chebi-compound-15377.json, water, and chebi-compound-17925.json,
 * alpha-D-glucose; each keeps the top level fields of the answer and two synonyms, which is all
 * the client reads), 404 for every other compound, and with a chosen status when asked to.
 */
class RecordedChebiService
{
    private static final String CONTEXT = "/compound/";
    private final HttpServer server;
    private final List<String> requests = new ArrayList<String>();
    private volatile int forcedStatus = 0;

    RecordedChebiService() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext(CONTEXT, new HttpHandler() {
            public void handle(HttpExchange exchange) throws IOException {
                String path = exchange.getRequestURI().getPath();
                synchronized (requests) {
                    requests.add(path);
                }
                byte[] body;
                int status = 200;
                if (forcedStatus != 0) {
                    status = forcedStatus;
                    body = "{\"detail\": \"forced\"}".getBytes(StandardCharsets.UTF_8);
                } else {
                    String id = path.substring(CONTEXT.length()).replace("/", "")
                            .replace("CHEBI:", "");
                    InputStream in = RecordedChebiService.class.getClassLoader()
                            .getResourceAsStream("chebi-compound-" + id + ".json");
                    if (in == null) {
                        status = 404;
                        body = "{\"detail\":\"No Compound matches the given query.\"}"
                                .getBytes(StandardCharsets.UTF_8);
                    } else {
                        try {
                            body = IOUtils.toByteArray(in);
                        } finally {
                            in.close();
                        }
                    }
                }
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, body.length);
                OutputStream out = exchange.getResponseBody();
                out.write(body);
                out.close();
            }
        });
        server.start();
    }

    /** @return the URL to give to ChebiNameLookup / setChebiUrl */
    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + CONTEXT;
    }

    /** @param status answer every request with this HTTP status (0: answer as recorded) */
    void setForcedStatus(int status) {
        forcedStatus = status;
    }

    /** @return the paths that were asked for, in order */
    List<String> requests() {
        synchronized (requests) {
            return new ArrayList<String>(requests);
        }
    }

    void stop() {
        server.stop(0);
    }
}
