package io.github.cononi.core;

import io.github.cononi.core.exception.BeanNotFoundException;
import io.github.cononi.core.exception.BeanRegistrationException;
import io.github.cononi.core.exception.BeanTypeMismatchException;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Interface defining the core functionality of the Nazna application context.
 * Provides methods for bean registration, retrieval, and lifecycle management.
 */
public interface NaznaContext extends AutoCloseable {
    /**
     * Register a class as a bean in the context
     * @param clazz The class to register
     * @throws BeanRegistrationException If registration fails
     */
    void registerBean(Class<?> clazz) throws BeanRegistrationException;

    /**
     * Register multiple classes as beans in the context
     * @param classes The classes to register
     * @throws BeanRegistrationException If registration fails
     */
    void registerBeans(List<Class<?>> classes) throws BeanRegistrationException;

    /**
     * Get a bean by name
     * @param name The bean name
     * @return The bean instance or null if not found
     */
    Object getBean(String name);

    /**
     * Get a bean by name with type safety
     * @param name The bean name
     * @param requiredType The expected bean type
     * @return The bean instance
     * @throws BeanNotFoundException If no bean is found
     * @throws BeanTypeMismatchException If the bean is not of the required type
     */
    <T> T getBean(String name, Class<T> requiredType) throws BeanNotFoundException, BeanTypeMismatchException;

    /**
     * Get a bean by type
     * @param clazz The bean type
     * @return The bean instance or null if not found
     */
    <T> T getBean(Class<T> clazz);

    /**
     * Get a bean by type as Optional
     * @param clazz The bean type
     * @return Optional containing the bean or empty if not found
     */
    <T> Optional<T> getBeanOptional(Class<T> clazz);

    /**
     * Get all beans of a specific type
     * @param clazz The bean type
     * @return List of beans of the specified type
     */
    <T> List<T> getBeansByType(Class<T> clazz);

    /**
     * Get all beans in the context
     * @return Collection of all beans
     */
    Collection<Object> getBeans();

    /**
     * Check if a bean with the given name exists
     * @param name The bean name
     * @return true if the bean exists
     */
    boolean containsBean(String name);

    /**
     * Check if a bean with the given type exists
     * @param clazz The bean type
     * @return true if the bean exists
     */
    boolean containsBeanOfType(Class<?> clazz);

    /**
     * Initialize the context after all beans are registered
     */
    void initialize();

    /**
     * Close the context and perform cleanup
     */
    void close();
}