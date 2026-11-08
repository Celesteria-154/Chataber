package celeste.chataber.tab;

import celeste.chataber.message.MessageCategory;

import java.util.List;

/** カテゴリに対応するHUD通知先。 */
public final class HudNotificationTargets {
    private HudNotificationTargets() {
    }

    public static List<Target> forCategory(ChatTabManager tabs, MessageCategory category) {
        // 同じカテゴリを含むタブも別の通知先として扱い、常時表示設定を独立させる
        List<Target> targets = tabs.tabs().stream()
                .filter(tab -> !ChatTabManager.ALL_TAB_ID.equals(tab.id()))
                .filter(tab -> tab.matches(category))
                .map(tab -> new Target("tab:" + tab.id(), tab.name()))
                .toList();
        if (!targets.isEmpty()) {
            return targets;
        }
        // 分類タブが削除されていても、一時通知用のカテゴリ名は残す
        String label = switch (category) {
            case PLAYER_CHAT -> "Chat";
            case SYSTEM -> "Sys";
            case COMMAND_FEEDBACK -> "Command";
        };
        return List.of(new Target("category:" + category.name(), label));
    }

    public record Target(String id, String label) {
    }
}
