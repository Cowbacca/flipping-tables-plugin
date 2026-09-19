package com.dashery.flippingtables;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.inject.Singleton;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Singleton
public class FlippingTablesClient
{
	private static final MediaType JSON = MediaType.parse("application/json");
	private static final long MAX_GAME_VALUE = Integer.MAX_VALUE;
	private static final int MAX_RESPONSE_BYTES = 256 * 1024;
	private static final int MAX_ACTIONS = 32;
	private static final int MAX_LIMITATIONS = 20;
	private static final int MAX_TEXT = 1000;
	private static final int MAX_OFFER_ID = 64;
	private static final int MAX_SEARCH_STATUS = 32;
	private static final int MAX_TIMESTAMP = 64;
	private static final Set<String> ACTION_TYPES = new HashSet<>(Arrays.asList("KEEP", "CANCEL", "REPRICE", "CREATE_BUY", "CREATE_SELL"));

	private final OkHttpClient client;
	private final FlippingTablesConfig config;
	private final Gson gson = new Gson();
	private volatile Call pendingCall;

	@Inject
	public FlippingTablesClient(OkHttpClient client, FlippingTablesConfig config)
	{
		this.client = client.newBuilder()
			.connectTimeout(60, TimeUnit.SECONDS)
			.readTimeout(60, TimeUnit.SECONDS)
			.writeTimeout(60, TimeUnit.SECONDS)
			.callTimeout(60, TimeUnit.SECONDS)
			.followRedirects(false)
			.followSslRedirects(false)
			.build();
		this.config = config;
	}

	public PortfolioModels.AdviceResponse requestPortfolioAdvice(PortfolioModels.AdviceRequest request, String token) throws IOException
	{
		if (request == null)
		{
			throw new IllegalArgumentException("Advice request is required");
		}
		validateToken(token);

		Request httpRequest = new Request.Builder()
			.url(adviceUrl(config == null ? null : config.apiBaseUrl()))
			.header("Authorization", "Bearer " + token)
			.header("Accept", "application/json")
			.post(RequestBody.create(JSON, gson.toJson(request)))
			.build();
		Call call = client.newCall(httpRequest);
		pendingCall = call;
		try (Response response = call.execute())
		{
			if (!response.isSuccessful())
			{
				throw httpFailure(response.code());
			}
			return parse(readBody(response.body()));
		}
		finally
		{
			if (pendingCall == call)
			{
				pendingCall = null;
			}
		}
	}

	public void cancelPendingRequest()
	{
		Call call = pendingCall;
		if (call != null)
		{
			call.cancel();
		}
	}

	private void validateToken(String token)
	{
		if (token == null || token.trim().isEmpty())
		{
			throw new IllegalArgumentException("API token is required");
		}
		for (int index = 0; index < token.length(); index++)
		{
			if (Character.isISOControl(token.charAt(index)))
			{
				throw new IllegalArgumentException("API token contains an invalid control character");
			}
		}
	}

	private HttpUrl adviceUrl(String configured) throws IOException
	{
		if (configured == null || configured.trim().isEmpty())
		{
			throw new IOException("Configured API base URL is missing");
		}
		HttpUrl base = HttpUrl.parse(configured);
		if (base == null || base.query() != null || base.fragment() != null || !base.username().isEmpty() || !base.password().isEmpty())
		{
			throw new IOException("Configured API base URL is invalid");
		}
		boolean loopback = "localhost".equalsIgnoreCase(base.host()) || "127.0.0.1".equals(base.host()) || "::1".equals(base.host());
		if (!"https".equals(base.scheme()) && !(loopback && "http".equals(base.scheme())))
		{
			throw new IOException("Configured API base URL must use HTTPS, except loopback HTTP");
		}
		return base.newBuilder().addPathSegments("portfolio-snapshots/advice").build();
	}

	private PortfolioModels.AdviceResponse parse(String body) throws IOException
	{
		try
		{
			JsonObject root = new JsonParser().parse(body).getAsJsonObject();
			requireLong(root, "snapshotId", 1, Long.MAX_VALUE);
			requireOptionalTimestamp(root, "marketDataThrough");
			JsonObject advice = requireObject(root, "advice");
			requireArray(advice, "actions", MAX_ACTIONS);
			requireArray(advice, "limitations", MAX_LIMITATIONS);
			requireString(advice, "searchStatus", false, MAX_SEARCH_STATUS);
			if (!"EXACT".equals(advice.get("searchStatus").getAsString())
				&& !"SEARCH_LIMIT_REACHED".equals(advice.get("searchStatus").getAsString()))
			{
				throw new IOException("Advice response has an invalid searchStatus");
			}
			requireLong(advice, "projectedCashCommitted", 0, Long.MAX_VALUE);
			requireLong(advice, "inventoryCost", 0, Long.MAX_VALUE);
			requireLong(advice, "conservativeInventoryValue", 0, Long.MAX_VALUE);
			requireLong(advice, "realisedProfit", Long.MIN_VALUE, Long.MAX_VALUE);
			validateActions(advice.getAsJsonArray("actions"));
			validateLimitations(advice.getAsJsonArray("limitations"));

			PortfolioModels.AdviceResponse response = gson.fromJson(root, PortfolioModels.AdviceResponse.class);
			if (response == null || response.getAdvice() == null || response.getAdvice().getActions() == null || response.getAdvice().getLimitations() == null)
			{
				throw new IOException("Advice response has invalid values");
			}
			return response;
		}
		catch (IOException error)
		{
			throw error;
		}
		catch (RuntimeException error)
		{
			throw new IOException("Advice response was malformed", error);
		}
	}

