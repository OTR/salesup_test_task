package dev.otr.salesup;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.net.http.HttpClient;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

public class CrptApi {

    private static final String CREATE_ENDPOINT
        = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    public static final String CONTENT_TYPE_VALUE = "application/json";
    public static final String CONTENT_TYPE_KEY = "Content-Type";

    private HttpClient client;
    private PayloadSupplier payloadSupplier;

    private final int requestLimit;
    private final ScheduledExecutorService scheduler;
    private final AtomicInteger counter;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        requireNonNull(timeUnit, "`timeUnit` should be not null");
        if (requestLimit < 0) {
            throw new RuntimeException(
                "`requestLimit` should be a positive int"
            );
        }

        this.requestLimit = requestLimit;
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.counter = new AtomicInteger(0);
        this.scheduler.scheduleAtFixedRate(
            () -> this.counter.set(0), 0, 1, timeUnit
        );
    }

    public void setHttpClient(HttpClient client) {
        this.client = client;
    }

    public void setPayloadSupplier(PayloadSupplier supplier) {
        this.payloadSupplier = supplier;
    }

    public static void main(String[] args) {
        CrptApi api = new CrptApi(TimeUnit.SECONDS, 1);
        api.setHttpClient(HttpClient.newHttpClient());
        api.setPayloadSupplier(new DefaultPayloadSupplier());
        Document document = new Document(
            new Description(""), "", "", "", true, "", "", "", emptyList(), "", ""
        );
        api.createDocument(document, "");
    }

    public void createDocument(Document document, String signature) {
        if (!checkThreshold()) {
            System.out.println("Request limit exceeded. Try again later.");
            return;
        }

        String payload = this.payloadSupplier.createPayload(
            document, signature
        );

        HttpRequest request = getHttpRequest(payload);

        try {
            HttpResponse<String> response = this.client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean checkThreshold() {
        int currentCount = this.counter.incrementAndGet();
        return currentCount <= this.requestLimit;
    }

    private static HttpRequest getHttpRequest(String payload) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(CREATE_ENDPOINT))
            .header(CONTENT_TYPE_KEY, CONTENT_TYPE_VALUE)
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        return request;
    }

    public interface PayloadSupplier {

        String createPayload(Document document, String signature);

    }

    public static class DefaultPayloadSupplier implements PayloadSupplier {

        private final ObjectMapper objectMapper;

        DefaultPayloadSupplier() {
            this.objectMapper = new ObjectMapper();
        }

        @Override
        public String createPayload(Document document, String signature) {
            try {
                // add the signature to the payload
                return this.objectMapper.writeValueAsString(document);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

    }

    public record Description(String participantInn) {}

    public record Product(
        String certificate_document,
        String certificate_documentDate,
        String certificate_documentNumber,
        String owner_inn,
        String producer_inn,
        String production_date,
        String tnved_code,
        String uit_code,
        String uitu_code
    ) {}

    public record Document(
        Description description,
        String doc_id,
        String doc_status,
        String doc_type,
        boolean importRequest,
        String owner_inn,
        String production_date,
        String production_type,
        List<Product> products,
        String reg_date,
        String reg_number
    ){}

}
