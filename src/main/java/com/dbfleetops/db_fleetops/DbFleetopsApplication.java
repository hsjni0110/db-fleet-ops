package com.dbfleetops.db_fleetops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DbFleetopsApplication {

	public static void main(String[] args) {
		SpringApplication.run(DbFleetopsApplication.class, args);
	}

}
