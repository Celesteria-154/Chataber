package celeste.chataber.client.gui;

import celeste.chataber.config.NotificationBadgeStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** 未読バッジと点滅する縁取りの描画。 */
final class NotificationBadgeRenderer {
    private static final int BADGE_COLOR = 0xFF44B8B0;
    private static final int OUTLINE_COLOR = 0xFFFFFFFF;
    private static final long OUTLINE_BLINK_MILLIS = 500L;

    private NotificationBadgeRenderer() {
    }

    static String text(int unread, NotificationBadgeStyle style) {
        if (unread <= 0) {
            return "";
        }
        return style == NotificationBadgeStyle.DOT
                ? "●"
                : unread >= 100 ? "99+" : Integer.toString(unread);
    }

    static void draw(GuiGraphicsExtractor graphics, Font font, String badge, int x, int y) {
        if (badge.isEmpty()) {
            return;
        }
        // 本体は常に残し、白い1pxの輪郭だけを一定間隔で表示・非表示にする。
        if (outlineVisible(System.currentTimeMillis())) {
            for (int offsetY = -1; offsetY <= 1; offsetY++) {
                for (int offsetX = -1; offsetX <= 1; offsetX++) {
                    if (offsetX != 0 || offsetY != 0) {
                        graphics.text(font, badge, x + offsetX, y + offsetY, OUTLINE_COLOR);
                    }
                }
            }
        }
        graphics.text(font, badge, x, y, BADGE_COLOR);
    }

    static boolean outlineVisible(long timeMillis) {
        return Math.floorMod(timeMillis / OUTLINE_BLINK_MILLIS, 2L) == 0L;
    }
}
