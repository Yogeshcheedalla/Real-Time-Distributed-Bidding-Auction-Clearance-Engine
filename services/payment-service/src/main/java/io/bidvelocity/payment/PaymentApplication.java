package io.bidvelocity.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@io.swagger.v3.oas.annotations.OpenAPIDefinition(info = @io.swagger.v3.oas.annotations.info.Info(title = "BidVelocity Payment Service API", description = "Winner invoices from events, payment state machine, provider abstraction", version = "0.1.0"))
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableScheduling
public class PaymentApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}
