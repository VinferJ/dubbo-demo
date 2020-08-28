package me.vinfer.learndubbo;

import com.alibaba.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author Vinfer
 */
@EnableDubbo
@SpringBootApplication
public class BootDubboProvider2App {

    public static void main(String[] args) {
        SpringApplication.run(BootDubboProvider2App.class, args);
    }

}
