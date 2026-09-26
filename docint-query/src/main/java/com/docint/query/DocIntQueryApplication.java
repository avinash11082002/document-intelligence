package com.docint.query;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication(scanBasePackages = {"com.docint.query", "com.docint.common"})
@EntityScan(basePackages = {"com.docint.common.entity"})
public class DocIntQueryApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocIntQueryApplication.class, args);
    }
}
