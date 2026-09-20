package io.bidvelocity.auction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@io.swagger.v3.oas.annotations.OpenAPIDefinition(info = @io.swagger.v3.oas.annotations.info.Info(title = "BidVelocity Auction Service API", description = "Marketplace search, lifecycle state machine, scheduling, transactional outbox", version = "0.1.0"))
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableScheduling
public class AuctionApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuctionApplication.class, args);
    }
}
