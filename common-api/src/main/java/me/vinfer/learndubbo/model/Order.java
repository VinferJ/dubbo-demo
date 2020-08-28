package me.vinfer.learndubbo.model;

import java.io.Serializable;

/**
 * @author Vinfer
 * @date 2020-08-17  06:42
 **/
public class Order implements Serializable {

    private Integer orderId;

    private User userInfo;

    public Order(Integer orderId, User userInfo) {
        this.orderId = orderId;
        this.userInfo = userInfo;
    }

    public Order(){}

    @Override
    public String toString() {
        return "Order{" +
                "orderId=" + orderId +
                ", userInfo=" + userInfo +
                '}';
    }

    public Integer getOrderId() {
        return orderId;
    }

    public void setOrderId(Integer orderId) {
        this.orderId = orderId;
    }

    public User getUserInfo() {
        return userInfo;
    }

    public void setUserInfo(User userInfo) {
        this.userInfo = userInfo;
    }
}
