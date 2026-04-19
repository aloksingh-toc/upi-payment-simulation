package com.upi.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class UpiPaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(UpiPaymentApplication.class, args);
    }
}
