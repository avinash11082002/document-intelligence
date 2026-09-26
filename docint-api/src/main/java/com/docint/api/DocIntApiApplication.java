package com.docint.api;

import com.docint.common.entity.Document;
import com.docint.common.entity.User;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication(scanBasePackages = {"com.docint.api", "com.docint.common"})
@EntityScan(basePackages = {"com.docint.common.entity"})
public class DocIntApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocIntApiApplication.class, args);
    }
}
