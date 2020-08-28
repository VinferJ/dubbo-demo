package me.vinfer.learndubbo.api;

import me.vinfer.learndubbo.model.User;
import java.util.HashMap;

/**
 *
 * @author Vinfer
 * @date 2020-08-17  01:31
 **/
public interface UserServiceApi {


    HashMap<String,User> getAllUsers();

    User getUserInfo(String username);

    boolean deleteUser(String username);

    boolean addUser(User user);

    boolean updateUser(User user);

}
