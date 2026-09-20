package com.qullamaggie.tradingsystem.data.provider;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.qullamaggie.tradingsystem.data.dto.PortfolioHolding;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class AvanzaProvider implements PortfolioDataProvider {

    private static final String BASE_URL = "https://www.avanza.se";
    private final AvanzaConfig config;
    private final ObjectMapper mapper = new ObjectMapper();

    public AvanzaProvider(AvanzaConfig config) {
        this.config = config;
    }

    @Override
    public List<PortfolioHolding> fetchHoldings() {
        JsonNode positions = fetchPositions();

        // OBS EJ VERIFIERAT: fältnamnen nedan kommer från en tredjeparts-SDK mot samma
        // endpoint (withOrderbook / account.id / volume.value / averageAcquiredPrice.value
        // / value.value / instrument). Verifiera mot ett riktigt svar i Network-fliken
        // och justera vid behov.
        List<PortfolioHolding> holdings = new ArrayList<>();
        for (JsonNode p : positions.path("withOrderbook")) {
            if (!config.accountId().equals(p.path("account").path("id").asText())) {
                continue;
            }
            JsonNode instrument = p.path("instrument");
            holdings.add(new PortfolioHolding(
                    instrument.path("orderbookId").asText(),
                    instrument.path("name").asText(),
                    p.path("volume").path("value").asInt(),
                    decimal(p.path("averageAcquiredPrice").path("value")),
                    decimal(instrument.path("orderbook").path("quote").path("latest").path("value")),
                    decimal(p.path("value").path("value"))
            ));
        }
        return holdings;
    }

    @Override
    public BigDecimal fetchAccountValue() {
        // Designval (säg till om du hellre vill annat): kontovärdet härleds ur SAMMA
        // positions-svar som fetchHoldings - innehavens värde + likvida medel för kontot -
        // istället för att verifiera och underhålla en andra endpoint (categorizedAccounts).
        JsonNode positions = fetchPositions();
        BigDecimal total = BigDecimal.ZERO;

        for (JsonNode p : positions.path("withOrderbook")) {
            if (config.accountId().equals(p.path("account").path("id").asText())) {
                total = total.add(decimal(p.path("value").path("value")));
            }
        }
        for (JsonNode cash : positions.path("cashPositions")) {
            if (config.accountId().equals(cash.path("account").path("id").asText())) {
                total = total.add(decimal(cash.path("totalBalance").path("value"))); // EJ VERIFIERAT fältnamn
            }
        }
        return total;
    }

    private JsonNode fetchPositions() {
        // Ny klient per anrop = ny session per pipeline-körning (beslutat: ingen cachning).
        // CookieManager är Javas motsvarighet till Pythons requests.Session() -
        // cookies från inloggningssteg 1 följer automatiskt med i steg 2 och alla anrop efter.
        HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        String securityToken = authenticate(client);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/_api/position-data/positions"))
                .header("X-SecurityToken", securityToken)
                .GET()
                .build();
        return send(client, request, "positions");
    }

    private String authenticate(HttpClient client) {
        String credentialsBody = """
                {"maxInactiveMinutes": 1440, "username": "%s", "password": "%s"}"""
                .formatted(config.username(), config.password());
        send(client, jsonPost("/_api/authentication/sessions/usercredentials", credentialsBody), "login step 1");

        String totpBody = """
                {"method": "TOTP", "totpCode": "%s"}""".formatted(generateTotpCode());
        HttpResponse<String> response = sendRaw(client, jsonPost("/_api/authentication/sessions/totp", totpBody), "login step 2");

        return response.headers().firstValue("X-SecurityToken")
                .orElseThrow(() -> new IllegalStateException("Avanza-inloggning gav ingen X-SecurityToken"));
    }

    private String generateTotpCode() {
        try {
            // Avanza använder RFC 6238-standard: SHA-1, 6 siffror, 30-sekundersfönster
            return new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6)
                    .generate(config.totpSecret(), Instant.now().getEpochSecond() / 30);
        } catch (Exception e) {
            throw new IllegalStateException("Kunde inte generera TOTP-kod", e);
        }
    }

    private HttpRequest jsonPost(String path, String body) {
        return HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private JsonNode send(HttpClient client, HttpRequest request, String description) {
        try {
            return mapper.readTree(sendRaw(client, request, description).body());
        } catch (Exception e) {
            throw new IllegalStateException("Kunde inte tolka Avanza-svar för " + description, e);
        }
    }

    private HttpResponse<String> sendRaw(HttpClient client, HttpRequest request, String description) {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException(
                        "Avanza svarade " + response.statusCode() + " för " + description);
            }
            return response;
        } catch (java.io.IOException | InterruptedException e) {
            throw new IllegalStateException("Avanza-anrop misslyckades: " + description, e);
        }
    }

    private BigDecimal decimal(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : new BigDecimal(node.asText());
    }
}