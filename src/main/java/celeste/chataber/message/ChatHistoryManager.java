package celeste.chataber.message;

import net.minecraft.network.chat.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** 受信順を保った上限付きチャット履歴を管理する。 */
public final class ChatHistoryManager {
    public static final int DEFAULT_MAX_MESSAGES = 100;

    private final int maxMessages;
    private final Clock clock;
    private final AtomicLong nextId = new AtomicLong();
    private final List<ChataberMessage> messages = new ArrayList<>();

    public ChatHistoryManager() {
        this(DEFAULT_MAX_MESSAGES, Clock.systemUTC());
    }

    ChatHistoryManager(int maxMessages, Clock clock) {
        if (maxMessages < 1) {
            throw new IllegalArgumentException("maxMessages must be positive");
        }
        this.maxMessages = maxMessages;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized ChataberMessage add(Component component, MessageCategory category) {
        return add(component, MessageRoute.category(category), Instant.now(clock));
    }

    public synchronized ChataberMessage add(Component component, MessageCategory category, Instant receivedAt) {
        return add(component, MessageRoute.category(category), receivedAt);
    }

    public synchronized ChataberMessage add(Component component, MessageRoute route) {
        return add(component, route, Instant.now(clock));
    }

    public synchronized ChataberMessage add(Component component, MessageRoute route, Instant receivedAt) {
        ChataberMessage message = new ChataberMessage(
                nextId.getAndIncrement(),
                component,
                route,
                receivedAt
        );
        messages.add(message);
        // 上限はカテゴリ別ではなく全受信で共有し、最も古い1件を破棄
        if (messages.size() > maxMessages) {
            messages.removeFirst();
        }
        return message;
    }

    public synchronized void clear() {
        messages.clear();
        nextId.set(0);
    }
}
