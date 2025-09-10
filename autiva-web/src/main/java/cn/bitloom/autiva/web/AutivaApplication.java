package cn.bitloom.autiva.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;

/**
 * The type Autiva application.
 *
 * @author bitloom
 */
@SpringBootApplication
@ComponentScan(basePackages = "cn.bitloom.autiva")
@ConfigurationPropertiesScan(basePackages = {"cn.bitloom.autiva.agentic.acm"})
public class AutivaApplication {

    /**
     * The entry point of application.
     *
     * @param args the input arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(AutivaApplication.class, args);
    }

}
