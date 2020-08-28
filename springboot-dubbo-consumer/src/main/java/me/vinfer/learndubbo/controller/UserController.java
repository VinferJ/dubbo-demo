package me.vinfer.learndubbo.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import me.vinfer.learndubbo.api.UserServiceApi;
import me.vinfer.learndubbo.api.OrderServiceApi;
import me.vinfer.learndubbo.model.Order;
import me.vinfer.learndubbo.model.User;
import org.springframework.web.bind.annotation.*;

/**
 * @author Vinfer
 * @date 2020-08-17  05:54
 **/
@RestController
@RequestMapping("/dubbo/consumer")
public class UserController {

    /**
     * 对于引用provider的api，dubbo提供了@Reference注解进行远程引用，不需要标注@Resoure或者@Autowired
     * 通过配置Reference的url属性可以实现绕过注册中心的直连，直连的服务完全不受注册中心影响
     * */
    @Reference
    private UserServiceApi userServiceApi;

    @Reference
    private OrderServiceApi orderServiceApi;

    @GetMapping(value = "/user/{username}")
    public User getUser(@PathVariable String username){
        return userServiceApi.getUserInfo(username);
    }

    @GetMapping(value = "/user/del/{username}")
    public String deleteUser(@PathVariable String username){
        return userServiceApi.deleteUser(username)?"delete user "+username+" success!":"delete fail!";
    }

    @GetMapping(value = "/user/get/all")
    public Object getAllUser(){
        return userServiceApi.getAllUsers();
    }

    @GetMapping(value = "/order")
    public Object createOrder(@RequestParam(value = "username") String username){
        System.out.println(username);
        Order order = orderServiceApi.createOrder(username);
        return order==null?"can not found user "+username:order;
    }

}
