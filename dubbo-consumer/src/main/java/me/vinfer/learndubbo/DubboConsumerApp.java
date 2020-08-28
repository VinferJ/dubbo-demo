package me.vinfer.learndubbo;

import me.vinfer.learndubbo.model.User;
import me.vinfer.learndubbo.service.UserService;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import java.io.IOException;
import java.util.Scanner;

/**
 * @author Vinfer
 * @date 2020-08-17  03:26
 **/
public class DubboConsumerApp {


    public static void main(String[] args){
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(new String[] {"META-INF.spring/consumer.xml"});
        context.start();
        Scanner in = new Scanner(System.in);
        String username,account,password = null;
        UserService api = context.getBean("userService", UserService.class);

        while (true){
            System.out.println("==============================================================");
            System.out.println("choose service: " +
                    "\n[1-getUser(String username)] " +
                    "\n[2-deleteUser(String username)]" +
                    "\n[3-updateUser(User user)]" +
                    "\n[4-addUser(User user)]" +
                    "\n[5-getAllUsers()]" +
                    "\n[0-exit]");
            switch (in.nextInt()){
                case 1:
                    System.out.println("input username:");
                    username = in.next();
                    User userInfo = api.getUser(username);
                    System.out.println("get user info: \n"+userInfo);
                    break;
                case 2:
                    System.out.println("input username:");
                    username = in.next();
                    boolean deleteFlag = api.deleteUser(username);
                    System.out.println(deleteFlag?"delete user "+username+" success":"delete fail");
                    break;
                case 3:
                    System.out.println("input username:");
                    username = in.next();
                    System.out.println("input user account:");
                    account = in.next();
                    System.out.println("input user password");
                    password = in.next();
                    boolean updateFlag = api.updateUser(new User(username,account,password));
                    System.out.println(updateFlag?"update user "+username+" success":"update fail");
                    break;
                case 4:
                    System.out.println("input username:");
                    username = in.next();
                    System.out.println("input user account:");
                    account = in.next();
                    System.out.println("input user password");
                    password = in.next();
                    boolean addFlag = api.addUser(new User(username,account,password));
                    System.out.println(addFlag?"add user "+username+" success":"add fail");
                    break;
                case 5:
                    System.out.println("all users:\n"+api.getAllUsers());
                    break;
                default:
                    System.out.println("调用结束...");
                    System.exit(1);
                    break;
            }
        }
    }

}
