package com.jinpei.graceful.shutdown.springboot1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * springboot 1.x启动类
 * Created by liuzhaoming on 2020/12/21
 */
@SpringBootApplication
public class GracefulApplication {
    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(GracefulApplication.class, args);
        System.out.println("xxx");
    }
}
