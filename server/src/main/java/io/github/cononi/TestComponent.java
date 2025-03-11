package io.github.cononi;

import io.github.cononi.core.annotation.Component;
import io.github.cononi.core.annotation.PostConstruct;
import io.github.cononi.core.annotation.PreDestroy;

@Component
public class TestComponent {
    @PostConstruct
    public void postConstruct() {
        System.out.println("나 실행 해요 ~");
    }

    @PreDestroy
    public void preDestroy() {
        System.out.println("나 죽어용~");
    }
}
