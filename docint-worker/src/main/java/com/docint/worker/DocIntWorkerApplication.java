package com.docint.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication(scanBasePackages = {"com.docint.worker", "com.docint.common"})
@EntityScan(basePackages = {"com.docint.common.entity"})
public class DocIntWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocIntWorkerApplication.class, args);
    }
}
