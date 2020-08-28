package me.vinfer.learndubbo.controller;

import me.vinfer.learndubbo.model.Order;
import me.vinfer.learndubbo.service.OrderServiceImpl;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * @author Vinfer
 * @date 2020-08-17  06:47
 **/
//@RestController
//@RequestMapping(value = "/dubbo/provider")
public class ServiceController {

    /*@Resource
    private OrderServiceImpl orderService;

    @GetMapping(value = "/order")
    public Order createOrder(@RequestParam(value = "username") String username){
        return orderService.createOrder(username);
    }*/

}
