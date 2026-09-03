package com.example.travelereasygate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // 開啟排程功能, 給 ExchangeRateUpdateService 每天自動更新匯率用
public class TravelerEasyGateApplication {

	public static void main(String[] args) {
		SpringApplication.run(TravelerEasyGateApplication.class, args);
	}

}
