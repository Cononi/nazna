package io.github.cononi.core.lifecycle;

/**
 * Interface to be implemented by beans that need to perform initialization
 * after all properties are set. Similar to Spring's InitializingBean.
 */
public interface InitializingBean {

    /**
     * Invoked by the NaznaContext after all bean properties have been set
     * and before the bean is ready for use.
     *
     * @throws Exception in case of initialization errors
     */
    void afterPropertiesSet() throws Exception;
}