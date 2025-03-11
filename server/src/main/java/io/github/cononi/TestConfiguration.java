package io.github.cononi;

import io.github.cononi.core.annotation.Bean;
import io.github.cononi.core.annotation.Configuration;
import io.github.cononi.core.annotation.PostConstruct;
import io.github.cononi.core.annotation.PreDestroy;

@Configuration
public class TestConfiguration {

    @Bean
    public String name() {
        return "name";
    }

    @Bean
    public String name2() {
        return "name";
    }

    @PostConstruct
    public void postConstruct() {
        System.out.println("나 실행 해요 ~");
    }

    @PreDestroy
    public void preDestroy() {
        System.out.println("나 죽어용~");
    }
}
