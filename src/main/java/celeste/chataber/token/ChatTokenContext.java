package celeste.chataber.token;

import net.minecraft.core.BlockPos;

import java.util.Objects;
import java.util.Optional;

/** トークン展開時に利用できるクライアント側の文脈。 */
public record ChatTokenContext(Optional<BlockPos> position) {
    public ChatTokenContext {
        Objects.requireNonNull(position, "position");
    }

    public static ChatTokenContext unavailable() {
        return new ChatTokenContext(Optional.empty());
    }

    public static ChatTokenContext at(BlockPos position) {
        return new ChatTokenContext(Optional.of(position));
    }
}
