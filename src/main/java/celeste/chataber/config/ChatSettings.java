package celeste.chataber.config;

import java.util.Set;
import java.util.Objects;

/** 一般設定の既定値と確定状態。 */
public final class ChatSettings {
    public static final String DEFAULT_DM_COMMAND_TEMPLATE = "/tell {player}";
    public static final String DEFAULT_HERE_FORMAT = "X:{x} Y:{y} Z:{z}";
    // HUD通知は秒単位。0は時間制限付き通知を無効化
    public static final int DEFAULT_HUD_NOTIFICATION_SECONDS = 5;
    public static final int MAX_HUD_NOTIFICATION_SECONDS = 60;
    // 0は件数制限なし（実際の表示数は画面幅に合わせる）
    public static final int DEFAULT_MAX_VISIBLE_DM_TABS = 0;
    public static final int MAX_VISIBLE_DM_TABS = 99;

    private State state = defaults();

    public State state() {
        return state;
    }

    public void apply(State newState) {
        state = validate(Objects.requireNonNull(newState, "newState"));
    }

    public static State defaults() {
        return new State(
                DmGroupPlacement.last(),
                true,
                DEFAULT_DM_COMMAND_TEMPLATE,
                DEFAULT_HUD_NOTIFICATION_SECONDS,
                Set.of(),
                NotificationBadgeStyle.DOT,
                DEFAULT_MAX_VISIBLE_DM_TABS,
                DEFAULT_HERE_FORMAT
        );
    }

    public static State validate(State candidate) {
        DmGroupPlacement placement = DmGroupPlacement.last();
        String template = candidate.dmCommandTemplate();
        boolean autoSend = candidate.dmAutoSend();
        // 雛形が不正な場合は、自動送信も止めて意図しないコマンド送信を避ける
        if (!isValidDmCommandTemplate(template)) {
            template = DEFAULT_DM_COMMAND_TEMPLATE;
            autoSend = false;
        }
        int seconds = Math.max(0, Math.min(MAX_HUD_NOTIFICATION_SECONDS,
                candidate.hudNotificationSeconds()));
        NotificationBadgeStyle badgeStyle = candidate.badgeStyle() == null
                ? NotificationBadgeStyle.DOT
                : candidate.badgeStyle();
        int maxVisibleDmTabs = Math.max(0, Math.min(MAX_VISIBLE_DM_TABS,
                candidate.maxVisibleDmTabs()));
        String hereFormat = isValidHereFormat(candidate.hereFormat())
                ? candidate.hereFormat().strip()
                : DEFAULT_HERE_FORMAT;
        return new State(
                placement,
                autoSend,
                template.strip(),
                seconds,
                candidate.persistentHudTargets(),
                badgeStyle,
                maxVisibleDmTabs,
                hereFormat
        );
    }

    public static boolean isValidDmCommandTemplate(String template) {
        if (template == null) {
            return false;
        }
        String normalized = template.strip();
        if (!normalized.startsWith("/") || normalized.indexOf('\n') >= 0 || normalized.indexOf('\r') >= 0) {
            return false;
        }
        // 宛先の置換箇所は1個だけ許可
        int first = normalized.indexOf("{player}");
        return first >= 0 && normalized.indexOf("{player}", first + 1) < 0;
    }

    public static boolean isValidHereFormat(String format) {
        if (format == null) {
            return false;
        }
        String normalized = format.strip();
        return !normalized.isEmpty()
                && normalized.indexOf('\n') < 0
                && normalized.indexOf('\r') < 0
                && normalized.contains("{x}")
                && normalized.contains("{y}")
                && normalized.contains("{z}");
    }

    /**
     * 一般設定の確定値。常時表示の対象は名前ではなくIDで保持する。
     * persistentHudTargets: 通常タブは tab:ID、DM相手は dm:相手ID。
     */
    public record State(
            DmGroupPlacement dmGroupPlacement,
            boolean dmAutoSend,
            String dmCommandTemplate,
            int hudNotificationSeconds,
            Set<String> persistentHudTargets,
            NotificationBadgeStyle badgeStyle,
            int maxVisibleDmTabs,
            String hereFormat
    ) {
        public State {
            persistentHudTargets = persistentHudTargets == null ? Set.of() : Set.copyOf(persistentHudTargets);
        }

        public boolean persistentHudNotifications(String targetId) {
            return persistentHudTargets.contains(targetId);
        }

    }
}
