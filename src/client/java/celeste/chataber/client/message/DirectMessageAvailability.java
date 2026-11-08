package celeste.chataber.client.message;

import celeste.chataber.message.DirectMessageManager;
import celeste.chataber.message.DirectMessagePeer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.List;
import java.util.Optional;

/** 現在の接続からDM可能なオンラインプレイヤーを判定する。 */
public final class DirectMessageAvailability {
    private final Minecraft client;

    public DirectMessageAvailability(Minecraft client) {
        this.client = client;
    }

    public boolean isAvailable() {
        return !eligibleOnlinePlayers().isEmpty();
    }

    public List<PlayerInfo> eligibleOnlinePlayers() {
        if (client.getConnection() == null) {
            return List.of();
        }
        return client.getConnection().getOnlinePlayers().stream()
                .filter(player -> !client.isLocalPlayer(player.getProfile().id()))
                .filter(player -> !isSelfName(player.getProfile().name()))
                .toList();
    }

    public boolean isEligiblePeer(DirectMessagePeer peer) {
        return peer != null && !isSelfName(peer.playerName());
    }

    public boolean isEligiblePlayerName(String playerName) {
        return DirectMessageManager.isValidPlayerName(playerName) && !isSelfName(playerName);
    }

    public Optional<String> onlinePlayerName(String playerName) {
        if (!isEligiblePlayerName(playerName)) {
            return Optional.empty();
        }
        String normalized = playerName.strip();
        return eligibleOnlinePlayers().stream()
                .map(player -> player.getProfile().name())
                .filter(name -> name.equalsIgnoreCase(normalized))
                .findFirst();
    }

    public boolean isOnlinePlayerName(String playerName) {
        return onlinePlayerName(playerName).isPresent();
    }

    public boolean isSelfName(String playerName) {
        if (playerName == null) {
            return false;
        }
        String normalized = playerName.strip();
        if (client.player != null
                && client.player.getGameProfile().name().equalsIgnoreCase(normalized)) {
            return true;
        }
        return client.getGameProfile().name().equalsIgnoreCase(normalized);
    }
}
