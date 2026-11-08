package celeste.chataber.message;

import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.util.Objects;

/** 受信内容と分類済み経路を保持する履歴要素。 */
public record ChataberMessage(
        long id,
        Component component,
        MessageRoute route,
        Instant receivedAt
) {
    public ChataberMessage {
        Objects.requireNonNull(component, "component");
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(receivedAt, "receivedAt");
    }

    public MessageCategory category() {
        return route instanceof MessageRoute.Category categoryRoute
                ? categoryRoute.category()
                : null;
    }
}
