package com.covenant.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@OpenAPIDefinition(
    info = @Info(title = "Covenant API", version = "1.0", description = "Escrow Platform API"),
    security = @SecurityRequirement(name = "bearerAuth") // <--- 1. Apply it globally
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT" // <--- 2. Define the Token Type
)
public class CovenantApplication {

	public static void main(String[] args) {
		SpringApplication.run(CovenantApplication.class, args);
	}

}
