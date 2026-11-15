package org.intermine.web.security;

/*
 * Copyright (C) 2002-2022 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;

import org.junit.Before;
import org.junit.Test;

/**
 * @author Alex Kalderimis
 *
 */
public class KeySignerTest {

	private static final String ALIAS = "foo";
	private PrivateKey signingKey;
	private PublicKey signingPublicKey;
	private PublicKey trustedKey;
	private KeySigner signer;
	private KeyStore store;

	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
	    keyGen.initialize(512); // short keys means faster tests.
	    
	    // For the purposes of these tests it is very important that the
	    // signing key and the signed key are not related, since that is how
	    // it would be used in the wild.
	    KeyPair keyPair = keyGen.generateKeyPair();
	    this.signingKey = keyPair.getPrivate();
	    this.signingPublicKey = keyPair.getPublic();
	    
	    KeyPair keyPair2 = keyGen.generateKeyPair();
	    this.trustedKey = keyPair2.getPublic();
	    String dn = "CN=Test, L=London, C=GB";
	    
	    this.signer = new KeySigner(signingKey, dn, 1, KeySigner.DEFAULT_ALGORITHM);
	    this.store = KeyStore.getInstance(KeyStore.getDefaultType());
	    this.store.load(null, null);
	}

	@Test
	public void testPutYourCertificateInPullYourCertificateOut() throws Exception {
		X509Certificate cert = signer.generateCertificate("trusted", trustedKey);
		store.setCertificateEntry(ALIAS, cert);
		Certificate retrieved = store.getCertificate(ALIAS);
		PublicKey fromStore = retrieved.getPublicKey();
		assertEquals(fromStore, trustedKey);
	}

	@Test
	public void testCertificateIsSignedByTheSigningKey() throws Exception {
		X509Certificate cert = signer.generateCertificate("trusted", trustedKey);
		cert.verify(signingPublicKey); // throws if the signature does not match
		assertEquals("SHA256withRSA", cert.getSigAlgName());
		assertEquals(3, cert.getVersion());
	}

	@Test
	public void testCertificateNamesAndValidity() throws Exception {
		X509Certificate cert = signer.generateCertificate("trusted", trustedKey);
		assertEquals("CN=trusted", cert.getSubjectX500Principal().getName());
		assertEquals(new javax.security.auth.x500.X500Principal("CN=Test, L=London, C=GB"),
				cert.getIssuerX500Principal());
		cert.checkValidity();
		Date oneDayOn = new Date(System.currentTimeMillis() + 23L * 3600L * 1000L);
		cert.checkValidity(oneDayOn);
		try {
			cert.checkValidity(new Date(System.currentTimeMillis() + 49L * 3600L * 1000L));
			fail("the certificate should have expired");
		} catch (java.security.cert.CertificateExpiredException e) {
			assertTrue(true);
		}
	}

	@Test
	public void testOtherAlgorithms() throws Exception {
		// SHA-384 and SHA-512 need more than the 512 bit keys of the other tests
		KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
		keyGen.initialize(2048);
		KeyPair bigPair = keyGen.generateKeyPair();
		for (String algorithm : new String[] {"SHA1withRSA", "SHA384withRSA", "SHA512withRSA"}) {
			KeySigner other = new KeySigner(bigPair.getPrivate(), "CN=Test", 1, algorithm);
			X509Certificate cert = other.generateCertificate("trusted", trustedKey);
			cert.verify(bigPair.getPublic());
			assertEquals(algorithm, cert.getSigAlgName());
		}
	}

	@Test(expected = KeySigner.SigningException.class)
	public void testUnknownAlgorithm() throws Exception {
		new KeySigner(signingKey, "CN=Test", 1, "MD2withFoo").generateCertificate("trusted", trustedKey);
	}

}
