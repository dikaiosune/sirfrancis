package io.sirfrancis.bacon.auth;

import io.dropwizard.auth.basic.BasicCredentials;
import io.sirfrancis.bacon.core.User;
import io.sirfrancis.bacon.db.UserDAO;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by adam on 4/30/15.
 */
public class HTTPAuthenticatorTest {
	Logger log = LoggerFactory.getLogger(HTTPAuthenticatorTest.class);
	SecureRandom random;

	@Before
	public void setUp() throws Exception {
		random = new SecureRandom();
	}

	@After
	public void tearDown() throws Exception {
		random = null;
	}

	@Test
	public void testHashEquals() throws Exception {
		HTTPAuthenticator auth = new HTTPAuthenticator(mock(UserDAO.class));
		byte[] equalFirst = new byte[8];
		byte[] equalSecond = new byte[8];

		byte[] unequalFirst = new byte[16];
		byte[] unequalSecond = new byte[16];

		random.nextBytes(unequalFirst);
		random.nextBytes(unequalSecond);
		random.nextBytes(equalFirst);

		//copy bytes to have an identical hash to check against
		for (int i = 0; i < equalFirst.length; i++) {
			equalSecond[i] = equalFirst[i];
		}

		//null array references should always be false
		assertFalse(HTTPAuthenticator.hashEquals(null, null));
		assertFalse(HTTPAuthenticator.hashEquals(null, equalFirst));
		assertFalse(HTTPAuthenticator.hashEquals(equalSecond, null));

		//different lengths should always be false
		assertFalse(HTTPAuthenticator.hashEquals(equalFirst, unequalSecond));

		//different contents should always be false
		assertFalse(HTTPAuthenticator.hashEquals(unequalFirst, unequalSecond));

		assertTrue(HTTPAuthenticator.hashEquals(equalFirst, equalSecond));
	}

	@Test
	public void testAuthenticate() throws Exception {
		UserDAO mockDAO = mock(UserDAO.class);
		HTTPAuthenticator auth = new HTTPAuthenticator(mockDAO);

		byte[] salt = new byte[16];
		random.nextBytes(salt);

		//test a successful authentication
		BasicCredentials credsOne = new BasicCredentials("testUserOne", "testPassOne");
		User one = new User(credsOne.getUsername(), salt, new SaltedHasher(credsOne.getPassword(), salt).getHash());

		when(mockDAO.getUser(credsOne.getUsername())).thenReturn(one);

		log.debug("This authentication should be successful:");
		assertTrue(one.equals(auth.authenticate(credsOne).get()));

		//test a failed authentication for a user that exists
		BasicCredentials credsTwo = new BasicCredentials("testUserTwo", "testPassTwo");
		User two = new User(credsTwo.getUsername(), salt, new SaltedHasher("differentPassword", salt).getHash());
		when(mockDAO.getUser(credsTwo.getUsername())).thenReturn(two);

		log.debug("This authentication should be unsuccesful (wrong password):");
		assertFalse(auth.authenticate(credsTwo).isPresent());

		//test a failed authentication for a user that doesn't exist
		BasicCredentials credsThree = new BasicCredentials("nonExistantUser", "doesn'tMatter");
		when(mockDAO.getUser(credsThree.getUsername())).thenReturn(null);

		log.debug("This authentication should be unsuccessul (no such user):");
		assertFalse(auth.authenticate(credsThree).isPresent());

	}
}
