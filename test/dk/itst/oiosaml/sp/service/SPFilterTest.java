package dk.itst.oiosaml.sp.service;

import static dk.itst.oiosaml.sp.service.TestHelper.getCredential;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.jmock.Expectations;
import org.junit.Before;
import org.junit.Test;

import dk.itst.oiosaml.configuration.BRSConfiguration;
import dk.itst.oiosaml.sp.OIOPrincipal;
import dk.itst.oiosaml.sp.UserAssertionHolder;
import dk.itst.oiosaml.sp.UserAssertionImpl;
import dk.itst.oiosaml.sp.model.OIOAssertion;
import dk.itst.oiosaml.sp.service.util.Constants;
import dk.itst.oiosaml.sp.util.BRSUtil;

public class SPFilterTest extends AbstractServiceTests {

	private final class BaseMatcherExtension extends
			BaseMatcher<ServletRequest> {
		public boolean matches(Object item) {
			HttpServletRequest req = (HttpServletRequest)item;
			String remoteUser = (req).getRemoteUser();
			if (!remoteUser.equals("joetest")) {
				return false;
			}
			assertNotNull(req.getUserPrincipal());
			assertTrue(req.getUserPrincipal() instanceof OIOPrincipal);
			OIOPrincipal p = (OIOPrincipal) req.getUserPrincipal();
			assertEquals("joetest", p.getName());
			assertNotNull(p.getAssertion());
			
			return true;
		}

		public void describeTo(Description description) {
		}
	}

	private FilterChain chain;
	private SPFilter filter;
	private Map<String, String> conf = new HashMap<String, String>();

	@Before
	public void setUp() throws NoSuchAlgorithmException, NoSuchProviderException {
		credential = getCredential();
		chain = context.mock(FilterChain.class);
		context.checking(new Expectations() {{
			allowing(req).getRequestURI(); will(returnValue("http://test"));
			allowing(req).getPathInfo(); will(returnValue("/test"));
			allowing(req).getRequestURL(); will(returnValue(new StringBuffer("http://test")));
			allowing(req).getQueryString();
		}});
		
		filter = new SPFilter();
		conf.put(Constants.PROP_ASSURANCE_LEVEL, "1");
		filter.setConfiguration(TestHelper.buildConfiguration(conf));
		filter.setFilterInitialized(true);
	}
	
	@Test
	public void failOnNotConfigured() throws ServletException, IOException {
		BRSConfiguration.setSystemConfiguration(null);
		final File dir = new File(File.createTempFile("test", "test").getAbsolutePath() + "dir");
		dir.mkdir();
		
		SPFilter filter = new SPFilter();
		final FilterConfig config = context.mock(FilterConfig.class);
		final ServletContext servletContext = context.mock(ServletContext.class);
		context.checking(new Expectations(){{
			one(config).getServletContext(); will(returnValue(servletContext));
			one(servletContext).getInitParameter(Constants.INIT_OIOSAML_HOME); will(returnValue(dir.getAbsolutePath()));
			one(session).getMaxInactiveInterval(); will(returnValue(10*60*1000));
		}});
		System.clearProperty(BRSUtil.OIOSAML_HOME);
		filter.init(config);
		
		final RequestDispatcher dispatcher = context.mock(RequestDispatcher.class);
		
		context.checking(new Expectations(){{
			one(req).getRequestDispatcher("/saml/configure"); will(returnValue(dispatcher));
			one(dispatcher).forward(req, res);
		}});
			
		filter.doFilter(req, res, chain);
		
		dir.delete();
	}

	@Test
	public void redirectWhenNotLoggedIn() throws Exception {
		final RequestDispatcher dispatcher = context.mock(RequestDispatcher.class);
		UserAssertionHolder.set(new UserAssertionImpl(new OIOAssertion(assertion)));		
		context.assertIsSatisfied();
		context.checking(new Expectations() {{
			one(session).setAttribute(with(equal(Constants.SESSION_REQUESTURI)), with(any(String.class)));
			one(session).setAttribute(with(equal(Constants.SESSION_QUERYSTRING)), with(any(String.class)));
			one(session).removeAttribute(Constants.SESSION_USER_ASSERTION);
			one(req).getRequestDispatcher("/saml/login"); will(returnValue(dispatcher));
			one(dispatcher).forward(req, res);
		}});
		
		filter.doFilter(req, res, chain);
		assertNull(UserAssertionHolder.get());
	}
	
	@Test
	public void doFilterWhenAuthenticated() throws Exception {
		UserAssertionHolder.set(null);
		
		setHandler();
		final BaseMatcher<ServletRequest> baseMatcher = new BaseMatcherExtension();
		context.checking(new Expectations() {{
			one(session).getAttribute(Constants.SESSION_USER_ASSERTION); will(returnValue(new UserAssertionImpl(new OIOAssertion(assertion))));
			one(chain).doFilter(with(baseMatcher) , with(any(HttpServletResponse.class)));
		}});
		filter.doFilter(req, res, chain);
		
		assertNotNull(UserAssertionHolder.get());
	}
	
	@Test
	public void failWhenAssuranceLevelIsTooLow() throws Exception {
		conf.put(Constants.PROP_ASSURANCE_LEVEL, "4");
		setHandler();
		context.checking(new Expectations() {{
			one(session).removeAttribute(Constants.SESSION_USER_ASSERTION);
		}});
		try {
			filter.doFilter(req, res, chain);
			fail("assurance level should be too low");
		} catch (RuntimeException e) {}
	}
}
