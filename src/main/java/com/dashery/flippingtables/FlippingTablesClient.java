package com.dashery.flippingtables;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Singleton;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.inject.Inject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Singleton
@Slf4j
public class FlippingTablesClient {
    private static final String BASE_URL = "https://flipping-tables-prod.herokuapp.com";
    private final OkHttpClient okHttpClient;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();


    @Inject
    public FlippingTablesClient(OkHttpClient okHttpClient) {
        this.okHttpClient = okHttpClient.newBuilder()
                .connectTimeout(10, TimeUnit.MINUTES)
                .readTimeout(10, TimeUnit.MINUTES)
                .writeTimeout(10, TimeUnit.MINUTES)
                .build();
    }

    @SneakyThrows
    public OfferAdvice requestOfferAdvice(OfferAdviceRequest offerAdviceRequest) {
        String jobId = createOfferAdviceJob(offerAdviceRequest);
        return getOfferAdviceJobResult(jobId);
    }

    private String createOfferAdviceJob(OfferAdviceRequest offerAdviceRequest) throws IOException {
        String json = gson.toJson(offerAdviceRequest);
        log.info("Requesting offer advice with json: {}", json);

        RequestBody body = RequestBody.create(
                MediaType.parse("application/json"), json);

        Request request = new Request.Builder()
                .url(BASE_URL + "/offer-advice-jobs")
                .post(body)
                .build();

        Call call = okHttpClient.newCall(request);
        Response response = call.execute();
        String bodyAsString = response.body().string();
        response.body().close();
        return gson.fromJson(bodyAsString, OfferAdviceJob.class).getId();
    }

    @SneakyThrows
    private OfferAdvice getOfferAdviceJobResult(String jobId) {
        Request request = new Request.Builder()
                .url(BASE_URL + "/offer-advice-jobs/" + jobId)
                .get()
                .build();

        Call call = okHttpClient.newCall(request);
        Response response = call.execute();
        String bodyAsString = response.body().string();
        response.body().close();
        OfferAdvice offerAdvice = gson.fromJson(bodyAsString, OfferAdviceJob.class).getOfferAdvice();

        if (offerAdvice != null) {
            return offerAdvice;
        } else {
            Thread.sleep(1000);
            return getOfferAdviceJobResult(jobId);
        }
    }

    @SneakyThrows
    public SellAdvice getSellAdvice(SellAdviceRequest sellAdviceRequest) {
        String json = gson.toJson(sellAdviceRequest);
        log.info("Requesting sell advice with json: {}", json);

        RequestBody body = RequestBody.create(
                MediaType.parse("application/json"), json);

        Request request = new Request.Builder()
                .url(BASE_URL + "/sell-advices")
                .post(body)
                .build();

        Call call = okHttpClient.newCall(request);
        Response response = call.execute();
        String bodyAsString = response.body().string();
        response.body().close();
        return gson.fromJson(bodyAsString, SellAdvice.class);
    }
}

