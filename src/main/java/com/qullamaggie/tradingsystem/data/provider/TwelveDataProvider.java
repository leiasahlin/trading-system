package com.qullamaggie.tradingsystem.data.provider;

import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches daily OHLCV data from the Twelve Data API.
 * Implements MarketDataProvider so the data source can be swapped
 * without affecting the rest of the system.
 */
@Component
public class TwelveDataProvider implements MarketDataProvider{
    private final RestClient restClient;
    private final String apiKey;

    public TwelveDataProvider(@Value("${twelvedata.api.key}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = RestClient.create("https://api.twelvedata.com");
    }

    @Override
    public List<DailyPrice> fetchDailyPrices(String symbol, int days) {
        // Build the request and fetch the raw JSON response as a string
        String json = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/time_series")
                        .queryParam("symbol", symbol)
                        .queryParam("interval", "1day")
                        .queryParam("outputsize", days)
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                .body(String.class);

        List<DailyPrice> prices = new ArrayList<>();

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);
            JsonNode values = root.get("values");

            for (JsonNode dayNode : values) {
                DailyPrice price = new DailyPrice();
                price.setDate(LocalDate.parse(dayNode.get("datetime").asText()));
                price.setOpen(new BigDecimal(dayNode.get("open").asText()));
                price.setHigh(new BigDecimal(dayNode.get("high").asText()));
                price.setLow(new BigDecimal(dayNode.get("low").asText()));
                price.setClose(new BigDecimal(dayNode.get("close").asText()));
                price.setVolume(Long.parseLong(dayNode.get("volume").asText()));
                prices.add(price);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Twelve Data Response for" + symbol, e);
        }
        return prices;
    }
}
