package io.bidvelocity.bidding;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@io.swagger.v3.oas.annotations.OpenAPIDefinition(info = @io.swagger.v3.oas.annotations.info.Info(title = "BidVelocity Bidding Service API", description = "Race-safe bids (FOR UPDATE + CAS), idempotency, anti-sniping, STOMP topics", version = "0.1.0"))
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class BiddingApplication {
    public static void main(String[] args) {
        SpringApplication.run(BiddingApplication.class, args);
    }
}
