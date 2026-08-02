package cn.elvis.monaco.plugin.runtime.spi;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PluginSpiBoundaryTest {

    @Test
    void publicSpiDoesNotReferenceRuntimeImplementationPackages() {
        List<Class<?>> spiTypes = List.of(
                HookInvocation.class,
                PluginCandidate.class,
                PluginInvoker.class,
                PluginLifecycle.class,
                PluginResource.class,
                PluginSource.class);
        List<String> violations = new ArrayList<>();

        for (Class<?> spiType : spiTypes) {
            for (var method : spiType.getDeclaredMethods()) {
                if (Modifier.isPublic(method.getModifiers())) {
                    inspect(spiType.getSimpleName() + '#' + method.getName(),
                            method.getGenericReturnType(), violations, new HashSet<>());
                    for (Type parameter : method.getGenericParameterTypes()) {
                        inspect(spiType.getSimpleName() + '#' + method.getName(),
                                parameter, violations, new HashSet<>());
                    }
                }
            }
            if (spiType.isRecord()) {
                for (var component : spiType.getRecordComponents()) {
                    inspect(spiType.getSimpleName() + '#' + component.getName(),
                            component.getGenericType(), violations, new HashSet<>());
                }
            }
        }

        assertEquals(List.of(), violations);
    }

    private static void inspect(
            String owner,
            Type type,
            List<String> violations,
            Set<Type> visited
    ) {
        if (type == null || !visited.add(type)) {
            return;
        }
        if (type instanceof Class<?> value) {
            Class<?> component = value;
            while (component.isArray()) {
                component = component.getComponentType();
            }
            if (!component.isPrimitive() && !approved(component.getName())) {
                violations.add(owner + " -> " + component.getName());
            }
        } else if (type instanceof ParameterizedType parameterized) {
            inspect(owner, parameterized.getRawType(), violations, visited);
            for (Type argument : parameterized.getActualTypeArguments()) {
                inspect(owner, argument, violations, visited);
            }
        } else if (type instanceof WildcardType wildcard) {
            for (Type bound : wildcard.getUpperBounds()) {
                inspect(owner, bound, violations, visited);
            }
            for (Type bound : wildcard.getLowerBounds()) {
                inspect(owner, bound, violations, visited);
            }
        } else if (type instanceof TypeVariable<?> variable) {
            for (Type bound : variable.getBounds()) {
                inspect(owner, bound, violations, visited);
            }
        }
    }

    private static boolean approved(String name) {
        return name.startsWith("java.")
                || name.startsWith("reactor.core.publisher.")
                || name.startsWith("cn.elvis.monaco.plugin.api.")
                || name.startsWith("cn.elvis.monaco.plugin.runtime.spi.");
    }
}
