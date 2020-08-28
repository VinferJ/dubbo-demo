package me.vinfer.learndubbo;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.io.IOException;

/**
 * @author Vinfer
 * @date 2020-08-17  01:20
 **/
public class DubboProviderApp {

    public static void main(String[] args) throws IOException {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(new String[]{"META-INF.spring/provider.xml"});
        context.start();
        System.out.println("Provider started.");
        /*阻塞main线程，不让服务启动后马上停止*/
        System.in.read(); // press any key to exit
    }
    
}
