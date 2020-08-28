package me.vinfer.learndubbo.model;

import java.io.Serializable;

/**
 * dubbo传输的对象必须实现可序列化Serializable接口
 * @author Vinfer
 * @date 2020-08-17  01:34
 **/
public class User implements Serializable {

    /**用户名*/
    private String username;

    /**账号*/
    private String account;

    /**密码*/
    private String password;


    public User(String username, String account, String password) {
        this.username = username;
        this.account = account;
        this.password = password;
    }

    public User(){}

    @Override
    public String toString() {
        return "User{" +
                "username='" + username + '\'' +
                ", account='" + account + '\'' +
                ", password='" + password + '\'' +
                '}';
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
