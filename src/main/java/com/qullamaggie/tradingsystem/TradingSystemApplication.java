package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.data.dto.IntradaySnapshot;
import com.qullamaggie.tradingsystem.data.provider.MarketDataProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;


@SpringBootApplication
public class TradingSystemApplication {
	public static void main(String[] args) {
		SpringApplication.run(TradingSystemApplication.class, args);
	}

	@Bean
	public CommandLineRunner testIntradaySnapshot(MarketDataProvider marketDataProvider) {
		return args -> {
			System.out.println("=== Fetching intraday snapshot for AAPL ===");
			IntradaySnapshot snapshot = marketDataProvider.fetchIntradaySnapshot("AAPL");
			if (snapshot == null) {
				System.out.println("No snapshot — opening window not found in data.");
			} else {
				System.out.println("Symbol:              " + snapshot.symbol());
				System.out.println("Date:                " + snapshot.date());
				System.out.println("Open:                " + snapshot.open());
				System.out.println("Opening Range High:  " + snapshot.openingRangeHigh());
				System.out.println("Intraday Low:        " + snapshot.intradayLow());
				System.out.println("Opening Range Vol:   " + snapshot.openingRangeVolume());
			}
			System.out.println("=========================================");
		};
	}
}
