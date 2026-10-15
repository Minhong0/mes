package com.minimes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan   // @ConfigurationProperties가 붙은 record(MqttProperties 등)를 찾아 빈으로 등록한다
public class MesServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(MesServerApplication.class, args);
	}

}
