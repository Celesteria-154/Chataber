package celeste.chataber.tab;

import celeste.chataber.message.MessageCategory;
import celeste.chataber.message.MessageRoute;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

/** タブ設定・選択状態・未読数の管理。 */
public final class ChatTabManager {
    public static final String ALL_TAB_ID = "all";
    public static final int MAX_TAB_NAME_LENGTH = 32;

    private final List<ChatTab> tabs = new ArrayList<>();
    // 未読更新では設定や表示フィルターを再構築しないよう、通知先を分離
    private final List<Runnable> selectionListeners = new ArrayList<>();
    private final List<Runnable> configurationListeners = new ArrayList<>();
    private final List<Runnable> unreadListeners = new ArrayList<>();
    private final Map<String, Integer> unreadCounts = new HashMap<>();
    private String selectedTabId;

    public ChatTabManager() {
        resetToDefaults();
    }

    public static List<ChatTab> defaultTabs() {
        return List.of(
                new ChatTab(ALL_TAB_ID, "All", EnumSet.allOf(MessageCategory.class), true),
                new ChatTab("chat", "Chat", EnumSet.of(MessageCategory.PLAYER_CHAT)),
                new ChatTab("system", "Sys", EnumSet.of(MessageCategory.SYSTEM)),
                new ChatTab("command", "Command", EnumSet.of(MessageCategory.COMMAND_FEEDBACK))
        );
    }

    public List<ChatTab> tabs() {
        return List.copyOf(tabs);
    }

    public ChatTab selectedTab() {
        return findById(selectedTabId).orElseGet(() -> tabs.getFirst());
    }

    public String selectedTabId() {
        return selectedTab().id();
    }

    public java.util.Optional<ChatTab> findById(String id) {
        return tabs.stream().filter(tab -> tab.id().equals(id)).findFirst();
    }

