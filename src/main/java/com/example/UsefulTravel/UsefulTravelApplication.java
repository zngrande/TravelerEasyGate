package com.example.UsefulTravel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // 開啟排程功能, 給 ExchangeRateUpdateService 每天自動更新匯率用
public class UsefulTravelApplication {

	public static void main(String[] args) {
		SpringApplication.run(UsefulTravelApplication.class, args);
	}

}
