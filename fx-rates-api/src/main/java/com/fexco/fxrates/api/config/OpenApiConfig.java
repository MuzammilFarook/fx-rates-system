package com.fexco.fxrates.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration for OpenAPI/Swagger documentation
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI fxRatesOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FX Rates API")
                        .description("REST API for querying real-time and historical foreign exchange rates")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Fexco Engineering")
                                .email("engineering@fexco.com")
                                .url("https://www.fexco.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local Development Server"),
                        new Server()
                                .url("https://api.fexco.com")
                                .description("Production Server")
                ));
    }
}
