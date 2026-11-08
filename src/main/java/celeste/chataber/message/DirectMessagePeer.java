package celeste.chataber.message;

import java.util.Objects;

/** 表示に使うDM相手の不変スナップショット。 */
public record DirectMessagePeer(
        String id,
        String playerName,
        String displayName,
        boolean pinned,
        boolean listed,
        boolean positionFixed,
        int displayOrder,
        long lastActivity,
        int unreadCount
) {
    public DirectMessagePeer {
        id = MessageRoute.normalizePeerId(id);
        playerName = requireText(playerName, "playerName");
        displayName = requireText(displayName, "displayName");
        if (unreadCount < 0) {
            throw new IllegalArgumentException("unreadCount must not be negative");
        }
    }

    public String shownName() {
        return displayName.isBlank() ? playerName : displayName;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
