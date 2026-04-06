package com.xi.circle.server.config;

import com.jingdianjichi.circle.server.sensitive.WordContext;
import com.jingdianjichi.circle.server.sensitive.WordFilter;
import com.jingdianjichi.circle.server.service.SensitiveWordsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 这段代码核心作用：Spring 容器中装配「敏感词过滤」工具类
 * 把敏感词过滤需要的两个核心对象，交给 Spring 管理（变成 Bean），方便项目里随时注入使用。
 */
@Configuration
public class SensitiveConfig {

    @Bean
    public WordContext wordContext(SensitiveWordsService service) {
        return new WordContext(true, service);
    }

    @Bean
    public WordFilter wordFilter(WordContext wordContext) {
        return new WordFilter(wordContext);
    }

}
