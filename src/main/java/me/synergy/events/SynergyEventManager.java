package me.synergy.events;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import me.synergy.anotations.SynergyHandler;
import me.synergy.anotations.SynergyListener;
import me.synergy.brains.Synergy;

public class SynergyEventManager {
    private static final Set<SynergyListener> listeners = new HashSet<>();

    public void registerEvents(SynergyListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
            Synergy.getLogger().info(listener.getClass().getSimpleName() + " event has been registered!");
        }
    }

    public void fireEvent(SynergyEvent event) {

        for (SynergyListener listener : listeners) {
            CompletableFuture.runAsync(() -> {
                try {
                    Method[] methods = listener.getClass().getDeclaredMethods();
                    for (Method method : methods) {
                        if (method.isAnnotationPresent(SynergyHandler.class)) {
                            Class<?>[] params = method.getParameterTypes();
                            if (params.length == 1 && params[0].isAssignableFrom(event.getClass())) {
                                try {
                                    method.invoke(listener, event);
                                } catch (IllegalAccessException | InvocationTargetException e) {
                                    Synergy.getLogger().error("[SynergyEventManager] Error invoking handler in " + listener.getClass().getSimpleName() + "." + method.getName() + ": " + e.getMessage());
                                    e.printStackTrace();
                                    if (e.getCause() != null) {
                                        Synergy.getLogger().error("[SynergyEventManager] Caused by: " + e.getCause().getMessage());
                                        e.getCause().printStackTrace();
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Synergy.getLogger().error("[SynergyEventManager] Unexpected error in async event handler: " + e.getMessage());
                    e.printStackTrace();
                }
            }).exceptionally(throwable -> {
                Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                Synergy.getLogger().error("[SynergyEventManager] CompletableFuture exception for event " + event.getIdentifier() + ": " + cause.toString());
                cause.printStackTrace();
                if (cause.getStackTrace().length > 0) {
                    StackTraceElement top = cause.getStackTrace()[0];
                    Synergy.getLogger().error("Error originated at: " 
                        + top.getClassName() + "." + top.getMethodName() 
                        + " (line: " + top.getLineNumber() + ")");
                }
                throwable.printStackTrace();
                return null;
            });
        }
    }

    public static SynergyEventManager getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        private static final SynergyEventManager INSTANCE = new SynergyEventManager();
    }
}