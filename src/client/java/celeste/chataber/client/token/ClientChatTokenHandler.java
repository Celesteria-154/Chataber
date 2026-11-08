package celeste.chataber.client.token;

import celeste.chataber.token.ChatTokenContext;
import celeste.chataber.token.ChatTokenProcessor;
import celeste.chataber.token.ChatMessageLengthValidator;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** 通常チャットのトークン展開と送信文字数の検証。 */
public final class ClientChatTokenHandler {
    private final Minecraft client;
    private final ChatTokenProcessor processor;
    // ALLOWとMODIFYで同じ変換を二度行わないよう、送信スレッド内だけ結果を保持する。
    private final ThreadLocal<PendingTransformation> pending = new ThreadLocal<>();

    public ClientChatTokenHandler(Minecraft client, ChatTokenProcessor processor) {
        this.client = client;
        this.processor = processor;
    }

    public void register() {
        ClientSendMessageEvents.ALLOW_CHAT.register(this::allowChat);
        ClientSendMessageEvents.MODIFY_CHAT.register(this::modifyChat);
        ClientSendMessageEvents.CHAT_CANCELED.register(message -> pending.remove());
    }

    private boolean allowChat(String message) {
        String transformed = processor.process(message, currentContext());
        if (!ChatMessageLengthValidator.isWithinVanillaLimit(transformed)) {
            pending.remove();
            showTooLongError();
            return false;
        }
        pending.set(new PendingTransformation(message, transformed));
        return true;
    }

    private String modifyChat(String message) {
        PendingTransformation transformation = pending.get();
        pending.remove();
        if (transformation != null && transformation.original().equals(message)) {
            return transformation.transformed();
        }
        return processor.process(message, currentContext());
    }

    private ChatTokenContext currentContext() {
        if (client.player == null || client.level == null) {
            return ChatTokenContext.unavailable();
        }
        return ChatTokenContext.at(client.player.blockPosition());
    }

    private void showTooLongError() {
        if (client.player != null) {
            client.player.sendOverlayMessage(Component.translatable("chataber.error.message_too_long"));
        }
    }

    private record PendingTransformation(String original, String transformed) {
    }
}
