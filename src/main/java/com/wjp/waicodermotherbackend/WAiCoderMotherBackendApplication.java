package com.wjp.waicodermotherbackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy = true)
// 扫描mapper包
@MapperScan("com.wjp.waicodermotherbackend.mapper")
public class WAiCoderMotherBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(WAiCoderMotherBackendApplication.class, args);
    }

}
