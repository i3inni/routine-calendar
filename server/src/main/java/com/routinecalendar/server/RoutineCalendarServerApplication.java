package com.routinecalendar.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RoutineCalendarServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(RoutineCalendarServerApplication.class, args);
	}

}
