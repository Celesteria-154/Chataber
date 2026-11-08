package celeste.chataber.message;

import java.util.Locale;
import java.util.Objects;

/** メッセージの表示先を通常カテゴリまたはDM会話として表す。 */
public sealed interface MessageRoute permits MessageRoute.Category, MessageRoute.DirectMessage {
    record Category(MessageCategory category) implements MessageRoute {
        public Category {
            Objects.requireNonNull(category, "category");
        }
    }

    record DirectMessage(String peerId, Direction direction) implements MessageRoute {
        public DirectMessage {
            peerId = normalizePeerId(peerId);
            Objects.requireNonNull(direction, "direction");
        }
    }

    enum Direction {
        INCOMING,
        OUTGOING
    }

    static Category category(MessageCategory category) {
        return new Category(category);
    }

    static DirectMessage directMessage(String peerId, Direction direction) {
        return new DirectMessage(peerId, direction);
    }

    static String normalizePeerId(String playerName) {
        Objects.requireNonNull(playerName, "playerName");
        String normalized = playerName.strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("playerName must not be blank");
        }
        return normalized;
    }
}
