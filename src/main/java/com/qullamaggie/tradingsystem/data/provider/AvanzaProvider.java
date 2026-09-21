package com.qullamaggie.tradingsystem.data.provider;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.qullamaggie.tradingsystem.data.dto.PortfolioHolding;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    private String authenticate(HttpClient client) {
        String credentialsBody = """
            {"maxInactiveMinutes": 1440, "username": "%s", "password": "%s"}"""
                .formatted(config.username(), config.password());
        HttpResponse<String> step1 = sendRaw(client,
                jsonPost("/_api/authentication/sessions/usercredentials", credentialsBody), "login step 1");

        // Kräver kontot ingen andra faktor är inloggningen klar redan här
        if (!readJson(step1).has("twoFactorLogin")) {
            return securityToken(step1);
        }

        String totpBody = """
            {"method": "TOTP", "totpCode": "%s"}""".formatted(generateTotpCode());
        return securityToken(sendRaw(client,
                jsonPost("/_api/authentication/sessions/totp", totpBody), "login step 2"));
    }

    private String securityToken(HttpResponse<String> response) {
        return response.headers().firstValue("X-SecurityToken")
                .orElseThrow(() -> new IllegalStateException("Avanza-inloggning gav ingen X-SecurityToken"));
    }

    private JsonNode readJson(HttpResponse<String> response) {
        try {
            return mapper.readTree(response.body());
        } catch (Exception e) {
            throw new IllegalStateException("Kunde inte tolka Avanza-svar", e);
        }
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
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private JsonNode send(HttpClient client, HttpRequest request, String description) {
        return readJson(sendRaw(client, request, description));
    }

    private HttpResponse<String> sendRaw(HttpClient client, HttpRequest request, String description) {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Avanza svarade " + response.statusCode()
                        + " för " + description + ": " + response.body());
            }
            return response;
        } catch (java.io.IOException | InterruptedException e) {
            throw new IllegalStateException("Avanza-anrop misslyckades: " + description, e);
        }
    }

    private BigDecimal decimal(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : new BigDecimal(node.asText());
    }

    @Override
    public List<PortfolioHolding> fetchHoldings() {
        List<PortfolioHolding> holdings = new ArrayList<>();
        for (JsonNode p : fetchPositions().path("withOrderbook")) {
            JsonNode instrument = p.path("instrument");
            BigDecimal marketValue = decimal(p.path("value").path("value"));
            int shares = p.path("volume").path("value").asInt();

            holdings.add(new PortfolioHolding(
                    instrument.path("isin").asText(null),
                    instrument.path("name").asText(null),
                    shares,
                    decimal(p.path("averageAcquiredPrice").path("value")),
                    // Avanza returnerar ingen kurs per aktie - härleds ur marknadsvärdet
                    shares > 0 && marketValue != null
                            ? marketValue.divide(BigDecimal.valueOf(shares), 4, RoundingMode.HALF_UP)
                            : null,
                    marketValue));
        }
        return holdings;
    }

    @Override
    public BigDecimal fetchAccountValue() {
        // Svaret är redan filtrerat till kontot i URL:en, så allt i det summeras:
        // innehavens marknadsvärde plus likvida medel.
        JsonNode positions = fetchPositions();
        BigDecimal total = BigDecimal.ZERO;

        for (JsonNode p : positions.path("withOrderbook")) {
            BigDecimal value = decimal(p.path("value").path("value"));
            if (value != null) {
                total = total.add(value);
            }
        }
        for (JsonNode cash : positions.path("cashPositions")) {
            BigDecimal balance = decimal(cash.path("totalBalance").path("value"));
            if (balance != null) {
                total = total.add(balance);
            }
        }
        return total;
    }

    private JsonNode fetchPositions() {
        HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        String securityToken = authenticate(client);

        // Kontots urlParameterId ligger i sökvägen - svaret gäller då bara det kontot,
        // så ingen filtrering på account.id behövs i parsningen.
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/_api/position-data/positions/" + config.accountId()))
                .header("X-SecurityToken", securityToken)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .GET()
                .build();
        return send(client, request, "positions");
    }
}