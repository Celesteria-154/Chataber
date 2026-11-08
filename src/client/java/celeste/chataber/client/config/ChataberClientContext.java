package celeste.chataber.client.config;

import celeste.chataber.client.message.DirectMessageAvailability;
import celeste.chataber.config.ChatSettings;
import celeste.chataber.config.ConfigManager;
import celeste.chataber.message.DirectMessageManager;
import celeste.chataber.tab.ChatTabManager;

import java.util.Objects;

/** Mod Menuと各設定画面で共有するクライアント側依存関係。 */
public record ChataberClientContext(
        ChatTabManager tabs,
        DirectMessageManager directMessages,
        ChatSettings settings,
        ConfigManager configManager,
        DirectMessageAvailability directMessageAvailability
) {
    private static ChataberClientContext instance;

    public ChataberClientContext {
        Objects.requireNonNull(tabs, "tabs");
        Objects.requireNonNull(directMessages, "directMessages");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(configManager, "configManager");
        Objects.requireNonNull(directMessageAvailability, "directMessageAvailability");
    }

    public static void initialize(ChataberClientContext context) {
        instance = Objects.requireNonNull(context, "context");
    }

    public static ChataberClientContext current() {
        if (instance == null) {
            throw new IllegalStateException("Chataber client context is not initialized");
        }
        return instance;
    }
}