	private void validateActions(Iterable<JsonElement> actions) throws IOException
	{
		for (JsonElement element : actions)
		{
			if (!element.isJsonObject())
			{
				throw new IOException("Advice response contains a malformed action");
			}
			JsonObject action = element.getAsJsonObject();
			String type = requireString(action, "type", false, MAX_SEARCH_STATUS);
			if (!ACTION_TYPES.contains(type))
			{
				throw new IOException("Advice response contains an unknown action type");
			}
			requireLong(action, "itemId", 1, MAX_GAME_VALUE);
			requireLong(action, "quantity", 1, MAX_GAME_VALUE);
			requireLong(action, "pricePerItem", 0, MAX_GAME_VALUE);
			if ("KEEP".equals(type) || "CANCEL".equals(type) || "REPRICE".equals(type))
			{
				requireString(action, "replacesOfferId", false, MAX_OFFER_ID);
			}
			else
			{
				requireOptionalString(action, "replacesOfferId");
			}
		}
	}

	private void validateLimitations(Iterable<JsonElement> limitations) throws IOException
	{
		for (JsonElement limitation : limitations)
		{
			if (!limitation.isJsonPrimitive() || !limitation.getAsJsonPrimitive().isString()
				|| limitation.getAsString().length() > MAX_TEXT)
			{
				throw new IOException("Advice response contains an invalid limitation");
			}
		}
	}

	private JsonObject requireObject(JsonObject object, String field) throws IOException
	{
		if (!object.has(field) || !object.get(field).isJsonObject())
		{
			throw new IOException("Advice response is missing " + field);
		}
		return object.getAsJsonObject(field);
	}

	private void requireArray(JsonObject object, String field) throws IOException
	{
		requireArray(object, field, Integer.MAX_VALUE);
	}

	private void requireArray(JsonObject object, String field, int maximumSize) throws IOException
	{
		if (!object.has(field) || !object.get(field).isJsonArray())
		{
			throw new IOException("Advice response is missing " + field);
		}
		if (object.getAsJsonArray(field).size() > maximumSize)
		{
			throw new IOException("Advice response has too many " + field);
		}
	}

	private String requireString(JsonObject object, String field, boolean allowEmpty) throws IOException
	{
		return requireString(object, field, allowEmpty, MAX_TEXT);
	}

	private String requireString(JsonObject object, String field, boolean allowEmpty, int maximumLength) throws IOException
	{
		if (!object.has(field) || !object.get(field).isJsonPrimitive() || !object.getAsJsonPrimitive(field).isString())
		{
			throw new IOException("Advice response is missing " + field);
		}
		String value = object.get(field).getAsString();
		if (value.length() > maximumLength || (!allowEmpty && value.trim().isEmpty()))
		{
			throw new IOException("Advice response has an invalid " + field);
		}
		return value;
	}

	private void requireOptionalString(JsonObject object, String field) throws IOException
	{
		if (object.has(field) && !object.get(field).isJsonNull())
		{
			requireString(object, field, false, MAX_OFFER_ID);
		}
	}

	private void requireOptionalTimestamp(JsonObject object, String field) throws IOException
	{
		if (!object.has(field) || object.get(field).isJsonNull())
		{
			return;
		}
		String value = requireString(object, field, false, MAX_TIMESTAMP);
		try
		{
			Instant.parse(value);
		}
		catch (DateTimeParseException error)
		{
			throw new IOException("Advice response has an invalid " + field, error);
		}
	}

	private String readBody(ResponseBody responseBody) throws IOException
	{
		if (responseBody == null)
		{
			return "";
		}
		if (responseBody.contentLength() > MAX_RESPONSE_BYTES)
		{
			throw new IOException("Portfolio advice response was too large");
		}
		try (InputStream input = responseBody.byteStream())
		{
			byte[] bytes = input.readNBytes(MAX_RESPONSE_BYTES + 1);
			if (bytes.length > MAX_RESPONSE_BYTES)
			{
				throw new IOException("Portfolio advice response was too large");
			}
			return new String(bytes, StandardCharsets.UTF_8);
		}
	}

	private long requireLong(JsonObject object, String field, long minimum, long maximum) throws IOException
	{
		if (!object.has(field) || !object.get(field).isJsonPrimitive())
		{
			throw new IOException("Advice response is missing " + field);
		}
		JsonPrimitive primitive = object.getAsJsonPrimitive(field);
		if (!primitive.isNumber())
		{
			throw new IOException("Advice response has an invalid " + field);
		}
		try
		{
			String value = primitive.getAsString();
			if (!value.matches("-?(0|[1-9]\\d*)"))
			{
				throw new NumberFormatException("Not an integer");
			}
			long number = Long.parseLong(value);
			if (number < minimum || number > maximum)
			{
				throw new IOException("Advice response has an invalid " + field);
			}
			return number;
		}
		catch (NumberFormatException error)
		{
			throw new IOException("Advice response has an invalid " + field, error);
		}
	}

	private IOException httpFailure(int status)
	{
		if (status == 401 || status == 403)
		{
			return new IOException("Portfolio advice was not authorized (HTTP " + status + ")");
		}
		if (status == 429)
		{
			return new IOException("Portfolio advice was rate limited (HTTP 429)");
		}
		if (status >= 500)
		{
			return new IOException("Portfolio advice server failed (HTTP " + status + ")");
		}
		return new IOException("Portfolio advice request failed (HTTP " + status + ")");
	}
}
