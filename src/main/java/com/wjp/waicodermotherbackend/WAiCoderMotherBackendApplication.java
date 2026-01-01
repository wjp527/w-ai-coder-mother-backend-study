package com.wjp.waicodermotherbackend;

import dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

// 禁用 RedisEmbeddingStoreAutoConfiguration,RAG检索增强
@SpringBootApplication(exclude = { RedisEmbeddingStoreAutoConfiguration.class })
@EnableAspectJAutoProxy(exposeProxy = true)
// 扫描mapper包
@MapperScan("com.wjp.waicodermotherbackend.mapper")
public class WAiCoderMotherBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(WAiCoderMotherBackendApplication.class, args);
    }

}
