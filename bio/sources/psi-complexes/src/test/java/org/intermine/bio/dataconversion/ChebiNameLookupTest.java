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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import junit.framework.TestCase;

/**
 * Tests the ChEBI REST client against a local HTTP server that answers with responses of
 * https://www.ebi.ac.uk/chebi/backend/api/public/compound/CHEBI:15377/ and CHEBI:17925/ as they
 * were recorded on 16 November 2026 (the two files in src/test/resources keep the top level
 * fields of the answer and two of its synonyms, which is all the client reads).
 */
public class ChebiNameLookupTest extends TestCase
{
    private RecordedChebiService service;
    private String baseUrl;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        service = new RecordedChebiService();
        baseUrl = service.url();
    }

    @Override
    protected void tearDown() throws Exception {
        service.stop();
        super.tearDown();
    }

    private int requestCount() {
        return service.requests().size();
    }

    public void testAccessions() {
        assertEquals("CHEBI:15377", ChebiNameLookup.toAccession("CHEBI:15377"));
        assertEquals("CHEBI:15377", ChebiNameLookup.toAccession("chebi:15377"));
        assertEquals("CHEBI:15377", ChebiNameLookup.toAccession(" 15377 "));
        assertNull(ChebiNameLookup.toAccession(null));
        assertNull(ChebiNameLookup.toAccession(""));
        assertNull(ChebiNameLookup.toAccession("CHEBI:"));
        assertNull(ChebiNameLookup.toAccession("EBI-123456"));
        assertNull(ChebiNameLookup.toAccession("../etc/passwd"));
    }

    public void testNameIsTheAsciiName() {
        ChebiNameLookup lookup = new ChebiNameLookup(baseUrl, 2000, 2000);
        assertEquals("water", lookup.getName("CHEBI:15377"));
        // the "name" of this compound is "α-<small>D</small>-glucose"
        assertEquals("alpha-D-glucose", lookup.getName("CHEBI:17925"));
        assertEquals("/compound/CHEBI:15377/", service.requests().get(0));
    }

    public void testEachCompoundIsAskedForOnce() {
        ChebiNameLookup lookup = new ChebiNameLookup(baseUrl, 2000, 2000);
        assertEquals("water", lookup.getName("CHEBI:15377"));
        assertEquals("water", lookup.getName("CHEBI:15377"));
        assertEquals("water", lookup.getName("15377"));
        assertEquals(1, requestCount());
    }

    public void testUnknownCompoundHasNoNameAndIsNotAskedForAgain() {
        ChebiNameLookup lookup = new ChebiNameLookup(baseUrl, 2000, 2000);
        assertNull(lookup.getName("CHEBI:99999999"));
        assertNull(lookup.getName("CHEBI:99999999"));
        assertEquals(1, requestCount());
        // a 404 is an answer, not a failure of the service
        assertFalse(lookup.isDisabled());
        assertEquals("water", lookup.getName("CHEBI:15377"));
    }

    public void testNotAnAccessionIsNotAskedFor() {
        ChebiNameLookup lookup = new ChebiNameLookup(baseUrl, 2000, 2000);
        assertNull(lookup.getName("EBI-123456"));
        assertNull(lookup.getName(null));
        assertEquals(0, requestCount());
    }

    public void testServerErrorGivesNoNameAndThenStopsAsking() {
        service.setForcedStatus(500);
        ChebiNameLookup lookup = new ChebiNameLookup(baseUrl, 2000, 2000);
        for (int i = 0; i < ChebiNameLookup.MAX_CONSECUTIVE_FAILURES; i++) {
            assertFalse(lookup.isDisabled());
            assertNull(lookup.getName("CHEBI:" + (15377 + i)));
        }
        assertTrue(lookup.isDisabled());
        int asked = requestCount();
        assertEquals(ChebiNameLookup.MAX_CONSECUTIVE_FAILURES, asked);
        // the service has come back, but the lookup has given up for this run
        service.setForcedStatus(0);
        assertNull(lookup.getName("CHEBI:17925"));
        assertEquals(asked, requestCount());
    }

    public void testASuccessResetsTheFailureCount() {
        ChebiNameLookup lookup = new ChebiNameLookup(baseUrl, 2000, 2000);
        service.setForcedStatus(503);
        assertNull(lookup.getName("CHEBI:1"));
        assertNull(lookup.getName("CHEBI:2"));
        service.setForcedStatus(0);
        assertEquals("water", lookup.getName("CHEBI:15377"));
        service.setForcedStatus(503);
        assertNull(lookup.getName("CHEBI:3"));
        assertNull(lookup.getName("CHEBI:4"));
        assertFalse(lookup.isDisabled());
    }

    public void testUnreachableServiceGivesNoName() throws Exception {
        // a port that nothing listens on: the server of this test, stopped
        service.stop();
        ChebiNameLookup lookup = new ChebiNameLookup(baseUrl, 500, 500);
        assertNull(lookup.getName("CHEBI:15377"));
        assertNull(lookup.getName("CHEBI:17925"));
        assertNull(lookup.getName("CHEBI:15422"));
        assertTrue(lookup.isDisabled());
    }

    public void testBlankUrlNeverAsks() {
        ChebiNameLookup lookup = new ChebiNameLookup("", 2000, 2000);
        assertTrue(lookup.isDisabled());
        assertNull(lookup.getName("CHEBI:15377"));
        assertEquals(0, requestCount());
    }

    public void testGarbageAnswerGivesNoName() throws Exception {
        // HTTP 200 with something that is not JSON (an HTML maintenance page)
        HttpServer html = HttpServer.create(new InetSocketAddress(
                InetAddress.getByName("127.0.0.1"), 0), 0);
        html.createContext("/compound/", new HttpHandler() {
            public void handle(HttpExchange exchange) throws IOException {
                byte[] body = "<html>maintenance</html>".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            }
        });
        html.start();
        try {
            ChebiNameLookup lookup = new ChebiNameLookup("http://127.0.0.1:"
                    + html.getAddress().getPort() + "/compound/", 2000, 2000);
            assertNull(lookup.getName("CHEBI:15377"));
            assertFalse(lookup.isDisabled());
        } finally {
            html.stop(0);
        }
    }
}
