package celeste.chataber.config;

import java.util.Objects;
import java.util.Set;

/** 一般設定画面の編集用コピー。 */
public final class ChataberSettingsDraft {
    private ChatSettings.State state;

    public ChataberSettingsDraft(ChatSettings liveSettings) {
        state = Objects.requireNonNull(liveSettings, "liveSettings").state();
    }

    public ChatSettings.State state() {
        return state;
    }

    public void setDmAutoSend(boolean enabled) {
        state = copy(enabled, state.dmCommandTemplate(), state.hudNotificationSeconds(),
                state.persistentHudTargets(),
                state.badgeStyle(), state.maxVisibleDmTabs(), state.hereFormat());
    }

    public void setDmCommandTemplate(String template) {
        state = copy(state.dmAutoSend(), template, state.hudNotificationSeconds(),
                state.persistentHudTargets(),
                state.badgeStyle(), state.maxVisibleDmTabs(), state.hereFormat());
    }

    public void setHudNotificationSeconds(int seconds) {
        state = copy(state.dmAutoSend(), state.dmCommandTemplate(), seconds,
                state.persistentHudTargets(),
                state.badgeStyle(), state.maxVisibleDmTabs(), state.hereFormat());
    }

    public void setBadgeStyle(NotificationBadgeStyle badgeStyle) {
        state = copy(
                state.dmAutoSend(),
                state.dmCommandTemplate(),
                state.hudNotificationSeconds(),
                state.persistentHudTargets(),
                Objects.requireNonNull(badgeStyle, "badgeStyle"),
                state.maxVisibleDmTabs(),
                state.hereFormat()
        );
    }

    public void setMaxVisibleDmTabs(int maxVisibleDmTabs) {
        state = copy(state.dmAutoSend(), state.dmCommandTemplate(), state.hudNotificationSeconds(),
                state.persistentHudTargets(),
                state.badgeStyle(), maxVisibleDmTabs, state.hereFormat());
    }

    public void setHereFormat(String hereFormat) {
        state = copy(state.dmAutoSend(), state.dmCommandTemplate(), state.hudNotificationSeconds(),
                state.persistentHudTargets(),
                state.badgeStyle(), state.maxVisibleDmTabs(), hereFormat);
    }

    public boolean isValid() {
        return ChatSettings.isValidDmCommandTemplate(state.dmCommandTemplate())
                && ChatSettings.isValidHereFormat(state.hereFormat())
                && state.hudNotificationSeconds() >= 0
                && state.hudNotificationSeconds() <= ChatSettings.MAX_HUD_NOTIFICATION_SECONDS;
    }

    public void applyTo(ChatSettings liveSettings) {
        if (!isValid()) {
            throw new IllegalStateException("Chat settings draft is invalid");
        }
        // 管理画面から戻った場合、そこで確定したタブ別設定を古いコピーで上書きしない。
        ChatSettings.State current = liveSettings.state();
        liveSettings.apply(new ChatSettings.State(
                state.dmGroupPlacement(), state.dmAutoSend(), state.dmCommandTemplate(),
                state.hudNotificationSeconds(), current.persistentHudTargets(), state.badgeStyle(),
                state.maxVisibleDmTabs(),
                state.hereFormat()
        ));
    }

    private ChatSettings.State copy(
            boolean autoSend,
            String commandTemplate,
            int notificationSeconds,
            Set<String> persistentHudTargets,
            NotificationBadgeStyle badgeStyle,
            int maxVisibleDmTabs,
            String hereFormat
    ) {
        return new ChatSettings.State(
                DmGroupPlacement.last(),
                autoSend,
                commandTemplate,
                notificationSeconds,
                persistentHudTargets,
                badgeStyle,
                maxVisibleDmTabs,
                hereFormat
        );
    }
}
