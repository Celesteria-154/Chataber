package celeste.chataber.client;

import celeste.chataber.client.gui.ChatTabBar;
import celeste.chataber.client.gui.ChatNotificationHud;
import celeste.chataber.client.config.ChataberClientContext;
import celeste.chataber.client.message.ClientDirectMessageInput;
import celeste.chataber.client.message.ChatVisibilityController;
import celeste.chataber.client.message.ClientMessageReceiver;
import celeste.chataber.client.message.DirectMessageAvailability;
import celeste.chataber.client.token.ClientChatTokenHandler;
import celeste.chataber.config.ChatSettings;
import celeste.chataber.config.ConfigManager;
import celeste.chataber.message.DirectMessageManager;
import celeste.chataber.message.ChatHistoryManager;
import celeste.chataber.message.MessageClassifier;
import celeste.chataber.tab.ChatTabManager;
import celeste.chataber.tab.ChatViewManager;
import celeste.chataber.tab.TabNotificationManager;
import celeste.chataber.token.ChatTokenProcessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;

/** クライアント初期化。 */
public class ChataberClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Minecraft client = Minecraft.getInstance();
        MessageClassifier classifier = new MessageClassifier();
        ChatHistoryManager history = new ChatHistoryManager();
        ChatTabManager tabs = new ChatTabManager();
        DirectMessageManager directMessages = new DirectMessageManager();
        ChatSettings settings = new ChatSettings();
        TabNotificationManager notifications = new TabNotificationManager();
        ConfigManager config = new ConfigManager();
        config.loadInto(tabs, directMessages, settings);
        ChatViewManager views = new ChatViewManager(tabs, directMessages);
        DirectMessageAvailability dmAvailability = new DirectMessageAvailability(client);
        if (directMessages.removePeers(peer -> dmAvailability.isSelfName(peer.playerName())) > 0) {
            config.save(tabs, directMessages, settings);
        }
        ChataberClientContext.initialize(new ChataberClientContext(
                tabs, directMessages, settings, config, dmAvailability));

        ChatVisibilityController visibility = new ChatVisibilityController(client, views, classifier);
        // 選択変更は表示だけを更新し、受信・保存経路には影響させない。
        views.addListener(() -> {
            visibility.apply();
            notifications.clearVisible(views::isVisible);
        });
        tabs.addConfigurationListener(() -> {
            views.synchronizeTabs();
            visibility.apply();
        });

        ChatTokenProcessor tokenProcessor = ChatTokenProcessor.createDefault(
                () -> settings.state().hereFormat());
        new ClientMessageReceiver(
                classifier,
                history,
                client,
                tabs,
                directMessages,
                views,
                notifications,
                settings,
                dmAvailability
        ).register();
        new ClientChatTokenHandler(client, tokenProcessor).register();
        new ClientDirectMessageInput(
                client, views, directMessages, settings, tokenProcessor, dmAvailability).register();
        new ChatTabBar(
                tabs, directMessages, views, notifications, settings, dmAvailability).register();
        new ChataberManagementKeyBinding(client, tabs, directMessages, settings, config, dmAvailability)
                .register();
        new ChatNotificationHud(client, tabs, directMessages, notifications, settings).register();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, joinedClient) -> visibility.apply());
        visibility.apply();
    }
}
