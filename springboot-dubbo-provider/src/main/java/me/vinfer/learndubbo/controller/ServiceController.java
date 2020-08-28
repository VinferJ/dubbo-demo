package me.vinfer.learndubbo.controller;

import me.vinfer.learndubbo.model.User;
import me.vinfer.learndubbo.service.ProviderApiImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

/**
 * @author Vinfer
 * @date 2020-08-17  05:43
 **/
@RestController
@RequestMapping("/dubbo/provider")
public class ServiceController {

    @Resource
    ProviderApiImpl providerApi;
    

    @GetMapping("/user/{username}")
    public User getUser(@PathVariable String username){
        return providerApi.getUserInfo(username);
    }

    @GetMapping("/user/all")
    public Object getAllUser(){
        return providerApi.getAllUsers();
    }

    @GetMapping("/user/del/{username}")
    public String deleteUser(@PathVariable String username){
        return providerApi.deleteUser(username)?"delete user "+username+" success!":"delete fail!";
    }


}
