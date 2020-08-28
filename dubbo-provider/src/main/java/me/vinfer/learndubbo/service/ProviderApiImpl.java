package me.vinfer.learndubbo.service;

import me.vinfer.learndubbo.api.UserServiceApi;
import me.vinfer.learndubbo.model.User;
import org.springframework.stereotype.Service;
import java.util.HashMap;

/**
 * 在@Service注解中的属性配置对应了dubbo:service标签中所有的属性配置
 * 但是@Service注解无法做到像xml中在dubbo:service内部再对接口的
 * 指定方法做更精确的配置，如果在springboot中想要做这种精确配置
 * 需要保留dubbo的xml配置文件
 *
 * @author Vinfer
 * @date 2020-08-17  01:42
 **/
@Service
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
        System.out.println("version 1....");
        return USER_TABLE;
    }

    @Override
    public User getUserInfo(String username) {
        System.out.println("version 1....");
        return USER_TABLE.get(username);
    }

    @Override
    public boolean deleteUser(String username) {
        System.out.println("version 1....");
        return USER_TABLE.remove(username)!=null;
    }

    @Override
    public boolean addUser(User user) {
        System.out.println("version 1....");
        if(user==null){
            return false;
        }else {
            return USER_TABLE.put(user.getUsername(), user)!=null;
        }
    }

    @Override
    public boolean updateUser(User user) {
        System.out.println("version 1....");
        if(user==null){
            return false;
        }else {
            return USER_TABLE.replace(user.getUsername(), user) != null;
        }
    }
}
