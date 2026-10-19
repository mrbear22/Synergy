package me.synergy.events;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import me.synergy.anotations.SynergyHandler;
import me.synergy.anotations.SynergyListener;
import me.synergy.brains.Synergy;
import me.synergy.modules.Tasks;

public class SynergyEventManager {

    private static final Map<Object, List<Method>> listeners = new ConcurrentHashMap<>();

    public void registerEvents(SynergyListener listener) {
        register(listener, listener.getClass());
    }

    public void registerEvents(Class<?> listener) {
        register(listener, listener);
    }

    private void register(Object listener, Class<?> type) {
        boolean instance = !(listener instanceof Class<?>);
        List<Method> handlers;
        try {
            handlers = Arrays.stream(type.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(SynergyHandler.class) && instance != Modifier.isStatic(m.getModifiers()))
                .filter(m -> m.getParameterCount() == 1 && m.getParameterTypes()[0].isAssignableFrom(SynergyEvent.class))
                .peek(m -> m.setAccessible(true))
                .toList();
        } catch (LinkageError e) {
            Synergy.getLogger().warning(type.getSimpleName() + " is disabled: missing " + e.getMessage());
            return;
        }
        listeners.putIfAbsent(listener, handlers);
    }

    public void fireEvent(SynergyEvent event) {
        listeners.forEach((listener, handlers) -> {
            Object target = listener instanceof Class<?> ? null : listener;
            for (Method handler : handlers) {
                if (!accepts(handler, event.getIdentifier())) continue;
                Tasks.async(() -> {
                    try {
                        handler.invoke(target, event);
                    } catch (InvocationTargetException | IllegalAccessException e) {
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        Synergy.getLogger().error(handler.getDeclaringClass().getSimpleName() + "." + handler.getName() + " failed on " + event.getIdentifier() + ": " + cause, cause);
                    }
                });
            }
        });
    }

    private static boolean accepts(Method handler, String identifier) {
        String[] ids = handler.getAnnotation(SynergyHandler.class).value();
        if (ids.length == 0) return true;
        for (String id : ids) {
            if (id.equals(identifier) || id.contains("*") && identifier.matches(Pattern.quote(id).replace("*", "\\E.*\\Q"))) return true;
        }
        return false;
    }

    public static SynergyEventManager getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        private static final SynergyEventManager INSTANCE = new SynergyEventManager();
    }
}
