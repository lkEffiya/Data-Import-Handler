package com.effiya.dih;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootApplication
public class DihApplication {

	public static void main(String[] args) {
		SpringApplication.run(DihApplication.class, args);
	}

	@Bean
	public ObjectMapper objectMapper() {
		return new ObjectMapper();
	}
}
