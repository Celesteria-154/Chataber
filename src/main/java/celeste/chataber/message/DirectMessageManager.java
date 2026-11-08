package celeste.chataber.message;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** DM相手の設定、活動順、未読数、現在選択を一元管理する。 */
public final class DirectMessageManager {
    private static final Pattern VANILLA_PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private final Map<String, MutablePeer> peers = new LinkedHashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private long activitySequence;
    private String currentPeerId;
    private String pendingOutgoingPeerId;

    public synchronized DirectMessagePeer observe(
            String playerName,
            String displayName
    ) {
        String id = MessageRoute.normalizePeerId(playerName);
        MutablePeer peer = peers.computeIfAbsent(id,
                ignored -> new MutablePeer(playerName.strip(), normalizedDisplayName(displayName, playerName)));
        peer.playerName = playerName.strip();
        // 既定名のままなら受信側の表示名を採用。設定済みの別名は維持
        if (peer.displayName.equals(peer.playerName) && displayName != null && !displayName.isBlank()) {
            peer.displayName = displayName.strip();
        }
        peer.lastActivity = ++activitySequence;
        notifyChanged();
        return peer.snapshot(id);
    }

    public synchronized DirectMessagePeer ensurePeer(String playerName) {
        if (!isValidPlayerName(playerName)) {
            throw new IllegalArgumentException("Invalid Vanilla player name");
        }
        String normalized = playerName.strip();
        String id = MessageRoute.normalizePeerId(normalized);
        MutablePeer peer = peers.computeIfAbsent(id, ignored -> new MutablePeer(normalized, normalized));
        peer.listed = true;
        notifyChanged();
        return peer.snapshot(id);
    }

    public synchronized Optional<DirectMessagePeer> find(String peerId) {
        MutablePeer peer = peers.get(MessageRoute.normalizePeerId(peerId));
        return peer == null ? Optional.empty() : Optional.of(peer.snapshot(MessageRoute.normalizePeerId(peerId)));
    }

