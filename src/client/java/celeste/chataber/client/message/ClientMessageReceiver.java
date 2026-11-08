package celeste.chataber.client.message;

import celeste.chataber.config.ChatSettings;
import celeste.chataber.message.DirectMessageManager;
import celeste.chataber.message.DirectMessagePeer;
import celeste.chataber.message.ChatHistoryManager;
import celeste.chataber.message.MessageClassifier;
import celeste.chataber.message.MessageCategory;
import celeste.chataber.message.MessageRoute;
import celeste.chataber.tab.ChatTabManager;
import celeste.chataber.tab.HudNotificationTargets;
import celeste.chataber.tab.ChatViewManager;
import celeste.chataber.tab.TabNotificationManager;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;

import java.time.Instant;
import java.util.Optional;

/** 受信メッセージの分類・保存・未読通知。 */
public final class ClientMessageReceiver {
    private final MessageClassifier classifier;
    private final ChatHistoryManager history;
    private final Minecraft client;
    private final ChatTabManager tabs;
    private final DirectMessageManager directMessages;
    private final ChatViewManager views;
    private final TabNotificationManager notifications;
    private final ChatSettings settings;
    private final DirectMessageAvailability availability;

    public ClientMessageReceiver(
            MessageClassifier classifier,
            ChatHistoryManager history,
            Minecraft client,
            ChatTabManager tabs,
            DirectMessageManager directMessages,
            ChatViewManager views,
            TabNotificationManager notifications,
            ChatSettings settings,
            DirectMessageAvailability availability
    ) {
        this.classifier = classifier;
        this.history = history;
        this.client = client;
        this.tabs = tabs;
        this.directMessages = directMessages;
        this.views = views;
        this.notifications = notifications;
        this.settings = settings;
        this.availability = availability;
    }

    public void register() {
        // 受信、分類、保存、未読更新を現在の表示状態から独立して行う。
        ClientReceiveMessageEvents.CHAT.register(this::receiveChat);

        // Action Barの表示はチャット履歴・通知へ取り込まない
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                receiveRegular(message, classifier.classifyGameMessage(message));
            }
        });

        // 別の接続へ履歴や通知を持ち越さない
        ClientPlayConnectionEvents.DISCONNECT.register((handler, disconnectedClient) -> {
            history.clear();
            directMessages.clearSession();
            tabs.clearAllUnread();
            notifications.clearAll();
            ClientMessageRouting.clear();
            views.resetToConfiguredTab();
        });
    }

    private void receiveChat(
            Component message,
            PlayerChatMessage signedMessage,
            GameProfile sender,
            ChatType.Bound boundChatType,
            Instant timestamp
    ) {
        // 表示文字列ではなくChatTypeからDMの送受信を判別
        if (boundChatType.chatType().is(ChatType.MSG_COMMAND_INCOMING)) {
            String playerName = sender == null ? boundChatType.name().getString() : sender.name();
            receiveDirectMessage(
                    message,
                    signedMessage,
                    playerName,
                    boundChatType.name().getString(),
                    MessageRoute.Direction.INCOMING,
                    timestamp
            );
            return;
        }
        if (boundChatType.chatType().is(ChatType.MSG_COMMAND_OUTGOING)) {
            DirectMessagePeer peer = resolveOutgoingPeer(boundChatType).orElse(null);
            if (peer != null) {
                receiveDirectMessage(
                        message,
                        signedMessage,
                        peer.playerName(),
                        peer.displayName(),
                        MessageRoute.Direction.OUTGOING,
                        timestamp
                );
                return;
            }
        }
        MessageRoute.Category route = MessageRoute.category(classifier.classifyPlayerChat(message));
        record(message, signedMessage == null ? null : signedMessage.signature(), route, timestamp);
        notifyHiddenCategory(route);
    }

    private void receiveRegular(Component message, MessageCategory category) {
        MessageRoute.Category route = MessageRoute.category(category);
        record(message, null, route, Instant.now());
        notifyHiddenCategory(route);
    }

    private void receiveDirectMessage(
            Component message,
            PlayerChatMessage signedMessage,
            String playerName,
            String displayName,
            MessageRoute.Direction direction,
            Instant timestamp
    ) {
        if (playerName == null || playerName.isBlank() || availability.isSelfName(playerName)) {
            MessageRoute.Category fallback = MessageRoute.category(MessageCategory.PLAYER_CHAT);
            record(message, signedMessage == null ? null : signedMessage.signature(), fallback, timestamp);
            notifyHiddenCategory(fallback);
            return;
        }
        DirectMessagePeer peer = directMessages.observe(playerName, displayName);
        MessageRoute route = MessageRoute.directMessage(peer.id(), direction);
        // 通常表示の受信文は残し、DMビュー用に「話者 + 本文」を別途用意
        Component conversation = directMessageConversation(
                signedMessage,
                direction == MessageRoute.Direction.OUTGOING
                        ? client.getGameProfile().name()
                        : peer.shownName()
        );
        record(message, signedMessage == null ? null : signedMessage.signature(), route, timestamp, conversation);
        // HUD通知は受信DMだけ。表示中の会話とチャット画面を開いている間は通知しない
        if (direction == MessageRoute.Direction.INCOMING
                && !views.isVisible(route)
                && !(client.gui.screen() instanceof ChatScreen)) {
            notifications.notify(
                    "dm:" + peer.id(),
                    peer.shownName(),
                    route,
                    settings.state().hudNotificationSeconds(),
                    settings.state().persistentHudNotifications("dm:" + peer.id())
            );
        }
    }

    private void notifyHiddenCategory(MessageRoute.Category route) {
        if (views.isVisible(route) || client.gui.screen() instanceof ChatScreen) {
            return;
        }
        // カテゴリが同じでも通知はタブ別に作成し、個別の表示期限を使う
        for (HudNotificationTargets.Target target : HudNotificationTargets.forCategory(tabs, route.category())) {
            notifications.notify(
                    target.id(), target.label(), route,
                    settings.state().hudNotificationSeconds(),
                    settings.state().persistentHudNotifications(target.id())
            );
        }
    }

    private void record(
            Component message,
            net.minecraft.network.chat.MessageSignature signature,
            MessageRoute route,
            Instant timestamp
    ) {
        history.add(message, route, timestamp);
        ClientMessageRouting.enqueue(message, signature, route);
        views.recordUnread(route);
    }

    private void record(
            Component message,
            net.minecraft.network.chat.MessageSignature signature,
            MessageRoute route,
            Instant timestamp,
            Component directMessagePresentation
    ) {
        history.add(message, route, timestamp);
        ClientMessageRouting.enqueue(message, signature, route, directMessagePresentation);
        views.recordUnread(route);
    }

    private static Component directMessageConversation(PlayerChatMessage message, String speaker) {
        if (message == null) {
            return null;
        }
        return Component.literal("<" + speaker + "> ").append(message.decoratedContent());
    }

    private Optional<DirectMessagePeer> resolveOutgoingPeer(ChatType.Bound boundChatType) {
        Optional<DirectMessagePeer> pending = directMessages.consumePendingOutgoing();
        if (pending.filter(availability::isEligiblePeer).isPresent()) {
            return pending;
        }
        String target = boundChatType.targetName().map(Component::getString).orElse("").strip();
        if (target.isEmpty()) {
            return Optional.empty();
        }
        if (!availability.isEligiblePlayerName(target)) {
            return Optional.empty();
        }
        return availability.onlinePlayerName(target).flatMap(directMessages::find);
    }
}
