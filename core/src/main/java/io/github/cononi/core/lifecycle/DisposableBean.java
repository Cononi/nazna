package io.github.cononi.core.lifecycle;

/**
 * Interface to be implemented by beans that need to perform cleanup
 * when the context is closed. Similar to Spring's DisposableBean.
 */
public interface DisposableBean {

    /**
     * Invoked by the NaznaContext when the context is being closed
     * to perform cleanup operations.
     *
     * @throws Exception in case of destruction errors
     */
    void destroy() throws Exception;
}