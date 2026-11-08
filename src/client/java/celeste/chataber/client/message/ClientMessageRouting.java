package celeste.chataber.client.message;

import celeste.chataber.message.MessageRoute;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/** 受信イベントの経路情報を生成直後のGuiMessageへ関連付ける。 */
public final class ClientMessageRouting {
    // 受信イベントはGuiMessage生成前なので、一時キューで同じメッセージへ引き渡す。
    private static final ThreadLocal<Deque<PendingRoute>> PENDING =
            ThreadLocal.withInitial(ArrayDeque::new);
    // 同じ文面が連続しても、生成されたGuiMessageのインスタンスごとに区別
    private static final Map<GuiMessage, MessageRoute> ROUTES = new IdentityHashMap<>();
    private static final Map<GuiMessage, Component> DIRECT_MESSAGE_PRESENTATIONS = new IdentityHashMap<>();
    private static String selectedDirectMessagePeerId;

    private ClientMessageRouting() {
    }

    public static void enqueue(
            Component component,
            MessageSignature signature,
            MessageRoute route
    ) {
        enqueue(component, signature, route, null);
    }

    public static void enqueue(
            Component component,
            MessageSignature signature,
            MessageRoute route,
            Component directMessagePresentation
    ) {
        PENDING.get().addLast(new PendingRoute(component, signature, route, directMessagePresentation));
    }

    public static void attachPendingRoute(GuiMessage message) {
        Deque<PendingRoute> pending = PENDING.get();
        while (!pending.isEmpty()) {
            PendingRoute candidate = pending.removeFirst();
            if (candidate.matches(message)) {
                synchronized (ROUTES) {
                    ROUTES.put(message, candidate.route());
                    if (candidate.directMessagePresentation() != null) {
                        DIRECT_MESSAGE_PRESENTATIONS.put(message, candidate.directMessagePresentation());
                    }
                }
                return;
            }
        }
    }

    public static Optional<MessageRoute> routeFor(GuiMessage message) {
        synchronized (ROUTES) {
            return Optional.ofNullable(ROUTES.get(message));
        }
    }

    /** DMビュー内だけ会話用の本文へ差し替える。Allなどでは元の受信文を表示。 */
    public static Component displayContent(GuiMessage message, Component original) {
        synchronized (ROUTES) {
            MessageRoute route = ROUTES.get(message);
            if (route instanceof MessageRoute.DirectMessage direct
                    && direct.peerId().equals(selectedDirectMessagePeerId)) {
                return DIRECT_MESSAGE_PRESENTATIONS.getOrDefault(message, original);
            }
            return original;
        }
    }

    public static void setDirectMessageView(String peerId) {
        synchronized (ROUTES) {
            selectedDirectMessagePeerId = peerId == null ? null : MessageRoute.normalizePeerId(peerId);
        }
    }

    /** Vanilla履歴から消えたメッセージの経路と表示文も解放する。 */
    public static void retainAll(Collection<GuiMessage> retainedMessages) {
        synchronized (ROUTES) {
            ROUTES.keySet().removeIf(message -> !containsIdentity(retainedMessages, message));
            DIRECT_MESSAGE_PRESENTATIONS.keySet()
                    .removeIf(message -> !containsIdentity(retainedMessages, message));
        }
    }

    public static void clear() {
        PENDING.remove();
        synchronized (ROUTES) {
            ROUTES.clear();
            DIRECT_MESSAGE_PRESENTATIONS.clear();
            selectedDirectMessagePeerId = null;
        }
    }

    private static boolean containsIdentity(Collection<GuiMessage> messages, GuiMessage target) {
        for (GuiMessage message : messages) {
            if (message == target) {
                return true;
            }
        }
        return false;
    }

    private record PendingRoute(
            Component component,
            MessageSignature signature,
            MessageRoute route,
            Component directMessagePresentation
    ) {
        private boolean matches(GuiMessage message) {
            // 署名を持つメッセージは署名で照合し、それ以外はComponentで照合
            if (signature != null && message.signature() != null) {
                return signature.equals(message.signature());
            }
            return component.equals(message.content());
        }
    }
}