    public void addSelectionListener(Runnable listener) {
        selectionListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void addConfigurationListener(Runnable listener) {
        configurationListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void addUnreadListener(Runnable listener) {
        unreadListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeUnreadListener(Runnable listener) {
        unreadListeners.remove(listener);
    }

    public void replaceAll(List<ChatTab> newTabs, String newSelectedTabId) {
        Objects.requireNonNull(newTabs, "newTabs");
        if (newTabs.isEmpty() || newTabs.stream().noneMatch(tab -> tab.id().equals(ALL_TAB_ID))) {
            throw new IllegalArgumentException("All tab is required");
        }
        tabs.clear();
        tabs.addAll(newTabs);
        // 残るタブの未読は維持し、削除されたタブのカウントだけ破棄
        unreadCounts.keySet().retainAll(newTabs.stream().map(ChatTab::id).toList());
        String previousSelection = selectedTabId;
        selectedTabId = findById(newSelectedTabId).isPresent() ? newSelectedTabId : ALL_TAB_ID;
        notifyConfigurationChanged();
        if (!Objects.equals(previousSelection, selectedTabId)) {
            notifySelectionChanged();
        }
    }

    public void resetToDefaults() {
        tabs.clear();
        tabs.addAll(defaultTabs());
        unreadCounts.clear();
        String previousSelection = selectedTabId;
        selectedTabId = ALL_TAB_ID;
        notifyConfigurationChanged();
        if (!Objects.equals(previousSelection, selectedTabId)) {
            notifySelectionChanged();
        }
    }

    public boolean select(String id) {
        if (findById(id).isEmpty() || selectedTabId.equals(id)) {
            return false;
        }
        selectedTabId = id;
        notifySelectionChanged();
        return true;
    }

    public ChatTab create(String proposedName) {
        String name = normalizeName(proposedName, "New Tab");
        ChatTab tab = new ChatTab(UUID.randomUUID().toString(), name, EnumSet.of(MessageCategory.PLAYER_CHAT));
        tabs.add(tab);
        notifyConfigurationChanged();
        return tab;
    }

    /** Allは削除不可。選択中のタブを削除した場合はAllへ戻す。 */
    public boolean delete(String id) {
        if (ALL_TAB_ID.equals(id)) {
            return false;
        }
        int index = indexOf(id);
        if (index < 0) {
            return false;
        }
        tabs.remove(index);
        unreadCounts.remove(id);
        boolean selectionChanged = false;
        if (selectedTabId.equals(id)) {
            selectedTabId = ALL_TAB_ID;
            selectionChanged = true;
        }
        notifyConfigurationChanged();
        if (selectionChanged) {
            notifySelectionChanged();
        }
        return true;
    }

    public boolean rename(String id, String proposedName) {
        int index = indexOf(id);
        String name = normalizeName(proposedName, null);
        if (index < 0 || name == null || tabs.get(index).name().equals(name)) {
            return false;
        }
        tabs.set(index, tabs.get(index).withName(name));
        notifyConfigurationChanged();
        return true;
    }

    public boolean setCategory(String id, MessageCategory category, boolean enabled) {
        int index = indexOf(id);
        if (index < 0 || ALL_TAB_ID.equals(id)) {
            return false;
        }
        ChatTab current = tabs.get(index);
        if (current.enabledCategories().contains(category) == enabled) {
            return false;
        }
        tabs.set(index, current.withCategory(category, enabled));
        notifyConfigurationChanged();
        return true;
    }

    public boolean setIncludeDirectMessages(String id, boolean included) {
        int index = indexOf(id);
        if (index < 0 || ALL_TAB_ID.equals(id)) {
            return false;
        }
        ChatTab current = tabs.get(index);
        boolean normalized = included && current.matches(MessageCategory.PLAYER_CHAT);
        if (current.includeDirectMessages() == normalized) {
            return false;
        }
        tabs.set(index, current.withDirectMessages(normalized));
        notifyConfigurationChanged();
        return true;
    }

    public boolean move(String id, int direction) {
        int index = indexOf(id);
        // directionの大きさではなく符号を使い、1回の操作で隣へ移動
        int target = index + Integer.signum(direction);
        if (index < 0 || direction == 0 || target < 0 || target >= tabs.size()) {
            return false;
        }
        ChatTab tab = tabs.remove(index);
        tabs.add(target, tab);
        notifyConfigurationChanged();
        return true;
    }

    public void incrementUnread(MessageCategory category) {
        incrementUnread(MessageRoute.category(category));
    }

    /** 同じ内容を表示する通常タブへそれぞれ加算する。Allには未読を持たせない。 */
    public void incrementUnread(MessageRoute route) {
        boolean changed = false;
        for (ChatTab tab : tabs) {
            if (!ALL_TAB_ID.equals(tab.id()) && tab.matches(route)) {
                unreadCounts.merge(tab.id(), 1, Integer::sum);
                changed = true;
            }
        }
        if (changed) {
            notifyUnreadChanged();
        }
    }

    public int unreadCount(String tabId) {
        return unreadCounts.getOrDefault(tabId, 0);
    }

    public void clearUnread(String tabId) {
        if (unreadCounts.remove(tabId) != null) {
            notifyUnreadChanged();
        }
    }

    public void clearAllUnread() {
        if (!unreadCounts.isEmpty()) {
            unreadCounts.clear();
            notifyUnreadChanged();
        }
    }

    private int indexOf(String id) {
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private static String normalizeName(String proposedName, String fallback) {
        if (proposedName == null) {
            return fallback;
        }
        String normalized = proposedName.strip();
        if (normalized.isEmpty()) {
            return fallback;
        }
        return normalized.length() <= MAX_TAB_NAME_LENGTH
                ? normalized
                : normalized.substring(0, MAX_TAB_NAME_LENGTH);
    }

    private void notifySelectionChanged() {
        for (Runnable listener : List.copyOf(selectionListeners)) {
            listener.run();
        }
    }

    private void notifyConfigurationChanged() {
        for (Runnable listener : List.copyOf(configurationListeners)) {
            listener.run();
        }
    }

    private void notifyUnreadChanged() {
        for (Runnable listener : List.copyOf(unreadListeners)) {
            listener.run();
        }
    }
}
