package com.jinpei.graceful.shutdown.springboot1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * springboot 1.x启动类
 * Created by liuzhaoming on 2020/12/21
 */
@SpringBootApplication
public class GracefulApplication {
    public static void main(String[] args) {
        SpringApplication.run(GracefulApplication.class, args);
    }
}
