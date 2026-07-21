package com.qullamaggie.tradingsystem.data.provider;

import com.qullamaggie.tradingsystem.data.dto.IntradaySnapshot;
import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches daily OHLCV data from the Twelve Data API.
 * Implements MarketDataProvider so the data source can be swapped
 * without affecting the rest of the system.
 */
@Component
public class TwelveDataProvider implements MarketDataProvider {
    private final RestClient restClient;
    private final String apiKey;
    private final int openingRangeMinutes;

    public TwelveDataProvider(@Value("${twelvedata.api.key}") String apiKey,
                              @Value("${trading.scanner.episodic-pivot.opening-range-minutes}") int openingRangeMinutes) {
        this.apiKey = apiKey;
        this.restClient = RestClient.create("https://api.twelvedata.com");
        this.openingRangeMinutes = openingRangeMinutes;
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

    @Override
    public IntradaySnapshot fetchIntradaySnapshot(String symbol) {
        // Fetch 1-minute bars for the opening window
        String json = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/time_series")
                        .queryParam("symbol", symbol)
                        .queryParam("interval", "1min")
                        .queryParam("outputsize", 30)
                        .queryParam("timezone", "America/New_York")
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                .body(String.class);

        List<JsonNode> openingBars = new ArrayList<>();
        LocalDate today = LocalDate.now();  // OBS: Local time, change later!
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalTime marketOpen = LocalTime.of(9, 30);
        LocalTime windowEnd = marketOpen.plusMinutes(openingRangeMinutes);

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);
            JsonNode values = root.get("values");

            for (JsonNode barNode : values) {
                LocalDateTime barTime = LocalDateTime.parse(barNode.get("datetime").asText(), formatter);

                boolean isToday = barTime.toLocalDate().equals(today);
                boolean inWindow = !barTime.toLocalTime().isBefore(marketOpen)   // >= 09:30
                        && barTime.toLocalTime().isBefore(windowEnd);            // < 09:30 + N

                if (isToday && inWindow) {
                    openingBars.add(barNode);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Twelve Data intraday response for " + symbol, e);
        }

        if (openingBars.isEmpty()) {
            return null;
        }

        JsonNode openBar = openingBars.getLast();
        BigDecimal open = new BigDecimal(openBar.get("open").asText());

        BigDecimal openingRangeHigh = new BigDecimal(openingBars.getFirst().get("high").asText());
        BigDecimal intradayLow = new BigDecimal(openingBars.getFirst().get("low").asText());
        long openingRangeVolume = 0;

        for (JsonNode bar : openingBars) {
            BigDecimal high = new BigDecimal(bar.get("high").asText());
            BigDecimal low = new BigDecimal(bar.get("low").asText());
            openingRangeHigh = openingRangeHigh.max(high);
            intradayLow = intradayLow.min(low);
            openingRangeVolume += Long.parseLong(bar.get("volume").asText());
        }

        return new IntradaySnapshot(symbol, today, open, openingRangeHigh, intradayLow, openingRangeVolume);
    }
}
