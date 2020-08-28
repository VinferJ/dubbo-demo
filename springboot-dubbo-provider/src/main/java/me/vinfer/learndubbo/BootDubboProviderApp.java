package me.vinfer.learndubbo;

import com.alibaba.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * springboot整合dubbo的三种方式：
 *      1. 导入dubbo-starter，在application.properties中配置属性
 *         使用@Service（dubbo的）暴露服务，使用@Reference注解引用服务，
 *         这种方式需要配合@EnableDubbo注解来使用
 *      2. 保留dubbo的xml配置文件，做更精确的配置，不需要@EnableDubbo注解
 *         使用@ImportResource("classpath:xxx.xml")导入一个配置文件
 *         同时在provider对服务的暴露也不需要使用@Service注解
 *         在consumer对服务的引用也不需要再使用@Reference注解
 *      3. 使用注解以及配置类（@Configuration）的方式进行配置
 *         通过写一个专门的注解配置类来做详细的统一配置，将每一个组件都手动创建到容器中
 *         配置类中的方法返回值类型与xml配置标签所对应
 *         如dubbo:application 对应的配置类方法返回值类型应为：ApplicationConfig
 *
 * @author Vinfer
 */
@EnableDubbo
@SpringBootApplication
public class BootDubboProviderApp {

    public static void main(String[] args) {
        SpringApplication.run(BootDubboProviderApp.class, args);
    }

}
