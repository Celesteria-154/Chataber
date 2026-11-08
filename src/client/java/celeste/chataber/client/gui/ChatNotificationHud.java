package celeste.chataber.client.gui;

import celeste.chataber.config.ChatSettings;
import celeste.chataber.message.DirectMessageManager;
import celeste.chataber.message.MessageRoute;
import celeste.chataber.tab.ChatTabManager;
import celeste.chataber.tab.TabNotificationManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.List;

/** 未読先を表示するHUD通知。 */
public final class ChatNotificationHud {
    private static final int HEIGHT = 14;
    private static final int GAP = 2;

    private final Minecraft client;
    private final ChatTabManager tabs;
    private final DirectMessageManager directMessages;
    private final TabNotificationManager notifications;
    private final ChatSettings settings;

    public ChatNotificationHud(
            Minecraft client,
            ChatTabManager tabs,
            DirectMessageManager directMessages,
            TabNotificationManager notifications,
            ChatSettings settings
    ) {
        this.client = client;
        this.tabs = tabs;
        this.directMessages = directMessages;
        this.notifications = notifications;
        this.settings = settings;
    }

    public void register() {
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("chataber", "tab_notifications"),
                (graphics, deltaTracker) -> extract(graphics)
        );
    }

    private void extract(GuiGraphicsExtractor graphics) {
        ChatSettings.State state = settings.state();
        // 設定画面から戻った際、すでに表示中の通知にもタブ別設定を反映
        notifications.applyLifetimePolicy(
                state.hudNotificationSeconds(), state::persistentHudNotifications);
        if (client.gui.screen() != null) {
            return;
        }
        List<TabNotificationManager.Notification> active = notifications.activeNotifications();
        if (active.isEmpty()) {
            return;
        }
        Font font = client.font;
        int x = 2;
        int y = graphics.guiHeight() - 29;
        boolean overflow = false;
        for (TabNotificationManager.Notification notification : active) {
            String label = notification.label();
            int unread = notification.targetId().startsWith("tab:")
                    ? tabs.unreadCount(notification.targetId().substring(4))
                    : unreadCount(notification.route());
            String badge = NotificationBadgeRenderer.text(unread, settings.state().badgeStyle());
            int badgeWidth = badge.isEmpty() ? 0 : font.width(badge) + 3;
            int width = Math.max(34, Math.min(94, font.width(label) + badgeWidth + 8));
            // 右端には、残りの通知を示す「+」と余白の20pxを確保
            if (x + width + 20 > graphics.guiWidth()) {
                overflow = true;
                break;
            }
            graphics.fill(x, y, x + width, y + HEIGHT, 0xAA102020);
            graphics.fill(x, y + HEIGHT - 2, x + width, y + HEIGHT, 0xFF44B8B0);
            int labelWidth = width - 8 - badgeWidth;
            String clipped = clipped(font, label, labelWidth);
            int contentWidth = font.width(clipped) + badgeWidth;
            int textX = x + Math.max(4, (width - contentWidth) / 2);
            graphics.text(font, clipped, textX, y + 2, 0xFFFFFFFF);
            NotificationBadgeRenderer.draw(
                    graphics, font, badge, textX + font.width(clipped) + 3, y + 2);
            x += width + GAP;
        }
        if (overflow) {
            graphics.fill(x, y, x + 16, y + HEIGHT, 0xAA102020);
            graphics.centeredText(font, "+", x + 8, y + 2, 0xFFFFFFFF);
        }
    }

    private int unreadCount(MessageRoute route) {
        if (route instanceof MessageRoute.DirectMessage direct) {
            return directMessages.find(direct.peerId()).map(peer -> peer.unreadCount()).orElse(0);
        }
        if (route instanceof MessageRoute.Category category) {
            return tabs.tabs().stream()
                    .filter(tab -> !ChatTabManager.ALL_TAB_ID.equals(tab.id()))
                    .filter(tab -> tab.matches(category.category()))
                    .mapToInt(tab -> tabs.unreadCount(tab.id()))
                    .max()
                    .orElse(0);
        }
        return 0;
    }

    private static String clipped(Font font, String value, int width) {
        if (font.width(value) <= width) {
            return value;
        }
        return font.plainSubstrByWidth(value, Math.max(0, width - font.width("…"))) + "…";
    }
}
