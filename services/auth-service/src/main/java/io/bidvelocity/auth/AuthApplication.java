package io.bidvelocity.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@io.swagger.v3.oas.annotations.OpenAPIDefinition(info = @io.swagger.v3.oas.annotations.info.Info(title = "BidVelocity Auth Service API", description = "Registration, login, JWT + refresh rotation, roles, Google OAuth2", version = "0.1.0"))
@SpringBootApplication
@EnableDiscoveryClient
public class AuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
