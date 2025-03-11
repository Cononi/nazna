package io.github.cononi.core;

import io.github.cononi.core.annotation.*;
import io.github.cononi.core.exception.BeanNotFoundException;
import io.github.cononi.core.exception.BeanRegistrationException;
import io.github.cononi.core.exception.BeanTypeMismatchException;
import io.github.cononi.core.exception.CircularDependencyException;
import io.github.cononi.core.lifecycle.DisposableBean;
import io.github.cononi.core.lifecycle.InitializingBean;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NaznaContextFactory implements NaznaContext, AutoCloseable {
    private final Map<String, Object> beans = new ConcurrentHashMap<>();
    private final Map<String, Class<?>> beanDefinitions = new LinkedHashMap<>();
    private final Map<String, BeanMethod> beanMethodDefinitions = new LinkedHashMap<>();
    private final Set<String> beansInCreation = new HashSet<>(); // For circular dependency detection
    private boolean initialized = false;

    // Class to hold @Bean method information
    private static class BeanMethod {
        final Object configInstance;
        final Method method;
        final String beanName;

        BeanMethod(Object configInstance, Method method, String beanName) {
            this.configInstance = configInstance;
            this.method = method;
            this.beanName = beanName;
        }
    }

    @Override
    public void registerBean(Class<?> clazz) throws BeanRegistrationException {
        try {
            // Get bean name from @Component annotation or meta-annotation
            String beanName = getBeanName(clazz);
            if (beanName == null) {
                throw new BeanRegistrationException(clazz.getName() + " is not a component or does not have a component meta-annotation");
            }

            // Check if bean is already registered
            if (beans.containsKey(beanName)) {
                System.out.println("Bean already registered: " + beanName);
                return;
            }

            // Register bean definition for lazy initialization
            beanDefinitions.put(beanName, clazz);
            System.out.println("Registered bean definition: " + beanName);

            // If this is a @Configuration class, scan for @Bean methods
            if (clazz.isAnnotationPresent(Configuration.class)) {
                registerBeanMethods(clazz);
            }
        } catch (Exception e) {
            throw new BeanRegistrationException("Failed to register bean: " + clazz.getName(), e);
        }
    }

    /**
     * Scans a @Configuration class for @Bean methods and registers them
     */
    private void registerBeanMethods(Class<?> configClass) throws Exception {
        // Create instance of configuration class
        Object configInstance = createBean(getBeanName(configClass), configClass);

        // Find all methods annotated with @Bean
        for (Method method : configClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Bean.class)) {
                // Get bean name from @Bean annotation or method name
                Bean beanAnnotation = method.getAnnotation(Bean.class);
                String beanName = beanAnnotation.value().isEmpty() ?
                        method.getName() : beanAnnotation.value();

                // Check if bean is already registered
                if (beans.containsKey(beanName) || beanMethodDefinitions.containsKey(beanName)) {
                    System.out.println("Bean already registered: " + beanName);
                    continue;
                }

                // Make method accessible
                method.setAccessible(true);

                // Register bean method definition
                beanMethodDefinitions.put(beanName, new BeanMethod(configInstance, method, beanName));
                System.out.println("Registered @Bean method definition: " + beanName);
            }
        }
    }

    @Override
    public void registerBeans(List<Class<?>> classes) throws BeanRegistrationException {
        for (Class<?> clazz : classes) {
            registerBean(clazz);
        }
    }

    @Override
    public Object getBean(String name) {
        if (!containsBean(name)) {
            return null;
        }

        // If bean is already instantiated, return it
        if (beans.containsKey(name)) {
            return beans.get(name);
        }

        // Lazy load from class definition if available
        if (beanDefinitions.containsKey(name)) {
            return createBean(name, beanDefinitions.get(name));
        }

        // Lazy load from @Bean method if available
        if (beanMethodDefinitions.containsKey(name)) {
            return createBeanFromMethod(beanMethodDefinitions.get(name));
        }

        return null;
    }

    @Override
    public <T> T getBean(String name, Class<T> requiredType) throws BeanNotFoundException, BeanTypeMismatchException {
        Object bean = getBean(name);

        if (bean == null) {
            throw new BeanNotFoundException("No bean found with name: " + name);
        }

        if (!requiredType.isInstance(bean)) {
            throw new BeanTypeMismatchException(
                    "Bean named '" + name + "' is of type " + bean.getClass().getName() +
                            ", but required type is " + requiredType.getName());
        }

        return requiredType.cast(bean);
    }

    @Override
    public <T> T getBean(Class<T> clazz) {
        for (String beanName : beanDefinitions.keySet()) {
            Class<?> beanClass = beanDefinitions.get(beanName);
            if (clazz.isAssignableFrom(beanClass)) {
                Object bean = getBean(beanName);
                if (bean != null) {
                    return clazz.cast(bean);
                }
            }
        }

        // Check already instantiated beans
        for (Object bean : beans.values()) {
            if (clazz.isInstance(bean)) {
                return clazz.cast(bean);
            }
        }

        return null;
    }

    @Override
    public <T> Optional<T> getBeanOptional(Class<T> clazz) {
        return Optional.ofNullable(getBean(clazz));
    }

    @Override
    public <T> List<T> getBeansByType(Class<T> clazz) {
        List<T> result = new ArrayList<>();

        // Check bean definitions first for lazy loading
        for (String beanName : beanDefinitions.keySet()) {
            Class<?> beanClass = beanDefinitions.get(beanName);
            if (clazz.isAssignableFrom(beanClass)) {
                Object bean = getBean(beanName);
                if (bean != null) {
                    result.add(clazz.cast(bean));
                }
            }
        }

        // Add any beans that might have been registered directly
        for (Object bean : beans.values()) {
            if (clazz.isInstance(bean) && !result.contains(bean)) {
                result.add(clazz.cast(bean));
            }
        }

        return result;
    }

    @Override
    public Collection<Object> getBeans() {
        // Ensure all beans are initialized
        beanDefinitions.keySet().forEach(this::getBean);

        return new ArrayList<>(beans.values());
    }

    @Override
    public boolean containsBean(String name) {
        return beans.containsKey(name) || beanDefinitions.containsKey(name) || beanMethodDefinitions.containsKey(name);
    }

    @Override
    public boolean containsBeanOfType(Class<?> clazz) {
        // Check bean definitions
        for (Class<?> beanClass : beanDefinitions.values()) {
            if (clazz.isAssignableFrom(beanClass)) {
                return true;
            }
        }

        // Check already instantiated beans
        for (Object bean : beans.values()) {
            if (clazz.isInstance(bean)) {
                return true;
            }
        }

        // Check bean method definitions
        // We need to try to check return types of @Bean methods
        for (BeanMethod beanMethod : beanMethodDefinitions.values()) {
            Class<?> returnType = beanMethod.method.getReturnType();
            if (clazz.isAssignableFrom(returnType)) {
                return true;
            }
        }

        return false;
    }

    // initialize() 메소드 내에서 PostConstruct 처리 부분을 수정
    @Override
    public void initialize() {
        if (initialized) {
            return;
        }

        // 클래스 기반 빈 인스턴스화
        for (String beanName : new ArrayList<>(beanDefinitions.keySet())) {
            getBean(beanName);
        }

        // 메소드 기반 빈 인스턴스화
        for (String beanName : new ArrayList<>(beanMethodDefinitions.keySet())) {
            getBean(beanName);
        }

        // InitializingBean 구현 및 @PostConstruct 메소드 호출
        Map<String, Object> beansCopy = new HashMap<>(beans);
        for (Map.Entry<String, Object> entry : beansCopy.entrySet()) {
            String beanName = entry.getKey();
            Object bean = entry.getValue();

            // InitializingBean 인터페이스 구현체 처리
            if (bean instanceof InitializingBean) {
                try {
                    System.out.println("Initializing bean: " + beanName);
                    ((InitializingBean) bean).afterPropertiesSet();
                } catch (Exception e) {
                    throw new RuntimeException("Error during bean initialization: " + beanName + " (" +
                            bean.getClass().getName() + "): " + e.getMessage(), e);
                }
            }

            // @PostConstruct 애노테이션 메소드 호출
            invokePostConstructMethods(bean, beanName);
        }

        initialized = true;
        System.out.println("Context initialized with " + beans.size() + " beans");
    }

    @Override
    public void close() {
        if (!initialized) {
            System.out.println("Context is not initialized, nothing to close");
            return;
        }

        // 생성 순서의 역순으로 빈 목록 작성
        List<String> beanNames = new ArrayList<>(beans.keySet());
        // 생성 역순으로 빈 파괴하기 위해 목록 뒤집기
        Collections.reverse(beanNames);

        for (String beanName : beanNames) {
            Object bean = beans.get(beanName);

            // DisposableBean 구현체 destroy 호출
            if (bean instanceof DisposableBean) {
                try {
                    System.out.println("Destroying bean: " + beanName);
                    ((DisposableBean) bean).destroy();
                } catch (Exception e) {
                    System.err.println("Error during bean destruction: " + beanName + " (" +
                            bean.getClass().getName() + "): " + e.getMessage());
                }
            }

            // @PreDestroy 애노테이션 메소드 호출
            invokePreDestroyMethods(bean, beanName);
        }

        beans.clear();
        beanDefinitions.clear();
        beanMethodDefinitions.clear();
        beansInCreation.clear();
        initialized = false;
        System.out.println("Context closed");
    }

    // Helper methods

    private Object createBean(String beanName, Class<?> beanClass) {
        // 순환 의존성 검출
        if (beansInCreation.contains(beanName)) {
            throw new CircularDependencyException("Circular dependency detected: " + beansInCreation + " -> " + beanName);
        }

        try {
            beansInCreation.add(beanName);

            // 가장 적합한 생성자 찾기
            Constructor<?>[] constructors = beanClass.getDeclaredConstructors();
            if (constructors.length == 0) {
                throw new BeanRegistrationException("No constructor found for " + beanClass.getName());
            }

            Constructor<?> constructor = findMostSuitableConstructor(constructors);
            constructor.setAccessible(true);

            // 생성자 매개변수 해결
            Object[] parameters = resolveConstructorParameters(constructor);

            // 빈 인스턴스 생성
            Object bean = constructor.newInstance(parameters);

            // 빈 등록
            beans.put(beanName, bean);

            // 초기화 단계가 아닐 때 InitializingBean 인터페이스 구현 빈 초기화
            if (initialized && bean instanceof InitializingBean) {
                try {
                    ((InitializingBean) bean).afterPropertiesSet();
                } catch (Exception e) {
                    throw new BeanRegistrationException("Error during bean initialization: " + beanName, e);
                }
            }

            // 초기화 단계가 아닐 때 @PostConstruct 메서드 호출
            if (initialized) {
                invokePostConstructMethods(bean, beanName);
            }

            System.out.println("Instantiated bean: " + beanName);
            return bean;
        } catch (CircularDependencyException e) {
            throw e;
        } catch (Exception e) {
            throw new BeanRegistrationException("Failed to create bean: " + beanName, e);
        } finally {
            beansInCreation.remove(beanName);
        }
    }

    /**
     * Creates a bean from a @Bean method
     */
    private Object createBeanFromMethod(BeanMethod beanMethod) {
        String beanName = beanMethod.beanName;

        // 순환 의존성 검출
        if (beansInCreation.contains(beanName)) {
            throw new CircularDependencyException("Circular dependency detected: " + beansInCreation + " -> " + beanName);
        }

        try {
            beansInCreation.add(beanName);

            // 메서드 매개변수 해결
            Parameter[] parameters = beanMethod.method.getParameters();
            Object[] paramValues = new Object[parameters.length];

            for (int i = 0; i < parameters.length; i++) {
                Class<?> paramType = parameters[i].getType();
                Object dependency = getBean(paramType);

                if (dependency == null) {
                    throw new BeanRegistrationException("No bean found for parameter type: " + paramType.getName());
                }

                paramValues[i] = dependency;
            }

            // @Bean 메서드 호출
            Object bean = beanMethod.method.invoke(beanMethod.configInstance, paramValues);

            if (bean == null) {
                throw new BeanRegistrationException("@Bean method returned null for bean: " + beanName);
            }

            // 빈 등록
            beans.put(beanName, bean);

            // 초기화 단계가 아닐 때 InitializingBean 인터페이스 구현 빈 초기화
            if (initialized && bean instanceof InitializingBean) {
                try {
                    System.out.println("Initializing @Bean method bean: " + beanName);
                    ((InitializingBean) bean).afterPropertiesSet();
                } catch (Exception e) {
                    throw new BeanRegistrationException("Error during bean initialization: " + beanName, e);
                }
            }

            // 초기화 단계가 아닐 때 @PostConstruct 메서드 호출
            if (initialized) {
                invokePostConstructMethods(bean, beanName);
            }

            System.out.println("Instantiated bean from @Bean method: " + beanName);
            return bean;
        } catch (CircularDependencyException e) {
            throw e;
        } catch (Exception e) {
            throw new BeanRegistrationException("Failed to create bean from @Bean method: " + beanName, e);
        } finally {
            beansInCreation.remove(beanName);
        }
    }

    private Constructor<?> findMostSuitableConstructor(Constructor<?>[] constructors) {
        return Arrays.stream(constructors)
                .max(Comparator.comparingInt(Constructor::getParameterCount))
                .orElse(constructors[0]);
    }

    private Object[] resolveConstructorParameters(Constructor<?> constructor) {
        Parameter[] parameters = constructor.getParameters();
        Object[] paramValues = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            Class<?> paramType = parameters[i].getType();
            Object dependency = getBean(paramType);

            if (dependency == null) {
                throw new BeanRegistrationException("No bean found for parameter type: " + paramType.getName());
            }

            paramValues[i] = dependency;
            System.out.println("Resolved dependency: " + paramType.getSimpleName());
        }

        return paramValues;
    }

    private String getBeanName(Class<?> clazz) {
        // Direct @Component annotation
        Component component = clazz.getAnnotation(Component.class);
        if (component != null) {
            return component.value().isEmpty() ?
                    firstLetterToLowerCase(clazz.getSimpleName()) : component.value();
        }

        // Meta-annotation with @Component
        for (Annotation annotation : clazz.getAnnotations()) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (annotationType.isAnnotationPresent(Component.class)) {
                try {
                    Method valueMethod = annotationType.getDeclaredMethod("value");
                    String value = (String) valueMethod.invoke(annotation);

                    return value != null && !value.isEmpty() ?
                            value : firstLetterToLowerCase(clazz.getSimpleName());
                } catch (Exception e) {
                    return firstLetterToLowerCase(clazz.getSimpleName());
                }
            }
        }

        return null;
    }

    private String firstLetterToLowerCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.substring(0, 1).toLowerCase() + input.substring(1);
    }


    /**
     * Find methods with any annotation named "PostConstruct" regardless of package
     */
    private List<Method> findPostConstructMethods(Class<?> clazz) {
        List<Method> methods = new ArrayList<>();
        for (Method method : clazz.getDeclaredMethods()) {
            // Check direct annotations
            for (Annotation annotation : method.getAnnotations()) {
                String annotationName = annotation.annotationType().getSimpleName();
                String annotationPackage = annotation.annotationType().getPackage().getName();

                System.out.println("Checking method " + method.getName() + " for annotation: " +
                        annotationPackage + "." + annotationName);

                if (annotationName.equals("PostConstruct")) {
                    System.out.println("Found @PostConstruct on method: " + method.getName());
                    methods.add(method);
                    break;
                }
            }
        }
        return methods;
    }

    /**
     * Find methods with any annotation named "PreDestroy" regardless of package
     */
    private List<Method> findPreDestroyMethods(Class<?> clazz) {
        List<Method> methods = new ArrayList<>();
        for (Method method : clazz.getDeclaredMethods()) {
            // Check direct annotations
            for (Annotation annotation : method.getAnnotations()) {
                String annotationName = annotation.annotationType().getSimpleName();
                String annotationPackage = annotation.annotationType().getPackage().getName();

                System.out.println("Checking method " + method.getName() + " for annotation: " +
                        annotationPackage + "." + annotationName);

                if (annotationName.equals("PreDestroy")) {
                    System.out.println("Found @PreDestroy on method: " + method.getName());
                    methods.add(method);
                    break;
                }
            }
        }
        return methods;
    }

    /**
     * Invokes all PostConstruct methods on a bean
     */
    private void invokePostConstructMethods(Object bean, String beanName) {
        // @PostConstruct 애노테이션 메소드 찾기
        List<Method> postConstructMethods = findMethodsWithAnnotation(bean.getClass(), "PostConstruct");

        for (Method method : postConstructMethods) {
            try {
                method.setAccessible(true);
                System.out.println("Calling @PostConstruct method on bean: " + beanName + ", method: " + method.getName());
                method.invoke(bean);
            } catch (Exception e) {
                System.err.println("Error calling @PostConstruct method on bean: " + beanName + ": " + e.getMessage());
            }
        }

        // 관례적인 init 메소드 호출 (이미 @PostConstruct로 처리되지 않은 경우)
        try {
            Method initMethod = bean.getClass().getDeclaredMethod("init");
            if (!hasAnnotationWithName(initMethod, "PostConstruct")) {
                initMethod.setAccessible(true);
                System.out.println("Calling init method on bean: " + beanName);
                initMethod.invoke(bean);
            }
        } catch (NoSuchMethodException e) {
            // 메소드가 없는 경우 무시
        } catch (Exception e) {
            System.err.println("Error calling init method on bean: " + beanName + ": " + e.getMessage());
        }
    }

    /**
     * Invokes all PreDestroy methods on a bean
     */
    private void invokePreDestroyMethods(Object bean, String beanName) {
        // @PreDestroy 애노테이션 메소드 찾기
        List<Method> preDestroyMethods = findMethodsWithAnnotation(bean.getClass(), "PreDestroy");

        for (Method method : preDestroyMethods) {
            try {
                method.setAccessible(true);
                System.out.println("Calling @PreDestroy method on bean: " + beanName + ", method: " + method.getName());
                method.invoke(bean);
            } catch (Exception e) {
                System.err.println("Error calling @PreDestroy method on bean: " + beanName + ": " + e.getMessage());
            }
        }

        // 관례적인 destroy 메소드 호출 (이미 @PreDestroy로 처리되지 않은 경우)
        try {
            Method destroyMethod = bean.getClass().getDeclaredMethod("destroy");
            if (!hasAnnotationWithName(destroyMethod, "PreDestroy")) {
                destroyMethod.setAccessible(true);
                System.out.println("Calling destroy method on bean: " + beanName);
                destroyMethod.invoke(bean);
            }
        } catch (NoSuchMethodException e) {
            // 메소드가 없는 경우 무시
        } catch (Exception e) {
            System.err.println("Error calling destroy method on bean: " + beanName + ": " + e.getMessage());
        }
    }

    private boolean isAnnotationPresent(Method method, String annotationSimpleName) {
        for (Annotation annotation : method.getAnnotations()) {
            if (annotation.annotationType().getSimpleName().equals(annotationSimpleName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 메소드에 특정 이름의 애노테이션이 있는지 확인 (패키지에 상관없이)
     */
    private boolean hasAnnotationWithName(Method method, String annotationName) {
        for (Annotation annotation : method.getAnnotations()) {
            if (annotation.annotationType().getSimpleName().equals(annotationName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 클래스의 모든 메소드 중 특정 이름의 애노테이션이 있는 메소드 목록 반환
     */
    private List<Method> findMethodsWithAnnotation(Class<?> clazz, String annotationName) {
        List<Method> result = new ArrayList<>();
        for (Method method : clazz.getDeclaredMethods()) {
            if (hasAnnotationWithName(method, annotationName)) {
                result.add(method);
            }
        }
        return result;
    }

}