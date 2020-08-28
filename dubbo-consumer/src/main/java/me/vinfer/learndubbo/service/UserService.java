package me.vinfer.learndubbo.service;

import me.vinfer.learndubbo.api.UserServiceApi;
import me.vinfer.learndubbo.model.User;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;


/**
 * @author Vinfer
 * @date 2020-08-17  03:27
 **/
@Service
public class UserService {

    @Resource
    UserServiceApi userServiceApi;


    public User getUser(String username){
        return userServiceApi.getUserInfo(username);
    }

    public boolean deleteUser(String username){
        return userServiceApi.deleteUser(username);
    }

    public boolean updateUser(User user){
        return userServiceApi.updateUser(user);
    }

    public boolean addUser(User user){
        return userServiceApi.addUser(user);
    }

    public Object getAllUsers(){
        return userServiceApi.getAllUsers();
    }


}
