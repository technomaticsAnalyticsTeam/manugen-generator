package ru.leyman.manugen.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;

@Configuration
public class ExecutorConfig {

    @Bean
    public ExecutorService executor() {
        return ForkJoinPool.commonPool();
    }

}
