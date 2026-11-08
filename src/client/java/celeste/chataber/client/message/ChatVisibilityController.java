package celeste.chataber.client.message;

import celeste.chataber.message.MessageClassifier;
import celeste.chataber.message.MessageRoute;
import celeste.chataber.tab.ChatViewManager;
import celeste.chataber.tab.SelectedChatView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;

/** 選択ビューに合わせてVanillaチャットの表示フィルターを更新する。 */
public final class ChatVisibilityController {
    private final Minecraft client;
    private final ChatViewManager views;
    private final MessageClassifier classifier;

    public ChatVisibilityController(Minecraft client, ChatViewManager views, MessageClassifier classifier) {
        this.client = client;
        this.views = views;
        this.classifier = classifier;
    }

    public void apply() {
        ClientMessageRouting.setDirectMessageView(
                views.selectedView() instanceof SelectedChatView.DirectMessage direct
                        ? direct.peerId()
                        : null
        );
        ChatComponent chat = client.gui.hud.getChat();
        // 受信履歴は変更せず、選択ビューに合う行だけを再表示
        chat.setVisibleMessageFilter(this::isVisible);
        chat.resetChatScroll();
    }

    private boolean isVisible(GuiMessage message) {
        // 経路が付いていないメッセージは、Vanillaの送信元と内容から分類
        MessageRoute route = ClientMessageRouting.routeFor(message).orElseGet(() ->
                MessageRoute.category(message.source() == GuiMessageSource.PLAYER
                        ? celeste.chataber.message.MessageCategory.PLAYER_CHAT
                        : classifier.classifyGameMessage(message.content())));
        return views.isVisible(route);
    }
}
