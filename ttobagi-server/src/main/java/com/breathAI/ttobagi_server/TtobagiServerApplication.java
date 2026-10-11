package com.breathAI.ttobagi_server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.util.TimeZone;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TtobagiServerApplication {

	public static void main(String[] args) {
		// 서버 위치와 무관하게 시각을 UTC로 다룬다
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(TtobagiServerApplication.class, args);
	}

}