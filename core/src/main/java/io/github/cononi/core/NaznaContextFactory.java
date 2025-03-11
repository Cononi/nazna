package io.github.cononi.core;

import io.github.cononi.core.annotation.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;

public class NaznaContextFactory implements NaznaContext{
    private final SequencedMap<String, Object> beans = new LinkedHashMap<>();

    // public NaznaContextFactory() {
    //     this.beans = new LinkedHashMap<>();
    // }

    // 클래스를 Bean으로 등록하는 메소드
    @Override
    public void registerBean(Class<?> clazz) {
        try {
            // @Component 어노테이션 또는 메타 어노테이션 확인
            String beanName = getBeanName(clazz);
            if (beanName == null) {
                throw new IllegalArgumentException(clazz.getName() + " is not a component or does not have a component meta-annotation");
            }

            // 이미 등록된 Bean인지 확인
            if (beans.containsKey(beanName)) {
                System.out.println("Bean already registered: " + beanName);
                return;
            }

            // 클래스의 생성자 확인
            Constructor<?>[] constructors = clazz.getDeclaredConstructors();
            if (constructors.length == 0) {
                throw new RuntimeException("No constructor found for " + clazz.getName());
            }

            // 가장 많은 파라미터를 가진 생성자 선택 (Spring 방식과 유사)
            Constructor<?> constructor = findMostSuitableConstructor(constructors);
            constructor.setAccessible(true);

            // 생성자 파라미터 확인 및 의존성 해결
            Object[] parameters = resolveConstructorParameters(constructor);

            // Bean 인스턴스 생성
            Object bean = constructor.newInstance(parameters);

            // Bean 등록 (이름으로만 등록)
            beans.put(beanName, bean);

            System.out.println("Registered bean: " + beanName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to register bean: " + clazz.getName(), e);
        }
    }

    @Override
    public void registerBeans(List<Class<?>> classes) {
        // 의존성이 없는 클래스부터 등록
        List<Class<?>> pending = new ArrayList<>(classes);

        while (!pending.isEmpty()) {
            boolean progress = false;

            for (int i = 0; i < pending.size(); i++) {
                Class<?> clazz = pending.get(i);
                Constructor<?> constructor = findMostSuitableConstructor(clazz.getDeclaredConstructors());
                boolean canInstantiate = true;

                // 모든 의존성이 이미 등록되어 있는지 확인
                for (Parameter param : constructor.getParameters()) {
                    if (getBean(param.getType()) == null) {
                        canInstantiate = false;
                        break;
                    }
                }

                // 모든 의존성이 해결되면 Bean 등록
                if (canInstantiate) {
                    registerBean(clazz);
                    pending.remove(i);
                    progress = true;
                    break;
                }
            }

            // 더 이상 진행이 없으면 순환 의존성 가능성이 있음
            if (!progress && !pending.isEmpty()) {
                throw new RuntimeException("Circular dependency detected among: " + pending);
            }
        }
    }

    @Override
    public Object getBean(String name) {
        return beans.get(name);
    }

    @Override
    public <T> T getBean(Class<T> clazz) {
        // beans 맵에서 직접 타입 기반 검색
        for (Object bean : beans.values()) {
            if (clazz.isInstance(bean)) {
                return clazz.cast(bean);
            }
        }

        return null;
    }

    @Override
    public Collection<Object> getBeans() {
        System.out.println("Total : " + beans.size());
        return beans.values();
    }

    // 클래스 타입으로 모든 Bean을 가져오는 메소드 (필요 시 사용)
    @Override
    public <T> List<T> getBeansByType(Class<T> clazz) {
        List<T> result = new ArrayList<>();
        for (Object bean : beans.values()) {
            if (clazz.isInstance(bean)) {
                result.add(clazz.cast(bean));
            }
        }
        return result;
    }


    // 가장 적합한 생성자 찾기 (가장 많은 파라미터를 가진 생성자)
    private Constructor<?> findMostSuitableConstructor(Constructor<?>[] constructors) {
        Constructor<?> selectedConstructor = constructors[0];
        for (Constructor<?> constructor : constructors) {
            if (constructor.getParameterCount() > selectedConstructor.getParameterCount()) {
                selectedConstructor = constructor;
            }
        }
        return selectedConstructor;
    }

    // 생성자 파라미터 해결
    private Object[] resolveConstructorParameters(Constructor<?> constructor) {
        Parameter[] parameters = constructor.getParameters();
        Object[] paramValues = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            Class<?> paramType = parameters[i].getType();
            Object dependency = getBean(paramType);

            if (dependency == null) {
                throw new RuntimeException("No bean found for parameter type: " + paramType.getName());
            }

            paramValues[i] = dependency;
            System.out.println("Resolved dependency: " + paramType.getSimpleName());
        }

        return paramValues;
    }

    // 클래스 이름의 첫 글자를 소문자로 변환하는 유틸리티 메소드
    private String firstLetterToLowerCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.substring(0, 1).toLowerCase() + input.substring(1);
    }

    // Bean 이름을 결정하는 메소드 (Component 또는 메타 어노테이션 처리)
    private String getBeanName(Class<?> clazz) {
        // 직접 @Component 어노테이션이 있는 경우
        Component component = clazz.getAnnotation(Component.class);
        if (component != null) {
            return component.value().isEmpty() ?
                    firstLetterToLowerCase(clazz.getSimpleName()) : component.value();
        }

        // 메타 어노테이션으로 @Component가 있는 경우 검사
        for (Annotation annotation : clazz.getAnnotations()) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (annotationType.isAnnotationPresent(Component.class)) {
                // 메타 어노테이션의 value 메소드 호출 시도
                try {
                    java.lang.reflect.Method valueMethod = annotationType.getDeclaredMethod("value");
                    String value = (String) valueMethod.invoke(annotation);

                    if (value != null && !value.isEmpty()) {
                        return value;
                    } else {
                        return firstLetterToLowerCase(clazz.getSimpleName());
                    }
                } catch (Exception e) {
                    // value 메소드가 없거나 호출 실패 시 클래스 이름 사용
                    return firstLetterToLowerCase(clazz.getSimpleName());
                }
            }
        }

        // @Component 또는 메타 어노테이션이 없는 경우
        return null;
    }

}
