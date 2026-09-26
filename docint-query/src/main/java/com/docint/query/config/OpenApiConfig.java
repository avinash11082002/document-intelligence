package com.docint.query.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI docIntQueryOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Document Intelligence — RAG Query API")
                        .description("Semantic question-answering over uploaded document collections with hybrid search and citation")
                        .version("1.0.0")
                        .contact(new Contact().name("Avinash")));
    }
}
