package com.wjp.waicodermotherbackend.ai.config;

import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis 对话记忆存储配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "spring.data.redis")
public class RedisChatMemoryStoreConfig {

    private String host;
    private int port;
    private String password;
    private Long ttl;

    @Bean
    public RedisChatMemoryStore redisChatMemoryStore() {
        return RedisChatMemoryStore.builder()
                .host(host)
                .port(port)
                // 如果密码不为空，一定要加上 user
                // .user("default")
                .password(password)
                .ttl(ttl)
                .build();
    }

}
