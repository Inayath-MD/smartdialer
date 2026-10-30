package com.smartdialer.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI smartDialerOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SmartDialer API")
                        .description("Concurrency-safe progressive and predictive outbound dialer with independent safety controller")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("SmartDialer Engineering Team")
                                .email("engineering@smartdialer.internal"))
                        .license(new License().name("Apache 2.0")));
    }
}
