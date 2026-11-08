package celeste.chataber.config;

import java.util.List;

/** 設定JSONの保存形式。 */
public record ChataberConfig(
        String selectedTab,
        List<TabConfig> tabs,
        DirectMessageConfig directMessages,
        NotificationConfig notifications,
        TokenConfig tokens
) {
    public record TabConfig(String id, String name, List<String> filters, Boolean includeDirectMessages) {
    }

    public record DirectMessageConfig(
            String placementMode,
            String placementAnchorTab,
            Boolean autoSend,
            String commandTemplate,
            List<PeerConfig> peers,
            Integer maxVisibleTabs
    ) {
    }

    public record PeerConfig(
            String playerName,
            String displayName,
            Boolean pinned,
            Boolean listed,
            Boolean positionFixed,
            Integer displayOrder
    ) {
    }

    public record NotificationConfig(
            Integer hudSeconds, String badgeStyle, Boolean persistentHud, List<String> persistentHudTargets
    ) {
    }

    public record TokenConfig(String hereFormat) {
    }
}
