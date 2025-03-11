package io.github.cononi.core;

import java.util.Collection;
import java.util.List;

public interface NaznaContext {

    void registerBean(Class<?> clazz);

    void registerBeans(List<Class<?>> classes);

    Object getBean(String name);

    Collection<Object> getBeans();

    <T> T getBean(Class<T> clazz);

    <T> List<T> getBeansByType(Class<T> clazz);

}
