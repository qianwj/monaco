package cn.elvis.monaco.plugin.contract;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicApiBoundaryTest {

    private static final String API_PATH = "cn/elvis/monaco/plugin/api";
    private static final String API_PACKAGE = "cn.elvis.monaco.plugin.api";

    @Test
    void publicSignaturesOnlyUseApprovedDependencies() throws Exception {
        Set<Class<?>> apiClasses = discoverApiClasses();
        assertFalse(apiClasses.isEmpty(), "Boundary test did not discover any plugin API types");

        List<String> violations = new ArrayList<>();
        for (Class<?> apiClass : apiClasses) {
            if (!Modifier.isPublic(apiClass.getModifiers())) {
                continue;
            }
            inspectClass(apiClass, violations);
        }
        assertTrue(violations.isEmpty(),
                () -> "Disallowed plugin API signatures:\n" + String.join("\n", violations));
    }

    private static void inspectClass(Class<?> apiClass, List<String> violations) {
        inspectTypes(apiClass.getName(), List.of(apiClass.getGenericInterfaces()), violations);
        inspectType(apiClass.getName(), apiClass.getGenericSuperclass(), violations, new HashSet<>());

        for (Field field : apiClass.getDeclaredFields()) {
            if (Modifier.isPublic(field.getModifiers()) || Modifier.isProtected(field.getModifiers())) {
                inspectType(apiClass.getName() + '#' + field.getName(), field.getGenericType(),
                        violations, new HashSet<>());
            }
        }
        for (Constructor<?> constructor : apiClass.getDeclaredConstructors()) {
            if (Modifier.isPublic(constructor.getModifiers()) || Modifier.isProtected(constructor.getModifiers())) {
                inspectTypes(apiClass.getName() + " constructor", List.of(constructor.getGenericParameterTypes()),
                        violations);
            }
        }
        for (Method method : apiClass.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers()) || Modifier.isProtected(method.getModifiers())) {
                String owner = apiClass.getName() + '#' + method.getName();
                inspectType(owner, method.getGenericReturnType(), violations, new HashSet<>());
                inspectTypes(owner, List.of(method.getGenericParameterTypes()), violations);
                inspectTypes(owner, List.of(method.getGenericExceptionTypes()), violations);
            }
        }
        if (apiClass.isRecord()) {
            for (RecordComponent component : apiClass.getRecordComponents()) {
                inspectType(apiClass.getName() + '#' + component.getName(), component.getGenericType(),
                        violations, new HashSet<>());
            }
        }
    }

    private static void inspectTypes(String owner, List<Type> types, List<String> violations) {
        for (Type type : types) {
            inspectType(owner, type, violations, new HashSet<>());
        }
    }

    private static void inspectType(
            String owner,
            Type type,
            List<String> violations,
            Set<Type> visited
    ) {
        if (type == null || !visited.add(type)) {
            return;
        }
        if (type instanceof Class<?> typeClass) {
            Class<?> component = typeClass;
            while (component.isArray()) {
                component = component.getComponentType();
            }
            if (!component.isPrimitive() && !approved(component.getName())) {
                violations.add(owner + " -> " + component.getName());
            }
        } else if (type instanceof ParameterizedType parameterized) {
            inspectType(owner, parameterized.getRawType(), violations, visited);
            inspectType(owner, parameterized.getOwnerType(), violations, visited);
            for (Type argument : parameterized.getActualTypeArguments()) {
                inspectType(owner, argument, violations, visited);
            }
        } else if (type instanceof GenericArrayType array) {
            inspectType(owner, array.getGenericComponentType(), violations, visited);
        } else if (type instanceof WildcardType wildcard) {
            for (Type bound : wildcard.getUpperBounds()) {
                inspectType(owner, bound, violations, visited);
            }
            for (Type bound : wildcard.getLowerBounds()) {
                inspectType(owner, bound, violations, visited);
            }
        } else if (type instanceof TypeVariable<?> variable) {
            for (Type bound : variable.getBounds()) {
                inspectType(owner, bound, violations, visited);
            }
        }
    }

    private static boolean approved(String className) {
        return className.startsWith("java.")
                || className.startsWith(API_PACKAGE + '.')
                || className.startsWith("cn.elvis.monaco.protocol.")
                || className.startsWith("reactor.core.publisher.");
    }

    private static Set<Class<?>> discoverApiClasses() throws Exception {
        Set<Class<?>> classes = new HashSet<>();
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> resources = loader.getResources(API_PATH);
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            if ("file".equals(resource.getProtocol())) {
                discoverFileClasses(resource, loader, classes);
            }
        }
        return classes;
    }

    private static void discoverFileClasses(
            URL resource,
            ClassLoader loader,
            Set<Class<?>> classes
    ) throws Exception {
        Path root;
        try {
            root = Path.of(resource.toURI());
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("Invalid plugin API classpath URL", exception);
        }
        try (var files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".class"))
                    .forEach(path -> classes.add(loadClass(root, path, loader)));
        }
    }

    private static Class<?> loadClass(Path root, Path classFile, ClassLoader loader) {
        String relative = root.relativize(classFile).toString();
        String suffix = relative.substring(0, relative.length() - ".class".length())
                .replace(java.io.File.separatorChar, '.');
        try {
            return Class.forName(API_PACKAGE + '.' + suffix, false, loader);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Cannot load plugin API class " + suffix, exception);
        }
    }
}
