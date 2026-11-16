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
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Looks up the name of a ChEBI compound at the ChEBI REST API
 * (<code>https://www.ebi.ac.uk/chebi/backend/api/public/compound/CHEBI:15377/</code>), which
 * replaced the SOAP web service this converter used to call. The names are kept for the life of
 * the object, so each compound is asked for once, and a service that cannot be reached never stops
 * the conversion: the name is then null (and the failure is logged).
 *
 * Java 8 bytecode, so this uses HttpURLConnection, not java.net.http.
 *
 * @author InterMine
 */
public class ChebiNameLookup
{
    private static final Logger LOG = Logger.getLogger(ChebiNameLookup.class);

    /** The public compound endpoint; the compound's accession and a slash are appended. */
    public static final String DEFAULT_URL =
        "https://www.ebi.ac.uk/chebi/backend/api/public/compound/";
    /** Milliseconds to wait for the connection. */
    public static final int CONNECT_TIMEOUT = 5000;
    /** Milliseconds to wait for the answer. */
    public static final int READ_TIMEOUT = 10000;
    /** After this many failed requests in a row (not found does not count) the lookup stops. */
    static final int MAX_CONSECUTIVE_FAILURES = 3;

    private static final String PREFIX = "CHEBI:";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final int connectTimeout;
    private final int readTimeout;
    // accession to name; a compound with no name is in here with a null value
    private final Map<String, String> names = new HashMap<String, String>();
    private int consecutiveFailures = 0;
    private boolean disabled = false;

    /**
     * A lookup at the public ChEBI REST API.
     */
    public ChebiNameLookup() {
        this(DEFAULT_URL, CONNECT_TIMEOUT, READ_TIMEOUT);
    }

    /**
     * @param baseUrl the URL of the compound endpoint, up to and including the final slash
     * (a blank value makes a lookup that never asks)
     * @param connectTimeout milliseconds to wait for the connection
     * @param readTimeout milliseconds to wait for the answer
     */
    public ChebiNameLookup(String baseUrl, int connectTimeout, int readTimeout) {
        this.baseUrl = StringUtils.isBlank(baseUrl) ? null
                : (baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.disabled = this.baseUrl == null;
    }

    /**
     * The ASCII name of a compound, "water" for CHEBI:15377.
     *
     * @param identifier the ChEBI accession, with or without the "CHEBI:" prefix
     * @return the name, or null if the compound has none, is not known to ChEBI or the service
     * cannot be reached
     */
    public String getName(String identifier) {
        String accession = toAccession(identifier);
        if (accession == null) {
            return null;
        }
        if (names.containsKey(accession)) {
            return names.get(accession);
        }
        if (disabled) {
            return null;
        }
        String name = null;
        try {
            name = fetchName(accession);
            consecutiveFailures = 0;
            names.put(accession, name);
        } catch (IOException e) {
            // not cached: it may work next time, unless the service is plainly down
            consecutiveFailures++;
            LOG.warn("No ChEBI name for " + accession + ": " + e);
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                disabled = true;
                LOG.warn("ChEBI was not reachable " + consecutiveFailures + " times in a row;"
                        + " small molecules get no name from here on");
            }
        }
        return name;
    }

    /**
     * Whether the lookup has given up on the service after repeated failures (or was made without
     * a URL).
     *
     * @return true if getName no longer asks the service
     */
    public boolean isDisabled() {
        return disabled;
    }

    /**
     * "15377", "chebi:15377" and "CHEBI:15377" are all CHEBI:15377; anything else is not an
     * accession.
     *
     * @param identifier an identifier as found in a PSI-MI file
     * @return the accession, or null if it is not one
     */
    static String toAccession(String identifier) {
        if (identifier == null) {
            return null;
        }
        String id = identifier.trim();
        if (id.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            id = id.substring(PREFIX.length());
        }
        if (id.isEmpty()) {
            return null;
        }
        for (int i = 0; i < id.length(); i++) {
            if (!Character.isDigit(id.charAt(i))) {
                return null;
            }
        }
        return PREFIX + id;
    }

    private String fetchName(String accession) throws IOException {
        URL url = new URL(baseUrl + accession + "/");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setConnectTimeout(connectTimeout);
            connection.setReadTimeout(readTimeout);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "InterMine psi-complexes");
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                // an obsolete or unknown accession: no name, and no reason to ask again
                LOG.info("ChEBI does not know " + accession);
                return null;
            }
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + status + " from " + url);
            }
            try (InputStream in = connection.getInputStream()) {
                String body = IOUtils.toString(in, StandardCharsets.UTF_8.name());
                JsonNode json = MAPPER.readTree(body);
                // ascii_name is what the SOAP service's getChebiAsciiName gave; "name" can carry
                // HTML markup (<small>D</small>)
                JsonNode name = json == null ? null : json.get("ascii_name");
                if (name == null || name.isNull() || StringUtils.isBlank(name.asText())) {
                    return null;
                }
                return name.asText();
            }
        } finally {
            connection.disconnect();
        }
    }
}
