package org.intermine.webservice.server.branding;

/*
 * Copyright (C) 2002-2022 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import junit.framework.TestCase;

/**
 * Tests for the way BrandingService makes image values usable by remote clients.
 */
public class BrandingServiceTest extends TestCase
{
    private static final String BASE = "https://mine.example.org/mymine";

    /** A path relative to the webapp is resolved against the base URL. */
    public void testRelativeImage() {
        assertEquals(BASE + "/images/intermine-logo-45x45.png",
                BrandingService.absoluteImageUrl(BASE, "images/intermine-logo-45x45.png"));
        assertEquals(BASE + "/model/images/logo.png",
                BrandingService.absoluteImageUrl(BASE + "/", "/model/images/logo.png"));
    }

    /** Absolute and protocol-relative values, and empty ones, are left alone. */
    public void testAbsoluteImageUnchanged() {
        assertEquals("https://www.humanmine.org/humanmine/model/images/logo.png",
                BrandingService.absoluteImageUrl(BASE,
                        "https://www.humanmine.org/humanmine/model/images/logo.png"));
        assertEquals("http://x.org/a.png", BrandingService.absoluteImageUrl(BASE, "http://x.org/a.png"));
        assertEquals("//cdn.example.org/a.png",
                BrandingService.absoluteImageUrl(BASE, "//cdn.example.org/a.png"));
        assertEquals("data:image/png;base64,AAAA",
                BrandingService.absoluteImageUrl(BASE, "data:image/png;base64,AAAA"));
        assertEquals("", BrandingService.absoluteImageUrl(BASE, ""));
        assertNull(BrandingService.absoluteImageUrl(BASE, null));
    }
}
