package me.vinfer.learndubbo.api;

import me.vinfer.learndubbo.model.Order;

/**
 * @author Vinfer
 * @date 2020-08-17  06:41
 **/
public interface OrderServiceApi {


    Order createOrder(String username);

}
