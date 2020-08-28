package me.vinfer.learndubbo.service;

import com.alibaba.dubbo.config.annotation.Service;
import me.vinfer.learndubbo.api.UserServiceApi;
import me.vinfer.learndubbo.model.User;
import org.springframework.stereotype.Component;

import java.util.HashMap;

/**
 * 集成springboot后，对于需要暴露的服务使用ali的@Service注解
 * 然后再使用springboot的@Compont注解扫描到ioc中
 * @author Vinfer
 * @date 2020-08-17  05:24
 **/
@Service
@Component
public class ProviderApiImpl implements UserServiceApi {
    private static final HashMap<String,User> USER_TABLE = new HashMap<>(16);

    static {
        User user1 = new User("vinfer","18150115813","pass@vinfer");
        User user2 = new User("zhangsan","18898081821","pass@zhangsan");
        User user3 = new User("lisi","18950099022","pass@lisi");
        USER_TABLE.put(user1.getUsername(), user1);
        USER_TABLE.put(user2.getUsername(), user2);
        USER_TABLE.put(user3.getUsername(), user3);
    }

    @Override
    public HashMap<String, User> getAllUsers() {
        return USER_TABLE;
    }

    @Override
    public User getUserInfo(String username) {
        return USER_TABLE.get(username);
    }

    @Override
    public boolean deleteUser(String username) {
        return USER_TABLE.remove(username)!=null;
    }

    @Override
    public boolean addUser(User user) {
        if(user==null){
            return false;
        }else {
            return USER_TABLE.put(user.getUsername(), user)!=null;
        }
    }

    @Override
    public boolean updateUser(User user) {
        if(user==null){
            return false;
        }else {
            return USER_TABLE.replace(user.getUsername(), user) != null;
        }
    }
}
