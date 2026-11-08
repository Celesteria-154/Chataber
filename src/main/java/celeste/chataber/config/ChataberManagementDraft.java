package celeste.chataber.config;

import celeste.chataber.message.DirectMessageManager;
import celeste.chataber.message.DirectMessagePeer;
import celeste.chataber.tab.ChatTabManager;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** タブ・DM管理画面の編集用コピー。 */
public final class ChataberManagementDraft {
    private final ChatTabManager tabs = new ChatTabManager();
    private final DirectMessageManager directMessages = new DirectMessageManager();
    private final Set<String> deletedPeerIds = new LinkedHashSet<>();
    private final Set<String> persistentHudTargets = new LinkedHashSet<>();

    public ChataberManagementDraft(
            ChatTabManager liveTabs,
            DirectMessageManager liveDirectMessages,
            ChatSettings liveSettings
    ) {
        Objects.requireNonNull(liveTabs, "liveTabs");
        Objects.requireNonNull(liveDirectMessages, "liveDirectMessages");
        Objects.requireNonNull(liveSettings, "liveSettings");
        // 編集はコピー側で行い、キャンセル時はライブ状態へ反映しない
        tabs.replaceAll(liveTabs.tabs(), liveTabs.selectedTabId());
        directMessages.applyPreferences(liveDirectMessages.allPeerPreferences());
        persistentHudTargets.addAll(liveSettings.state().persistentHudTargets());
    }

    public ChatTabManager tabs() {
        return tabs;
    }

    public DirectMessageManager directMessages() {
        return directMessages;
    }

    public DirectMessagePeer ensurePeer(String playerName) {
        DirectMessagePeer peer = directMessages.ensurePeer(playerName);
        deletedPeerIds.remove(peer.id());
        return peer;
    }

    public boolean deletePeer(String peerId) {
        DirectMessagePeer peer = directMessages.find(peerId).orElse(null);
        if (peer == null) {
            return false;
        }
        // 設定の上書きだけでは相手が残るため、明示的な削除を別に記録
        deletedPeerIds.add(peer.id());
        persistentHudTargets.remove("dm:" + peer.id());
        directMessages.removePeers(candidate -> candidate.id().equals(peer.id()));
        return true;
    }

    public boolean persistentHudNotifications(String targetId) {
        return persistentHudTargets.contains(targetId);
    }

    public void setPersistentHudNotifications(String targetId, boolean persistent) {
        if (persistent) {
            persistentHudTargets.add(targetId);
        } else {
            persistentHudTargets.remove(targetId);
        }
    }

    public void applyTo(
            ChatTabManager liveTabs,
            DirectMessageManager liveDirectMessages,
            ChatSettings liveSettings
    ) {
        liveTabs.replaceAll(tabs.tabs(), liveTabs.selectedTabId());
        liveDirectMessages.applyPreferences(directMessages.allPeerPreferences());
        liveDirectMessages.removePeers(peer -> deletedPeerIds.contains(peer.id()));
        // この画面で扱わない一般設定は、確定時の値を引き継ぐ
        ChatSettings.State current = liveSettings.state();
        liveSettings.apply(new ChatSettings.State(
                current.dmGroupPlacement(),
                current.dmAutoSend(),
                current.dmCommandTemplate(),
                current.hudNotificationSeconds(),
                // 削除したタブ・DM相手の常時表示設定は保存しない
                persistentHudTargets.stream()
                        .filter(target -> target.startsWith("tab:")
                                ? tabs.findById(target.substring(4)).isPresent()
                                : target.startsWith("dm:")
                                        && directMessages.find(target.substring(3)).isPresent())
                        .collect(java.util.stream.Collectors.toSet()),
                current.badgeStyle(),
                current.maxVisibleDmTabs(),
                current.hereFormat()
        ));
    }
}
