package com.dashery.flippingtables;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import okhttp3.OkHttpClient;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FlippingTablesClientTest
{
	@Test
	public void sendsTypedAdviceWithBearerTokenAndIsoStrings() throws Exception
	{
		AtomicReference<String> authorization = new AtomicReference<>();
		AtomicReference<String> body = new AtomicReference<>();
		HttpServer server = server(new FixedResponseHandler(200, validResponse(null), authorization, body));

		try
		{
			PortfolioModels.AdviceResponse response = client(server, "/api/").requestPortfolioAdvice(request(), "token-value");

			assertEquals(7L, response.getSnapshotId());
			assertEquals("Bearer token-value", authorization.get());
			assertTrue(body.get().contains("\"nextVisitInterval\":\"PT5M\""));
			assertTrue(body.get().contains("\"capturedAt\":\"2026-01-01T00:00:00Z\""));
			assertNull(response.getMarketDataThrough());
		}
		finally
		{
			server.stop(0);
		}
	}

	@Test
	public void surfacesAuthorizationRateLimitAndServerErrors() throws Exception
	{
		assertFailure(401, "not authorized");
		assertFailure(403, "not authorized");
		assertFailure(429, "rate limited");
		assertFailure(500, "server failed");
	}

	@Test
	public void rejectsRedirectInsteadOfFollowingIt() throws Exception
	{
		AtomicInteger requests = new AtomicInteger();
		HttpServer server = server(exchange ->
		{
			requests.incrementAndGet();
			exchange.getResponseHeaders().add("Location", "/api/portfolio-snapshots/advice");
			exchange.sendResponseHeaders(302, -1);
			exchange.close();
		});

		try
		{
			assertTrue(expectIo(() -> client(server, "/api").requestPortfolioAdvice(request(), "token")).contains("HTTP 302"));
			assertEquals(1, requests.get());
		}
		finally
		{
			server.stop(0);
		}
	}

	@Test
	public void rejectsMalformedAndUnsafeAdviceResponses() throws Exception
	{
		assertInvalidResponse("{}");
		assertInvalidResponse(validResponse(null).replace("\"snapshotId\":7", "\"snapshotId\":0"));
		assertInvalidResponse(validResponse("{\"type\":\"UNKNOWN\",\"itemId\":1,\"quantity\":1,\"pricePerItem\":1}"));
		assertInvalidResponse(validResponse("{\"type\":\"CREATE_BUY\",\"itemId\":-1,\"quantity\":1,\"pricePerItem\":1}"));
		assertInvalidResponse(validResponse("{\"type\":\"CREATE_BUY\",\"itemId\":1,\"quantity\":1.5,\"pricePerItem\":1}"));
		assertInvalidResponse(validResponse("{\"type\":\"CANCEL\",\"itemId\":1,\"quantity\":1,\"pricePerItem\":1}"));
		assertInvalidResponse(validResponse("{\"type\":\"CREATE_SELL\",\"itemId\":2147483648,\"quantity\":1,\"pricePerItem\":1}"));
		assertInvalidResponse(validResponse(null).replace("\"searchStatus\":\"EXACT\"", "\"searchStatus\":\"OTHER\""));
		assertInvalidResponse(validResponse(null).replace("\"limitations\":[]", "\"limitations\":[1]"));
		assertInvalidResponse(validResponse(null).replace("\"marketDataThrough\":null", "\"marketDataThrough\":\"not-a-timestamp\""));
		assertInvalidResponse(validResponse(null).replace("\"actions\":[]", "\"actions\":" + actions(33)));
		assertInvalidResponse(validResponse(null).replace("\"limitations\":[]", "\"limitations\":" + limitations(21)));
	}

	@Test
	public void rejectsOversizedResponsesBeforeParsing() throws Exception
	{
		String response = validResponse(null) + repeated(' ', 256 * 1024);
		HttpServer server = server(new FixedResponseHandler(200, response, new AtomicReference<>(), new AtomicReference<>()));
		try
		{
			assertTrue(expectIo(() -> client(server, "/api").requestPortfolioAdvice(request(), "token")).contains("too large"));
		}
		finally
		{
			server.stop(0);
		}
	}

	@Test
	public void acceptsAnIsoMarketDataTimestamp() throws Exception
	{
		String response = validResponse(null).replace("\"marketDataThrough\":null", "\"marketDataThrough\":\"2026-01-01T00:00:00Z\"");
		HttpServer server = server(new FixedResponseHandler(200, response, new AtomicReference<>(), new AtomicReference<>()));
		try
		{
			assertEquals("2026-01-01T00:00:00Z", client(server, "/api").requestPortfolioAdvice(request(), "token").getMarketDataThrough());
		}
		finally
		{
			server.stop(0);
		}
	}

	@Test
	public void rejectsMissingAndUnsafeBaseUrlsBeforeNetworkCalls()
	{
		FlippingTablesConfig missingBaseUrl = new FlippingTablesConfig()
		{
			@Override
			public String apiBaseUrl()
			{
				return null;
			}
		};
		FlippingTablesConfig insecureBaseUrl = new FlippingTablesConfig()
		{
			@Override
			public String apiBaseUrl()
			{
				return "http://example.com/api";
			}
		};

		assertTrue(expectIo(() -> new FlippingTablesClient(new OkHttpClient(), missingBaseUrl).requestPortfolioAdvice(request(), "token")).contains("missing"));
		assertTrue(expectIo(() -> new FlippingTablesClient(new OkHttpClient(), insecureBaseUrl).requestPortfolioAdvice(request(), "token")).contains("must use HTTPS"));
	}

	@Test
	public void rejectsControlCharactersInTokensBeforeSending() throws Exception
	{
		HttpServer server = server(new FixedResponseHandler(200, validResponse(null), new AtomicReference<>(), new AtomicReference<>()));
		try
		{
			try
			{
				client(server, "/api").requestPortfolioAdvice(request(), "token\nvalue");
				fail("Expected token validation failure");
			}
			catch (IllegalArgumentException expected)
			{
				assertTrue(expected.getMessage().contains("control"));
			}
		}
		finally
		{
			server.stop(0);
		}
	}

	private void assertFailure(int status, String expectedMessage) throws Exception
	{
		HttpServer server = server(new FixedResponseHandler(status, "ignored", new AtomicReference<>(), new AtomicReference<>()));
		try
		{
			assertTrue(expectIo(() -> client(server, "/api").requestPortfolioAdvice(request(), "token")).contains(expectedMessage));
		}
		finally
		{
			server.stop(0);
		}
	}

	private void assertInvalidResponse(String response) throws Exception
	{
		HttpServer server = server(new FixedResponseHandler(200, response, new AtomicReference<>(), new AtomicReference<>()));
		try
		{
			assertFalse(expectIo(() -> client(server, "/api").requestPortfolioAdvice(request(), "token")).isEmpty());
		}
		finally
		{
			server.stop(0);
		}
	}

	private HttpServer server(HttpHandler handler) throws IOException
	{
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/api/portfolio-snapshots/advice", handler);
		server.start();
		return server;
	}

	private FlippingTablesClient client(HttpServer server, String basePath)
	{
		return new FlippingTablesClient(new OkHttpClient(), new FlippingTablesConfig()
		{
			@Override
			public String apiBaseUrl()
			{
				return "http://127.0.0.1:" + server.getAddress().getPort() + basePath;
			}
		});
	}

	private PortfolioModels.AdviceRequest request()
	{
		PortfolioModels.Snapshot snapshot = new PortfolioModels.Snapshot(
			100L, Collections.emptyList(), Collections.emptyList(), 1,
			Collections.emptyMap(), "2026-01-01T00:00:00Z");
		return new PortfolioModels.AdviceRequest(snapshot, "PT5M", 10L, true);
	}

	private String expectIo(ThrowingCall call)
	{
		try
		{
			call.call();
			fail("Expected IOException");
			return "";
		}
		catch (IOException expected)
		{
			return expected.getMessage();
		}
	}

	private String validResponse(String action)
	{
		String actions = action == null ? "[]" : "[" + action + "]";
		return "{\"snapshotId\":7,\"advice\":{\"actions\":" + actions
			+ ",\"projectedCashCommitted\":0,\"realisedProfit\":0,\"inventoryCost\":0"
			+ ",\"conservativeInventoryValue\":0,\"limitations\":[],\"searchStatus\":\"EXACT\"}"
			+ ",\"marketDataThrough\":null}";
	}

	private String repeated(char value, int count)
	{
		StringBuilder result = new StringBuilder(count);
		for (int index = 0; index < count; index++)
		{
			result.append(value);
		}
		return result.toString();
	}

	private String actions(int count)
	{
		StringBuilder result = new StringBuilder("[");
		for (int index = 0; index < count; index++)
		{
			if (index > 0)
			{
				result.append(',');
			}
			result.append("{\"type\":\"CREATE_BUY\",\"itemId\":1,\"quantity\":1,\"pricePerItem\":1}");
		}
		return result.append(']').toString();
	}

	private String limitations(int count)
	{
		StringBuilder result = new StringBuilder("[");
		for (int index = 0; index < count; index++)
		{
			if (index > 0)
			{
				result.append(',');
			}
			result.append("\"limitation\"");
		}
		return result.append(']').toString();
	}

	private interface ThrowingCall
	{
		void call() throws IOException;
	}

	private static final class FixedResponseHandler implements HttpHandler
	{
		private final int status;
		private final String response;
		private final AtomicReference<String> authorization;
		private final AtomicReference<String> body;

		private FixedResponseHandler(int status, String response, AtomicReference<String> authorization, AtomicReference<String> body)
		{
			this.status = status;
			this.response = response;
			this.authorization = authorization;
			this.body = body;
		}

		@Override
		public void handle(HttpExchange exchange) throws IOException
		{
			authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
			body.set(read(exchange.getRequestBody()));
			byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(status, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		}

		private String read(InputStream input) throws IOException
		{
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			byte[] buffer = new byte[256];
			int bytesRead;
			while ((bytesRead = input.read(buffer)) != -1)
			{
				output.write(buffer, 0, bytesRead);
			}
			return new String(output.toByteArray(), StandardCharsets.UTF_8);
		}
	}
}
