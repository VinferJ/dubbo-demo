package me.vinfer.learndubbo;

import com.alibaba.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 使用@EnableDubbo开启基于注解的dubbo功能
 * 该注解的主要作用是规定包扫描规则
 * @author Vinfer
 */
@EnableDubbo
@SpringBootApplication
public class BootDubboConsumerApp {

	public static void main(String[] args) {
		SpringApplication.run(BootDubboConsumerApp.class, args);
	}

}
