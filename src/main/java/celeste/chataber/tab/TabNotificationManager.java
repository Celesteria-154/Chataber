package celeste.chataber.tab;

import celeste.chataber.message.MessageRoute;

import java.time.Clock;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/** HUD通知の集約と表示期限の管理。 */
public final class TabNotificationManager {
    private final Clock clock;
    private final Map<String, MutableNotification> notifications = new LinkedHashMap<>();
    private long sequence;

    public TabNotificationManager() {
        this(Clock.systemUTC());
    }

    TabNotificationManager(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 通知先ごとに受信を集約し、表示期限を更新する。
     *
     * @param targetId 通知先ID（通常タブ: tab:ID、DM: dm:相手ID）
     * @param label HUDに表示するタブ名または相手名
     * @param route 表示済みかを判定するための受信経路
     * @param durationSeconds 表示秒数。0以下は時間制限付き通知を作らない
     * @param persistent trueなら、対象の内容を表示するまで期限切れにしない
     */
    public synchronized void notify(
            String targetId,
            String label,
            MessageRoute route,
            int durationSeconds,
            boolean persistent
    ) {
        removeExpired();
        if (!persistent && durationSeconds <= 0) {
            return;
        }
        // 期限はミリ秒単位。常時表示は最大値を使い、通常の期限判定から除外
        long expiresAt = persistent
                ? Long.MAX_VALUE
                : clock.millis() + durationSeconds * 1000L;
        // 同じ通知先への再受信は、項目を増やさず内容と期限を更新
        MutableNotification notification = notifications.computeIfAbsent(targetId,
                ignored -> new MutableNotification());
        notification.label = label;
        notification.route = Objects.requireNonNull(route, "route");
        notification.routes.add(route);
        notification.expiresAt = expiresAt;
        notification.sequence = ++sequence;
    }

    public synchronized List<Notification> activeNotifications() {
        removeExpired();
        return notifications.entrySet().stream()
                .map(entry -> new Notification(
                        entry.getKey(),
                        entry.getValue().label,
                        entry.getValue().route,
                        entry.getValue().expiresAt,
                        entry.getValue().sequence
                ))
                // 再受信した通知先も、新しい通知として手前に表示
                .sorted(Comparator.comparingLong(Notification::sequence).reversed())
                .toList();
    }

    /** 表示中の通知に新しい表示期限を適用。 */
    public synchronized void applyLifetimePolicy(int durationSeconds, Predicate<String> persistentTarget) {
        Objects.requireNonNull(persistentTarget, "persistentTarget");
        removeExpired();
        long expiresAt = clock.millis() + Math.max(0, durationSeconds) * 1000L;
        notifications.entrySet().removeIf(entry -> {
            MutableNotification notification = entry.getValue();
            if (persistentTarget.test(entry.getKey())) {
                notification.expiresAt = Long.MAX_VALUE;
            } else if (durationSeconds <= 0) {
                return true;
            } else if (notification.expiresAt == Long.MAX_VALUE) {
                // 常時表示の解除時だけ期限を設定し直す。毎フレームの延長は行わない
                notification.expiresAt = expiresAt;
            }
            return false;
        });
    }

    public synchronized void clearAll() {
        notifications.clear();
    }

    public synchronized void clearVisible(Predicate<MessageRoute> visibility) {
        Objects.requireNonNull(visibility, "visibility");
        notifications.values().removeIf(notification -> {
            // 複数カテゴリを含むタブでは、未表示のカテゴリ分を残す
            notification.routes.removeIf(visibility);
            if (notification.routes.isEmpty()) {
                return true;
            }
            if (!notification.routes.contains(notification.route)) {
                notification.route = notification.routes.iterator().next();
            }
            return false;
        });
    }

    private void removeExpired() {
        long now = clock.millis();
        notifications.values().removeIf(notification -> notification.expiresAt <= now);
    }

    public record Notification(
            String targetId,
            String label,
            MessageRoute route,
            long expiresAt,
            long sequence
    ) {
    }

    private static final class MutableNotification {
        private String label;
        private MessageRoute route;
        // 同じタブへ集約した通知の内訳。routeは表示用の代表経路
        private final Set<MessageRoute> routes = new LinkedHashSet<>();
        private long expiresAt;
        private long sequence;
    }
}
