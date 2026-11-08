package celeste.chataber.message;

import java.util.Objects;

/** 設定ファイルへ保存するDM相手の表示設定。 */
public record DirectMessagePeerPreference(
        String playerName,
        String displayName,
        boolean pinned,
        boolean listed,
        boolean positionFixed,
        int displayOrder
) {
    public DirectMessagePeerPreference {
        Objects.requireNonNull(playerName, "playerName");
        playerName = playerName.strip();
        if (playerName.isEmpty()) {
            throw new IllegalArgumentException("playerName must not be blank");
        }
        displayName = displayName == null || displayName.isBlank() ? playerName : displayName.strip();
    }

    public String id() {
        return MessageRoute.normalizePeerId(playerName);
    }
}