    public synchronized List<DirectMessagePeer> visiblePeers() {
        return peers.entrySet().stream()
                .filter(entry -> entry.getValue().listed || entry.getValue().pinned)
                .map(entry -> entry.getValue().snapshot(entry.getKey()))
                // ピン留めを先頭にし、固定位置は表示順、残りは最近の活動順で並べる。
                .sorted(Comparator
                        .comparing(DirectMessagePeer::pinned).reversed()
                        .thenComparingInt(peer -> peer.positionFixed() ? 0 : 1)
                        .thenComparingInt(peer -> peer.positionFixed() ? peer.displayOrder() : 0)
                        .thenComparing(Comparator.comparingLong(
                                (DirectMessagePeer peer) -> peer.positionFixed()
                                        ? Long.MIN_VALUE
                                        : peer.lastActivity()).reversed())
                        .thenComparing(DirectMessagePeer::shownName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public synchronized List<DirectMessagePeer> allPeers() {
        return peers.entrySet().stream()
                .map(entry -> entry.getValue().snapshot(entry.getKey()))
                .sorted(Comparator.comparing(DirectMessagePeer::shownName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** 保存対象は明示的に設定した相手だけ。受信で見つけた相手はセッション内で扱う。 */
    public synchronized List<DirectMessagePeerPreference> preferences() {
        return peers.values().stream()
                .filter(peer -> peer.configured)
                .map(peer -> new DirectMessagePeerPreference(
                        peer.playerName,
                        peer.displayName,
                        peer.pinned,
                        peer.listed,
                        peer.positionFixed,
                        peer.displayOrder
                ))
                .toList();
    }

    /** 管理画面のコピーには、セッション中に見つけた相手も含める。 */
    public synchronized List<DirectMessagePeerPreference> allPeerPreferences() {
        return peers.values().stream()
                .map(peer -> new DirectMessagePeerPreference(
                        peer.playerName,
                        peer.displayName,
                        peer.pinned,
                        peer.listed,
                        peer.positionFixed,
                        peer.displayOrder
                ))
                .toList();
    }

    public synchronized void applyPreferences(List<DirectMessagePeerPreference> preferences) {
        // 実行中に見つけた相手は残し、永続化される表示設定だけを初期化して重ねる。
        for (MutablePeer peer : peers.values()) {
            peer.pinned = false;
            peer.displayName = peer.playerName;
            peer.listed = true;
            peer.positionFixed = false;
            peer.displayOrder = 0;
            peer.configured = false;
        }
        if (preferences != null) {
            for (DirectMessagePeerPreference preference : preferences) {
                if (preference == null || !isValidPlayerName(preference.playerName())) {
                    continue;
                }
                String id = preference.id();
                MutablePeer peer = peers.computeIfAbsent(id,
                        ignored -> new MutablePeer(preference.playerName(), preference.displayName()));
                peer.playerName = preference.playerName();
                peer.displayName = preference.displayName();
                peer.pinned = preference.pinned();
                peer.listed = preference.listed();
                peer.positionFixed = peer.pinned && preference.positionFixed();
                peer.displayOrder = preference.displayOrder();
                peer.configured = true;
            }
        }
        notifyChanged();
    }

    public synchronized boolean updatePreference(
            String peerId,
            String displayName,
            boolean pinned,
            boolean listed
    ) {
        MutablePeer peer = peers.get(MessageRoute.normalizePeerId(peerId));
        if (peer == null) {
            return false;
        }
        return updatePreference(
                peerId,
                displayName,
                pinned,
                listed,
                peer.positionFixed,
                peer.displayOrder
        );
    }

    public synchronized boolean updatePreference(
            String peerId,
            String displayName,
            boolean pinned,
            boolean listed,
            boolean positionFixed,
            int displayOrder
    ) {
        MutablePeer peer = peers.get(MessageRoute.normalizePeerId(peerId));
        if (peer == null) {
            return false;
        }
        peer.displayName = normalizedDisplayName(displayName, peer.playerName);
        peer.pinned = pinned;
        peer.listed = listed;
        peer.positionFixed = pinned && positionFixed;
        peer.displayOrder = displayOrder;
        peer.configured = true;
        notifyChanged();
        return true;
    }

    public synchronized boolean select(String peerId) {
        String id = MessageRoute.normalizePeerId(peerId);
        if (!peers.containsKey(id)) {
            return false;
        }
        boolean changed = !id.equals(currentPeerId);
        currentPeerId = id;
        clearUnreadInternal(id);
        notifyChanged();
        return changed;
    }

    public synchronized Optional<String> currentPeerId() {
        return Optional.ofNullable(currentPeerId);
    }

    public synchronized void incrementUnread(String peerId) {
        MutablePeer peer = peers.get(MessageRoute.normalizePeerId(peerId));
        if (peer != null) {
            peer.unreadCount++;
            notifyChanged();
        }
    }

    public synchronized void clearUnread(String peerId) {
        if (clearUnreadInternal(MessageRoute.normalizePeerId(peerId))) {
            notifyChanged();
        }
    }

    public synchronized void clearAllUnread() {
        boolean changed = false;
        for (MutablePeer peer : peers.values()) {
            if (peer.unreadCount != 0) {
                peer.unreadCount = 0;
                changed = true;
            }
        }
        if (changed) {
            notifyChanged();
        }
    }

    /** 次の送信結果を会話へ関連付けるため、直近の送信先を保持する。 */
    public synchronized void rememberOutgoing(String peerId) {
        pendingOutgoingPeerId = MessageRoute.normalizePeerId(peerId);
    }

    public synchronized Optional<DirectMessagePeer> consumePendingOutgoing() {
        String peerId = pendingOutgoingPeerId;
        pendingOutgoingPeerId = null;
        return peerId == null ? Optional.empty() : find(peerId);
    }

    /** 切断時は活動順・未読・選択をリセットし、設定済みの相手だけ残す。 */
    public synchronized void clearSession() {
        activitySequence = 0;
        currentPeerId = null;
        pendingOutgoingPeerId = null;
        for (MutablePeer peer : peers.values()) {
            peer.lastActivity = 0;
            peer.unreadCount = 0;
        }
        peers.entrySet().removeIf(entry -> {
            MutablePeer peer = entry.getValue();
            return !peer.configured;
        });
        notifyChanged();
    }

    public synchronized int removePeers(Predicate<DirectMessagePeer> predicate) {
        int previousSize = peers.size();
        peers.entrySet().removeIf(entry -> predicate.test(entry.getValue().snapshot(entry.getKey())));
        if (currentPeerId != null && !peers.containsKey(currentPeerId)) {
            currentPeerId = null;
        }
        if (pendingOutgoingPeerId != null && !peers.containsKey(pendingOutgoingPeerId)) {
            pendingOutgoingPeerId = null;
        }
        int removed = previousSize - peers.size();
        if (removed > 0) {
            notifyChanged();
        }
        return removed;
    }

    public synchronized void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public synchronized void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    public static boolean isValidPlayerName(String playerName) {
        return playerName != null && VANILLA_PLAYER_NAME.matcher(playerName.strip()).matches();
    }

    private boolean clearUnreadInternal(String peerId) {
        MutablePeer peer = peers.get(peerId);
        if (peer == null || peer.unreadCount == 0) {
            return false;
        }
        peer.unreadCount = 0;
        return true;
    }

    private void notifyChanged() {
        for (Runnable listener : List.copyOf(listeners)) {
            listener.run();
        }
    }

    private static String normalizedDisplayName(String displayName, String playerName) {
        return displayName == null || displayName.isBlank() ? playerName.strip() : displayName.strip();
    }

    private static final class MutablePeer {
        private String playerName;
        private String displayName;
        private boolean pinned;
        private boolean listed = true;
        private boolean positionFixed;
        private int displayOrder;
        private long lastActivity;
        private int unreadCount;
        private boolean configured;

        private MutablePeer(String playerName, String displayName) {
            this.playerName = playerName;
            this.displayName = displayName;
        }

        private DirectMessagePeer snapshot(String id) {
            return new DirectMessagePeer(
                    id,
                    playerName,
                    displayName,
                    pinned,
                    listed,
                    positionFixed,
                    displayOrder,
                    lastActivity,
                    unreadCount
            );
        }
    }
}
