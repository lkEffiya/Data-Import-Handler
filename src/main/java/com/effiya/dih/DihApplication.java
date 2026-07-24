package com.effiya.dih;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class DihApplication {

	public static void main(String[] args) {
		SpringApplication.run(DihApplication.class, args);
	}

}
