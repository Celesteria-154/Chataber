package celeste.chataber.token;

import net.minecraft.util.StringUtil;

/** Vanillaのチャット長制限内で送信できる文かを判定する。 */
public final class ChatMessageLengthValidator {
    private ChatMessageLengthValidator() {
    }

    public static boolean isWithinVanillaLimit(String message) {
        return StringUtil.trimChatMessage(message).equals(message);
    }
}
