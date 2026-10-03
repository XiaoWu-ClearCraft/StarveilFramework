package com.xiaowu.game.starveil.game.ecs;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 事件总线：按事件类型同步分发。发布发生在调用线程，订阅者被直接执行。
 */
public final class EventBus {
    private final Map<Class<?>, List<Consumer<?>>> subscribers = new IdentityHashMap<>();

    public <T> Subscription subscribe(Class<T> type, Consumer<T> consumer) {
        List<Consumer<?>> list = subscribers.computeIfAbsent(type, k -> new ArrayList<>());
        list.add(consumer);
        return new Subscription(this, type, consumer);
    }

    @SuppressWarnings("unchecked")
    public <T> void publish(T event) {
        List<Consumer<?>> list = subscribers.get(event.getClass());
        if (list == null || list.isEmpty()) {
            return;
        }
        for (Consumer<?> consumer : new ArrayList<>(list)) {
            ((Consumer<T>) consumer).accept(event);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    void unsubscribe(Class<?> type, Consumer<?> consumer) {
        List<Consumer<?>> list = subscribers.get(type);
        if (list != null) {
            list.remove((Consumer) consumer);
        }
    }

    public static final class Subscription {
        private final EventBus bus;
        private final Class<?> type;
        private final Consumer<?> consumer;
        private boolean active = true;

        Subscription(EventBus bus, Class<?> type, Consumer<?> consumer) {
            this.bus = bus;
            this.type = type;
            this.consumer = consumer;
        }

        public void cancel() {
            if (active) {
                active = false;
                bus.unsubscribe(type, consumer);
            }
        }
    }
}
