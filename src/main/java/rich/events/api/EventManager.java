package rich.events.api;

import rich.events.api.events.Event;
import rich.events.api.events.EventStoppable;
import rich.events.api.types.Priority;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class EventManager {

    /**
     * Concurrent on purpose. {@link #callEvent} reads this map from the render and client
     * threads every frame, while register/unregister structurally modify it whenever a
     * module is toggled or a config is reloaded. A plain HashMap can return null for a
     * present key while another thread is resizing it, which silently drops every handler
     * of that event until the next registration.
     */
    private static final Map<Class<? extends Event>, List<MethodData>> REGISTRY_MAP = new ConcurrentHashMap<>();

    /** Handlers that already reported a failure, so a broken one cannot spam the console every frame. */
    private static final Set<String> REPORTED_FAILURES = ConcurrentHashMap.newKeySet();

    public EventManager() {}

    public static void register(Object object) {
        for (final Method method : object.getClass().getDeclaredMethods()) {
            if (isMethodBad(method)) {
                continue;
            }

            register(method, object);
        }
    }

    public static void register(Object object, Class<? extends Event> eventClass) {
        for (final Method method : object.getClass().getDeclaredMethods()) {
            if (isMethodBad(method, eventClass)) {
                continue;
            }

            register(method, object);
        }
    }

    public static void unregister(Object object) {
        for (final List<MethodData> dataList : REGISTRY_MAP.values()) {
            dataList.removeIf(data -> data.source().equals(object));
        }

        cleanMap(true);
    }

    public static void unregister(Object object, Class<? extends Event> eventClass) {
        final List<MethodData> dataList = REGISTRY_MAP.get(eventClass);

        if (dataList != null) {
            dataList.removeIf(data -> data.source().equals(object));
            cleanMap(true);
        }
    }

    private static void register(Method method, Object object) {
        @SuppressWarnings("unchecked")
        final Class<? extends Event> indexClass = (Class<? extends Event>) method.getParameterTypes()[0];
        final MethodData data = new MethodData(object, method, method.getAnnotation(EventHandler.class).value());

        if (!data.target().canAccess(data.source())) {
            data.target().setAccessible(true);
        }

        final List<MethodData> dataList = REGISTRY_MAP.computeIfAbsent(indexClass, key -> new CopyOnWriteArrayList<>());

        if (dataList.contains(data)) {
            return;
        }

        dataList.add(data);
        sortListValue(indexClass);
    }

    public static void removeEntry(Class<? extends Event> indexClass) {
        REGISTRY_MAP.remove(indexClass);
    }

    public static void cleanMap(boolean onlyEmptyEntries) {
        if (onlyEmptyEntries) {
            REGISTRY_MAP.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        } else {
            REGISTRY_MAP.clear();
        }
    }

    private static void sortListValue(Class<? extends Event> indexClass) {
        final List<MethodData> current = REGISTRY_MAP.get(indexClass);

        if (current == null) {
            return;
        }

        final List<MethodData> sorted = new ArrayList<>(current.size());

        for (final byte priority : Priority.VALUE_ARRAY) {
            for (final MethodData data : current) {
                if (data.priority() == priority) {
                    sorted.add(data);
                }
            }
        }

        REGISTRY_MAP.put(indexClass, new CopyOnWriteArrayList<>(sorted));
    }

    private static boolean isMethodBad(Method method) {
        return method.getParameterTypes().length != 1 || !method.isAnnotationPresent(EventHandler.class);
    }

    private static boolean isMethodBad(Method method, Class<? extends Event> eventClass) {
        return isMethodBad(method) || !method.getParameterTypes()[0].equals(eventClass);
    }

    public static Event callEvent(final Event event) {
        final List<MethodData> dataList = REGISTRY_MAP.get(event.getClass());

        if (dataList == null) {
            return event;
        }

        if (event instanceof EventStoppable stoppable) {
            for (final MethodData data : dataList) {
                invoke(data, event);

                if (stoppable.isStopped()) {
                    break;
                }
            }
        } else {
            for (final MethodData data : dataList) {
                invoke(data, event);
            }
        }

        return event;
    }

    private static void invoke(final MethodData data, final Event argument) {
        try {
            data.target().invoke(data.source(), argument);
        } catch (InvocationTargetException e) {
            reportOnce(data, e.getCause());
        } catch (IllegalAccessException | IllegalArgumentException e) {
            reportOnce(data, e);
        }
    }

    /**
     * Reports the first failure of a handler and stays silent afterwards. The previous
     * implementation rebuilt an error string with +=, called fillInStackTrace() and wrote
     * to System.out on every dispatch, so a single throwing handler cost a full stack walk
     * plus a synchronized console write every frame.
     */
    private static void reportOnce(final MethodData data, final Throwable cause) {
        final String key = data.source().getClass().getName() + '#' + data.target().getName();

        if (!REPORTED_FAILURES.add(key)) {
            return;
        }

        System.err.println("[EventManager] handler failed, further reports suppressed: " + key);

        if (cause != null) {
            cause.printStackTrace();
        }
    }

    private record MethodData(Object source, Method target, byte priority) {}
}
