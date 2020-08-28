package me.vinfer.learndubbo.config;

import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.config.RegistryConfig;
import com.alibaba.dubbo.config.ServiceConfig;
import me.vinfer.learndubbo.api.OrderServiceApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *
 * 对dubbo使用注解配置
 * 返回值类型与xml配置标签所对应
 * @author Vinfer
 * @date 2020-08-18  11:22
 **/
//@Configuration
public class DubboConfig {

    //@Bean
    public ApplicationConfig applicationConfig(){
        ApplicationConfig applicationConfig = new ApplicationConfig();
        applicationConfig.setName("springboot-dubbo-provider");
        return applicationConfig;
    }

    //@Bean
    public RegistryConfig registryConfig(){
        RegistryConfig registryConfig = new RegistryConfig();
        registryConfig.setProtocol("zookeeper");
        registryConfig.setAddress("122.51.9.156:2181");
        return registryConfig;
    }


}
