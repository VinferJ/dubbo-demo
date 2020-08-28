package me.vinfer.learndubbo.service;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.dubbo.config.annotation.Service;
import me.vinfer.learndubbo.api.UserServiceApi;
import me.vinfer.learndubbo.api.OrderServiceApi;
import me.vinfer.learndubbo.model.Order;
import me.vinfer.learndubbo.model.User;
import org.springframework.stereotype.Component;

/**
 * @author Vinfer
 * @date 2020-08-17  06:45
 **/
@Service
@Component
public class OrderServiceImpl implements OrderServiceApi {

    /**
     * 这里直接使用@Reference注解进行远程引用
     * 一旦一个provider中调用了其他provider的接口
     * 那么该provider将会自动获得消费者角色
     * */
    @Reference
    private UserServiceApi userServiceApi;

    @Override
    public Order createOrder(String username) {
        User userInfo = userServiceApi.getUserInfo(username);
        if(userInfo!=null){
            return new Order(username.hashCode(),userInfo);
        }
        return null;
    }
}
