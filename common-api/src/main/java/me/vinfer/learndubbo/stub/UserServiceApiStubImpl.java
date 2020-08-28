package me.vinfer.learndubbo.stub;

import me.vinfer.learndubbo.api.UserServiceApi;
import me.vinfer.learndubbo.model.User;

import java.util.HashMap;

/**
 * 用户服务的本地存根
 * 本地存根stub：
 *      远程服务后，客户端通常只剩下接口，而实现全在服务器端，但提供方有些时候想在客户端也执行部分逻辑，
 *      比如：做 ThreadLocal 缓存，提前验证参数，调用失败后伪造容错数据等等，此时就需要在 API 中带上 Stub，
 *      客户端生成 Proxy 实例，会把 Proxy 通过构造函数传给 Stub [1]，
 *      然后把 Stub 暴露给用户，Stub 可以决定要不要去调 Proxy。
 * @author Vinfer
 * @date 2020-08-18  10:12
 **/
public class UserServiceApiStubImpl implements UserServiceApi {

    private final UserServiceApi userServiceApi;

    /**
     * 这里的注入是有dubbo来做的，传入的是UserService的远程代理对象
     * 前提是dubbo:service中对UserService必须将stub配置为true或者指定class
     * 又或者是在dubbo:reference中配置（在reference中配置更方便）
     * @param userServiceApi       用户服务接口
     */
    public UserServiceApiStubImpl(UserServiceApi userServiceApi){
        this.userServiceApi = userServiceApi;
    }


    @Override
    public HashMap<String, User> getAllUsers() {
        /*
        * 此代码（return之前的）在客户端执行, 你可以在客户端做ThreadLocal本地缓存，或预先验证参数是否合法，等等
        * */
        System.out.println("execute in stub before remote calling...");
        try {
            //userService.xxx 才是发起真正的远程调用
            return userServiceApi.getAllUsers();
        }catch (Exception e){
            /*发生异常后可以返回容错数据*/
            return null;
        }

    }

    @Override
    public User getUserInfo(String username) {
        try {
            if(username!=null){
                User userInfo = userServiceApi.getUserInfo(username);
                if(userInfo!=null){
                    return userInfo;
                }else {
                    System.out.println("can not found user "+username);
                }
            }
            return new User();
        }catch (Exception e){
            e.printStackTrace();
            return new User();
        }
    }

    @Override
    public boolean deleteUser(String username) {
        try {
            if(username!=null){
                if(getUserInfo(username)!=null){
                    return userServiceApi.deleteUser(username);
                }else {
                    System.out.println("can not found user "+username);
                }
            }
            return false;
        }catch (Exception e){
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean addUser(User user) {
        try {
            if(user!=null){
                return userServiceApi.addUser(user);
            }else {
                return false;
            }
        }catch (Exception e){
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean updateUser(User user) {
        try {
            if(user!=null){
                if(getUserInfo(user.getUsername())!=null){
                    return userServiceApi.updateUser(user);
                }else {
                    System.out.println("can not found user "+user.getUsername());
                }
            }
            return false;
        }catch (Exception e){
            e.printStackTrace();
            return false;
        }
    }
}
