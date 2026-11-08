package celeste.chataber.message;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.Objects;

/** ゲームメッセージの内容から表示カテゴリを判定する。 */
public final class MessageClassifier {
    private static final String COMMAND_KEY_PREFIX = "commands.";
    private static final String ADMIN_CHAT_KEY = "chat.type.admin";

    public MessageCategory classifyPlayerChat(Component component) {
        Objects.requireNonNull(component, "component");
        return MessageCategory.PLAYER_CHAT;
    }

    public MessageCategory classifyGameMessage(Component component) {
        Objects.requireNonNull(component, "component");

        if (component.getContents() instanceof TranslatableContents translatable) {
            String key = translatable.getKey();
            if (key.startsWith(COMMAND_KEY_PREFIX) || key.equals(ADMIN_CHAT_KEY)) {
                return MessageCategory.COMMAND_FEEDBACK;
            }
        }

        return MessageCategory.SYSTEM;
    }
}
