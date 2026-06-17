package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.data.service.MarketDataService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;


@SpringBootApplication
public class TradingSystemApplication {
	public static void main(String[] args) {
		SpringApplication.run(TradingSystemApplication.class, args);
	}
}
