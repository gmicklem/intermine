package org.intermine.web.fair;

/*
 * Copyright (C) 2002-2022 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import junit.framework.TestCase;
import org.intermine.web.registry.model.Instance;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests the lookup of the mine in the InterMine registry against a local HTTP server.
 */
public class SemanticMarkupFormatterTest extends TestCase
{
    private HttpServer server;
    private String registryURL;
    private int namespaceStatus = 200;
    private int instanceStatus = 200;
    private final List<String> requests = new ArrayList<String>();

    @Override
    protected void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/service/namespace", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                requests.add(exchange.getRequestURI().toString());
                reply(exchange, namespaceStatus, "{\"namespace\":\"testmine\","
                        + "\"statusCode\":200}");
            }
        });
        server.createContext("/service/instances/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                requests.add(exchange.getRequestURI().toString());
                // non-ASCII name and unknown properties, which the registry may add at any time
                reply(exchange, instanceStatus, "{\"instance\":{\"name\":\"TestMine\","
                        + "\"namespace\":\"testmine\",\"maintainerOrgName\":\"Caf\u00e9 Lab\","
                        + "\"maintainerEmail\":\"help@example.org\",\"organisms\":[\"human\"],"
                        + "\"somethingNew\":{\"a\":1}},\"statusCode\":200,"
                        + "\"executionTime\":\"1 ms\"}");
            }
        });
        server.start();
        registryURL = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    @Override
    protected void tearDown() {
        server.stop(0);
    }

    private static void reply(HttpExchange exchange, int status, String body)
        throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        OutputStream out = exchange.getResponseBody();
        out.write(bytes);
        out.close();
    }

    public void testLookUpInstance() throws IOException {
        Instance instance = SemanticMarkupFormatter.lookUpInstance(registryURL,
                "http://mine.example.org/testmine");
        assertNotNull(instance);
        assertEquals("testmine", instance.getNamespace());
        assertEquals("Caf\u00e9 Lab", instance.getMaintainerOrgName());
        assertEquals("help@example.org", instance.getMaintainerEmail());
        assertEquals(2, requests.size());
        assertEquals("/service/namespace?url=http://mine.example.org/testmine", requests.get(0));
        assertEquals("/service/instances/testmine", requests.get(1));
    }

    public void testNamespaceNotFound() throws IOException {
        namespaceStatus = 404;
        assertNull(SemanticMarkupFormatter.lookUpInstance(registryURL, "http://x.example.org/"));
        assertEquals(1, requests.size());
    }

    public void testInstanceNotFound() throws IOException {
        instanceStatus = 500;
        assertNull(SemanticMarkupFormatter.lookUpInstance(registryURL, "http://x.example.org/"));
        assertEquals(2, requests.size());
    }

    public void testRegistryNotReachable() {
        server.stop(0);
        try {
            SemanticMarkupFormatter.lookUpInstance(registryURL, "http://x.example.org/");
            fail("expected an IOException");
        } catch (IOException e) {
            // expected: getMineInstance logs it and returns null
        }
    }
}
