package com.wjp.waicodeuser;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@MapperScan("com.wjp.waicodeuser.mapper")
@ComponentScan("com.wjp")
public class WAiCodeUserApplication {
    public static void main(String[] args) {
        SpringApplication.run(WAiCodeUserApplication.class, args);
    }
}
