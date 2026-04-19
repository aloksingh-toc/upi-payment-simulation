package com.upi.payment.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("UPI Payment Gateway API")
                        .description("""
                                Simulation of a UPI (Unified Payments Interface) payment gateway.

                                **Authentication**
                                - Payment and account endpoints require a Bearer API key in the `Authorization` header.
                                - Webhook endpoints use HMAC-SHA256 signature verification via `X-Webhook-Signature`.

                                **Note:** UPI is regulated by the Reserve Bank of India (RBI) and NPCI.
                                This API is scoped to the Indian financial ecosystem (INR only).
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("UPI Payment Simulation")
                                .url("https://github.com")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local development")))
                .components(new Components()
                        .addSecuritySchemes("BearerAuth", new SecurityScheme()
                                .name("BearerAuth")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("API Key")
                                .description("Enter your API key. Configured via APP_API_KEY environment variable.")));
    }
}
