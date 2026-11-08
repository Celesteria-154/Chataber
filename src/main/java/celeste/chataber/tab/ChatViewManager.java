package celeste.chataber.tab;

import celeste.chataber.message.DirectMessageManager;
import celeste.chataber.message.MessageRoute;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 通常タブとDM会話の表示切り替え。 */
public final class ChatViewManager {
    private final ChatTabManager tabs;
    private final DirectMessageManager directMessages;
    private final List<Runnable> listeners = new ArrayList<>();
    private SelectedChatView selectedView;

    public ChatViewManager(ChatTabManager tabs, DirectMessageManager directMessages) {
        this.tabs = Objects.requireNonNull(tabs, "tabs");
        this.directMessages = Objects.requireNonNull(directMessages, "directMessages");
        this.selectedView = new SelectedChatView.Tab(tabs.selectedTabId());
    }

    public SelectedChatView selectedView() {
        return selectedView;
    }

    public boolean selectTab(String tabId) {
        if (tabs.findById(tabId).isEmpty()) {
            return false;
        }
        boolean viewChanged = !(selectedView instanceof SelectedChatView.Tab tab) || !tab.tabId().equals(tabId);
        tabs.select(tabId);
        selectedView = new SelectedChatView.Tab(tabId);
        // Allは通常カテゴリとDMの両方を表示するため、未読もまとめて解除
        if (ChatTabManager.ALL_TAB_ID.equals(tabId)) {
            tabs.clearAllUnread();
            directMessages.clearAllUnread();
        } else {
            tabs.clearUnread(tabId);
            tabs.findById(tabId)
                    .filter(ChatTab::includeDirectMessages)
                    .ifPresent(ignored -> directMessages.clearAllUnread());
        }
        if (viewChanged) {
            notifyChanged();
        }
        return viewChanged;
    }

    public boolean selectDirectMessage(String peerId) {
        if (!directMessages.select(peerId)) {
            if (directMessages.find(peerId).isEmpty()) {
                return false;
            }
        }
        String normalized = MessageRoute.normalizePeerId(peerId);
        boolean changed = !(selectedView instanceof SelectedChatView.DirectMessage direct)
                || !direct.peerId().equals(normalized);
        selectedView = new SelectedChatView.DirectMessage(normalized);
        directMessages.clearUnread(normalized);
        if (changed) {
            notifyChanged();
        }
        return changed;
    }

    public boolean isVisible(MessageRoute route) {
        if (selectedView instanceof SelectedChatView.Tab selectedTab) {
            if (ChatTabManager.ALL_TAB_ID.equals(selectedTab.tabId())) {
                return true;
            }
            ChatTab tab = tabs.findById(selectedTab.tabId()).orElse(tabs.selectedTab());
            return tab.matches(route);
        }
        // DMビューでは、送受信の方向に関係なく同じ相手の会話を表示
        SelectedChatView.DirectMessage selectedDm = (SelectedChatView.DirectMessage) selectedView;
        return route instanceof MessageRoute.DirectMessage direct
                && selectedDm.peerId().equals(direct.peerId());
    }

    /** 現在のビューで見えていない受信だけを未読へ加算する。送信DMは対象外。 */
    public void recordUnread(MessageRoute route) {
        if (isVisible(route)) {
            return;
        }
        if (route instanceof MessageRoute.Category category) {
            tabs.incrementUnread(category.category());
        } else if (route instanceof MessageRoute.DirectMessage direct
                && direct.direction() == MessageRoute.Direction.INCOMING) {
            tabs.incrementUnread(route);
            directMessages.incrementUnread(direct.peerId());
        }
    }

    public void resetToConfiguredTab() {
        selectedView = new SelectedChatView.Tab(tabs.selectedTabId());
        notifyChanged();
    }

    public void synchronizeTabs() {
        if (selectedView instanceof SelectedChatView.Tab selected
                && tabs.findById(selected.tabId()).isEmpty()) {
            resetToConfiguredTab();
        }
    }

    public void addListener(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    private void notifyChanged() {
        for (Runnable listener : List.copyOf(listeners)) {
            listener.run();
        }
    }
}
