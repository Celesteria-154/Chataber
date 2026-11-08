package celeste.chataber.tab;

import celeste.chataber.message.MessageRoute;

import java.util.Objects;

/** 現在表示している通常タブまたはDM会話を表す。 */
public sealed interface SelectedChatView permits SelectedChatView.Tab, SelectedChatView.DirectMessage {
    record Tab(String tabId) implements SelectedChatView {
        public Tab {
            Objects.requireNonNull(tabId, "tabId");
        }
    }

    record DirectMessage(String peerId) implements SelectedChatView {
        public DirectMessage {
            peerId = MessageRoute.normalizePeerId(peerId);
        }
    }
}
