package io.github.cononi.core;

import io.github.cononi.core.annotation.Component;
import io.github.cononi.core.annotation.NaznaServerApplication;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;

public class NaznaApplication {
    private final NaznaContext naznaContext;

    public NaznaApplication() {
        this.naznaContext = new NaznaContextFactory();
    }

    public NaznaApplication run(Class<?> primarySource, String... args) {
        NaznaServerApplication annotation = primarySource.getAnnotation(NaznaServerApplication.class);

        if (annotation == null) {
            throw new RuntimeException("The primary source class must be annotated with @NaznaServerApplication");
        }

        // 클래스패스 스캐닝 활성화 여부 확인
        boolean scanClasspath = annotation.scanClasspath();

        if (scanClasspath) {
            // 컴포넌트 스캔 패키지 리스트 가져오기
            String[] scanPackages = annotation.scanPackages();
            if (scanPackages.length == 0) {
                // 기본값으로 주 클래스의 패키지 사용
                scanPackages = new String[] { primarySource.getPackage().getName() };
            }

            // 패키지 스캔 및 빈 등록
            for (String basePackage : scanPackages) {
                scanPackageAndRegisterBeans(basePackage);
            }
        }

        // 모든 빈을 등록한 후 컨텍스트 초기화 (PostConstruct 메서드 호출)
        naznaContext.initialize();

        System.out.println("Application started with " + naznaContext.getBeans().size() + " beans");

        // 애플리케이션 종료 후크 등록 (PreDestroy 메서드 호출)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Application shutting down...");
            naznaContext.close();
        }));

        return this;
    }

    public NaznaContext getContext() {
        return naznaContext;
    }

    // 패키지 스캔 및 컴포넌트 등록
    private void scanPackageAndRegisterBeans(String basePackage) {
        try {
            System.out.println("Scanning package: " + basePackage);

            // 패키지 내의 모든 클래스 찾기
            List<Class<?>> componentsInPackage = findClassesInPackage(basePackage);

            // 컴포넌트만 필터링
            List<Class<?>> components = componentsInPackage.stream()
                    .filter(this::isComponent)
                    .collect(Collectors.toList());

            System.out.println("Found " + components.size() + " components in package " + basePackage);

            components.forEach(e-> {
                System.out.println(" 컴포넌트 : " +  e.toString());
            });
            // 빈 등록
            naznaContext.registerBeans(components);
        } catch (Exception e) {
            throw new RuntimeException("Error scanning package: " + basePackage, e);
        }
    }

    // 클래스가 컴포넌트인지 확인
    private boolean isComponent(Class<?> clazz) {
        // @Component 어노테이션이 직접 있는지 확인
        if (clazz.isAnnotationPresent(Component.class)) {
            return true;
        }

        // 메타 어노테이션으로 @Component가 있는지 확인
        for (Annotation annotation : clazz.getAnnotations()) {
            if (annotation.annotationType().isAnnotationPresent(Component.class)) {
                return true;
            }
        }

        return false;
    }


    // 패키지 내의 모든 클래스 찾기
    private List<Class<?>> findClassesInPackage(String packageName) throws Exception {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        String path = packageName.replace('.', '/');
        Enumeration<URL> resources = classLoader.getResources(path);
        List<File> dirs = new ArrayList<>();

        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            dirs.add(new File(resource.getFile()));
        }

        List<Class<?>> classes = new ArrayList<>();
        for (File directory : dirs) {
            classes.addAll(findClassesInDirectory(directory, packageName));
        }

        return classes;
    }

    // 디렉토리 내의 모든 클래스 찾기 (재귀적)
    private List<Class<?>> findClassesInDirectory(File directory, String packageName) throws ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();

        if (!directory.exists()) {
            return classes;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return classes;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                classes.addAll(findClassesInDirectory(file, packageName + "." + file.getName()));
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + '.' + file.getName().substring(0, file.getName().length() - 6);
                try {
                    Class<?> clazz = Class.forName(className);
                    // 인터페이스, 어노테이션, 열거형, 추상 클래스 제외
                    if (!clazz.isInterface() && !clazz.isAnnotation() && !clazz.isEnum() &&
                            !Modifier.isAbstract(clazz.getModifiers())) {
                        classes.add(clazz);
                    }
                } catch (NoClassDefFoundError | ClassNotFoundException e) {
                    // 클래스를 로드할 수 없는 경우 무시
                    System.out.println("Warning: Could not load class " + className);
                }
            }
        }

        return classes;
    }
}
