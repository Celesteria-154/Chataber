package celeste.chataber.client.message;

import celeste.chataber.config.ChatSettings;
import celeste.chataber.message.DirectMessageManager;
import celeste.chataber.message.DirectMessageCommandFormatter;
import celeste.chataber.message.DirectMessagePeer;
import celeste.chataber.tab.ChatViewManager;
import celeste.chataber.tab.SelectedChatView;
import celeste.chataber.token.ChatTokenContext;
import celeste.chataber.token.ChatTokenProcessor;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** DM本文から送信コマンドへの変換。 */
public final class ClientDirectMessageInput {
    private final Minecraft client;
    private final ChatViewManager views;
    private final DirectMessageManager directMessages;
    private final ChatSettings settings;
    private final ChatTokenProcessor tokenProcessor;
    private final DirectMessageAvailability availability;

    public ClientDirectMessageInput(
            Minecraft client,
            ChatViewManager views,
            DirectMessageManager directMessages,
            ChatSettings settings,
            ChatTokenProcessor tokenProcessor,
            DirectMessageAvailability availability
    ) {
        this.client = client;
        this.views = views;
        this.directMessages = directMessages;
        this.settings = settings;
        this.tokenProcessor = tokenProcessor;
        this.availability = availability;
    }

    public void register() {
        ClientDirectMessageInputBridge.initialize(this);
        ClientSendMessageEvents.COMMAND.register(this::rememberExplicitDirectMessageTarget);
    }

    String transform(String input) {
        // スラッシュ始まりのコマンドは、DMビュー内でもそのまま送信経路へ渡す
        if (input.isBlank()
                || input.startsWith("/")
                || !settings.state().dmAutoSend()
                || !(views.selectedView() instanceof SelectedChatView.DirectMessage selected)) {
            return input;
        }
        if (!availability.isAvailable()) {
            showError("chataber.error.invalid_dm_target");
            return "";
        }
        DirectMessagePeer peer = directMessages.find(selected.peerId()).orElse(null);
        if (peer == null || !availability.isOnlinePlayerName(peer.playerName())) {
            showError("chataber.error.invalid_dm_target");
            return "";
        }

        // 座標などを展開してからコマンドを組み立て、最終的な文字数を検証
        String body = tokenProcessor.process(input, currentContext());
        String command = DirectMessageCommandFormatter.format(
                settings.state().dmCommandTemplate(), peer.playerName(), body).orElse(null);
        if (command == null) {
            showError("chataber.error.message_too_long");
            return "";
        }
        directMessages.rememberOutgoing(peer.id());
        return command;
    }

    // 手入力のVanilla DMコマンドも、送信結果を紐付けるため宛先を記録
    private void rememberExplicitDirectMessageTarget(String command) {
        if (!availability.isAvailable()) {
            return;
        }
        String[] parts = command.strip().split("\\s+", 3);
        if (parts.length < 2) {
            return;
        }
        String commandName = parts[0].toLowerCase(java.util.Locale.ROOT);
        if (!(commandName.equals("tell") || commandName.equals("msg") || commandName.equals("w"))) {
            return;
        }
        String onlinePlayer = availability.onlinePlayerName(parts[1]).orElse(null);
        if (onlinePlayer == null) {
            return;
        }
        directMessages.find(onlinePlayer)
                .ifPresent(peer -> directMessages.rememberOutgoing(peer.id()));
    }

    private ChatTokenContext currentContext() {
        if (client.player == null || client.level == null) {
            return ChatTokenContext.unavailable();
        }
        return ChatTokenContext.at(client.player.blockPosition());
    }

    private void showError(String translationKey) {
        if (client.player != null) {
            client.player.sendOverlayMessage(Component.translatable(translationKey));
        }
    }
}
