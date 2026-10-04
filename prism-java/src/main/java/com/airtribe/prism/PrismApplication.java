package com.airtribe.prism;

import com.airtribe.prism.config.DotenvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PrismApplication {

    static {
        DotenvLoader.load();
    }

    public static void main(String[] args) {
        DotenvLoader.load();
        SpringApplication.run(PrismApplication.class, args);
    }
}
