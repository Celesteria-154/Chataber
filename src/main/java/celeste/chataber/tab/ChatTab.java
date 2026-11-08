package celeste.chataber.tab;

import celeste.chataber.message.ChataberMessage;
import celeste.chataber.message.MessageCategory;
import celeste.chataber.message.MessageRoute;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** チャットタブの表示設定。 */
public final class ChatTab {
    private final String id;
    private final String name;
    private final EnumSet<MessageCategory> enabledCategories;
    private final boolean includeDirectMessages;

    public ChatTab(String id, String name, Set<MessageCategory> enabledCategories) {
        this(id, name, enabledCategories, false);
    }

    public ChatTab(
            String id,
            String name,
            Set<MessageCategory> enabledCategories,
            boolean includeDirectMessages
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.enabledCategories = enabledCategories.isEmpty()
                ? EnumSet.noneOf(MessageCategory.class)
                : EnumSet.copyOf(enabledCategories);
        // DM包含はプレイヤーチャットが有効なタブだけで使用
        this.includeDirectMessages = includeDirectMessages
                && this.enabledCategories.contains(MessageCategory.PLAYER_CHAT);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Set<MessageCategory> enabledCategories() {
        return Set.copyOf(enabledCategories);
    }

    public boolean includeDirectMessages() {
        return includeDirectMessages;
    }

    public boolean matches(MessageCategory category) {
        return enabledCategories.contains(category);
    }

    public boolean matches(ChataberMessage message) {
        return matches(message.route());
    }

    public boolean matches(MessageRoute route) {
        return route instanceof MessageRoute.Category categoryRoute
                ? matches(categoryRoute.category())
                : includeDirectMessages && route instanceof MessageRoute.DirectMessage;
    }

    public ChatTab withName(String newName) {
        return new ChatTab(id, newName, enabledCategories, includeDirectMessages);
    }

    public ChatTab withCategory(MessageCategory category, boolean enabled) {
        EnumSet<MessageCategory> categories = EnumSet.copyOf(enabledCategories);
        if (enabled) {
            categories.add(category);
        } else {
            categories.remove(category);
        }
        return new ChatTab(id, name, categories, includeDirectMessages);
    }

    public ChatTab withDirectMessages(boolean included) {
        return new ChatTab(id, name, enabledCategories, included);
    }
}
