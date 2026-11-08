package celeste.chataber.client.gui;

import celeste.chataber.client.message.ClientPlayerListChangeBridge;
import celeste.chataber.client.message.DirectMessageAvailability;
import celeste.chataber.config.ChatSettings;
import celeste.chataber.config.NotificationBadgeStyle;
import celeste.chataber.message.DirectMessageManager;
import celeste.chataber.message.DirectMessagePeer;
import celeste.chataber.tab.ChatTab;
import celeste.chataber.tab.ChatTabLayout;
import celeste.chataber.tab.ChatTabManager;
import celeste.chataber.tab.ChatViewManager;
import celeste.chataber.tab.SelectedChatView;
import celeste.chataber.tab.TabNotificationManager;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** チャット画面のタブとポップアップ。 */
public final class ChatTabBar {
    private static final int TAB_GAP = 2;
    private static final int CONTROL_WIDTH = 16;
    private static final int DIVIDER_WIDTH = 8;
    private static final int HEIGHT = 14;

    private final ChatTabManager tabs;
    private final DirectMessageManager directMessages;
    private final ChatViewManager views;
    private final TabNotificationManager notifications;
    private final ChatSettings settings;
    private final DirectMessageAvailability availability;
    private Instance activeInstance;

    public ChatTabBar(
            ChatTabManager tabs,
            DirectMessageManager directMessages,
            ChatViewManager views,
            TabNotificationManager notifications,
            ChatSettings settings,
            DirectMessageAvailability availability
    ) {
        this.tabs = tabs;
        this.directMessages = directMessages;
        this.views = views;
        this.notifications = notifications;
        this.settings = settings;
        this.availability = availability;
    }

    public void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof ChatScreen chatScreen) {
                if (activeInstance != null) {
                    activeInstance.detach();
                }
                activeInstance = new Instance(client, chatScreen, scaledWidth, scaledHeight);
                activeInstance.addWidgets();
            }
        });
    }

    private final class Instance {
        private final Minecraft client;
        private final ChatScreen screen;
        private final int screenWidth;
        private final int screenHeight;
        private final List<TabButton> tabButtons = new ArrayList<>();
        private final Set<String> visibleTabIds = new LinkedHashSet<>();
        private final Map<String, TranslucentControl> dmTabButtons = new LinkedHashMap<>();
        private final Set<String> visibleDmTabIds = new LinkedHashSet<>();
        private final Runnable refreshCallback = this::refreshLayout;
        private TranslucentControl overflowButton;
        private TranslucentControl dmButton;
        private TranslucentControl configureButton;
        private TranslucentControl addPeerButton;
        private Divider divider;
        private PlayerNameEditBox dmSearch;
        private PopupList popup;
        private PopupMode popupMode = PopupMode.NONE;
        private boolean detached;

        private Instance(Minecraft client, ChatScreen screen, int screenWidth, int screenHeight) {
            this.client = client;
            this.screen = screen;
            this.screenWidth = screenWidth;
            this.screenHeight = screenHeight;
        }

        private void addWidgets() {
            int y = screenHeight - 29;
            for (ChatTab tab : tabs.tabs()) {
                TranslucentControl button = TranslucentControl.badged(
                        client.font,
                        tab::name,
                        () -> tabs.unreadCount(tab.id()),
                        () -> settings.state().badgeStyle(),
                        0,
                        y,
                        () -> {
                            views.selectTab(tab.id());
                            notifications.clearVisible(views::isVisible);
                            closePopup();
                            refreshLayout();
                        },
                        () -> views.selectedView() instanceof SelectedChatView.Tab selected
                                && selected.tabId().equals(tab.id())
                );
                tabButtons.add(new TabButton(tab.id(), button));
                Screens.getWidgets(screen).add(button);
            }

            overflowButton = TranslucentControl.plain(
                    client.font, () -> "Tabs ▾", 0, y,
                    () -> togglePopup(PopupMode.TABS), () -> false);
            Screens.getWidgets(screen).add(overflowButton);

            dmButton = TranslucentControl.badged(
                    client.font,
                    () -> "DM ▾",
                    this::overflowDmUnread,
                    () -> settings.state().badgeStyle(),
                    0,
                    y,
                    () -> togglePopup(PopupMode.DIRECT_MESSAGES),
                    () -> false
            );
            Screens.getWidgets(screen).add(dmButton);

            divider = new Divider(0, y);
            Screens.getWidgets(screen).add(divider);

            configureButton = TranslucentControl.fixed(
                    client.font, () -> "+", 0, y, CONTROL_WIDTH,
                    () -> togglePopup(PopupMode.ADD_DIRECT_MESSAGE), () -> false);
            Screens.getWidgets(screen).add(configureButton);

            dmSearch = new PlayerNameEditBox(
                    client.font,
                    2,
                    y - 126,
                    96,
                    16,
                    Component.translatable("chataber.dm.search"),
                    this::addableOnlinePlayerNames
            );
            dmSearch.setMaxLength(16);
            dmSearch.setResponder(ignored -> popup.resetScroll());
            dmSearch.setSubmitHandler(this::addSearchedPeer);
            dmSearch.visible = false;
            Screens.getWidgets(screen).add(dmSearch);

            addPeerButton = TranslucentControl.fixed(
                    client.font, () -> "+", 100, y - 126, CONTROL_WIDTH,
                    this::addSearchedPeer, () -> false);
            addPeerButton.visible = false;
            Screens.getWidgets(screen).add(addPeerButton);

            popup = new PopupList(client.font, 2, y - 108, 114, 104);
            popup.visible = false;
            Screens.getWidgets(screen).add(popup);

            // 画面を閉じる際にリスナーを解除
            directMessages.addListener(refreshCallback);
            tabs.addUnreadListener(refreshCallback);
            ClientPlayerListChangeBridge.setListener(refreshCallback);
            ScreenEvents.remove(screen).register(removed -> {
                detach();
                if (activeInstance == this) {
                    activeInstance = null;
                }
            });
            refreshLayout();
        }

        private void detach() {
            if (detached) {
                return;
            }
            detached = true;
            directMessages.removeListener(refreshCallback);
            tabs.removeUnreadListener(refreshCallback);
            ClientPlayerListChangeBridge.clearListener(refreshCallback);
        }

        private void refreshLayout() {
            // 通常タブの残り幅を先に確保し、その後DM群を同じ行へ詰める。
            boolean dmAvailable = availability.isAvailable();
            if (!dmAvailable && views.selectedView() instanceof SelectedChatView.DirectMessage) {
                views.selectTab(tabs.selectedTabId());
            }

            List<DirectMessagePeer> visibleDmTabs = eligibleVisiblePeers();
            if (views.selectedView() instanceof SelectedChatView.DirectMessage selected
                    && visibleDmTabs.stream().noneMatch(peer -> peer.id().equals(selected.peerId()))) {
                views.selectTab(tabs.selectedTabId());
            }
            synchronizeDmTabButtons(visibleDmTabs);

            for (TabButton tabButton : tabButtons) {
                tabButton.button().fitToContent();
            }
            for (TranslucentControl dmTabButton : dmTabButtons.values()) {
                dmTabButton.fitToContent();
                dmTabButton.visible = false;
            }
            overflowButton.fitToContent();
            dmButton.fitToContent();

            int minimumDmGroup = 0;
            if (dmAvailable) {
                minimumDmGroup = DIVIDER_WIDTH + TAB_GAP;
                if (visibleDmTabs.isEmpty()) {
                    minimumDmGroup += dmButton.getWidth();
                } else {
                    String priorityId = preferredDmId(visibleDmTabs);
                    minimumDmGroup += dmTabButtons.get(priorityId).getWidth();
                    if (visibleDmTabs.size() > 1) {
                        minimumDmGroup += TAB_GAP + dmButton.getWidth();
                    }
                }
            }

            configureButton.visible = true;
            divider.visible = dmAvailable;
            // 追加ボタンとDM群の最小幅を引いた残りを、通常タブへ割り当てる
            int normalAvailable = Math.max(0,
                    screenWidth - 4 - configureButton.getWidth() - TAB_GAP - minimumDmGroup
                            - (dmAvailable ? TAB_GAP : 0));
            List<ChatTabLayout.Item> items = tabButtons.stream()
                    .map(button -> new ChatTabLayout.Item(button.id(), button.button().getWidth()))
                    .toList();
            List<String> priorities = new ArrayList<>();
            priorities.add(ChatTabManager.ALL_TAB_ID);
            if (views.selectedView() instanceof SelectedChatView.Tab selected) {
                priorities.add(selected.tabId());
            }
            ChatTabLayout.Result fitted = ChatTabLayout.fit(
                    normalAvailable,
                    items,
                    priorities,
                    overflowButton.getWidth(),
                    TAB_GAP
            );
            visibleTabIds.clear();
            visibleTabIds.addAll(fitted.visibleIds());
            int x = 2;
            for (TabButton tabButton : tabButtons) {
                boolean visible = visibleTabIds.contains(tabButton.id());
                tabButton.button().visible = visible;
                if (visible) {
                    tabButton.button().setX(x);
                    x += tabButton.button().getWidth() + TAB_GAP;
                }
            }
            overflowButton.visible = fitted.overflow();
            if (overflowButton.visible) {
                overflowButton.setX(x);
                x += overflowButton.getWidth() + TAB_GAP;
            }
            if (dmAvailable) {
                divider.setX(x);
                x += divider.getWidth() + TAB_GAP;
            }

            // DM群は残り幅と設定された表示数の両方で制限し、余りをDM一覧へ移す
            visibleDmTabIds.clear();
            boolean showDmOverflow = false;
            if (dmAvailable && visibleDmTabs.isEmpty()) {
                showDmOverflow = ChatTabLayout.showDirectMessageOverflow(true, 0, 0);
            } else if (dmAvailable) {
                int dmAvailableWidth = Math.max(0,
                        screenWidth - 2 - configureButton.getWidth() - TAB_GAP - x);
                List<ChatTabLayout.Item> dmItems = visibleDmTabs.stream()
                        .map(peer -> new ChatTabLayout.Item(peer.id(), dmTabButtons.get(peer.id()).getWidth()))
                        .toList();
                ChatTabLayout.Result dmFitted = ChatTabLayout.fit(
                        dmAvailableWidth,
                        dmItems,
                        List.of(preferredDmId(visibleDmTabs)),
                        dmButton.getWidth(),
                        TAB_GAP,
                        settings.state().maxVisibleDmTabs()
                );
                visibleDmTabIds.addAll(dmFitted.visibleIds());
                for (DirectMessagePeer peer : visibleDmTabs) {
                    TranslucentControl button = dmTabButtons.get(peer.id());
                    button.visible = visibleDmTabIds.contains(peer.id());
                    if (button.visible) {
                        button.setX(x);
                        x += button.getWidth() + TAB_GAP;
                    }
                }
                showDmOverflow = ChatTabLayout.showDirectMessageOverflow(
                        true,
                        visibleDmTabs.size(),
                        visibleDmTabs.size() - visibleDmTabIds.size()
                );
            }
            dmButton.visible = showDmOverflow;
            if (showDmOverflow) {
                dmButton.setX(x);
                x += dmButton.getWidth() + TAB_GAP;
            }
            if (!showDmOverflow && popupMode == PopupMode.DIRECT_MESSAGES) {
                closePopup();
            }
            configureButton.setX(x);
            positionPopup();
        }

        private List<DirectMessagePeer> eligibleVisiblePeers() {
            return directMessages.visiblePeers().stream()
                    .filter(availability::isEligiblePeer)
                    .filter(peer -> peer.lastActivity() > 0
                            || availability.isOnlinePlayerName(peer.playerName()))
                    .toList();
        }

        private void synchronizeDmTabButtons(List<DirectMessagePeer> peers) {
            int y = screenHeight - 29;
            for (DirectMessagePeer peer : peers) {
                if (dmTabButtons.containsKey(peer.id())) {
                    continue;
                }
                String peerId = peer.id();
                String playerName = peer.playerName();
                TranslucentControl button = TranslucentControl.badged(
                        client.font,
                        () -> directMessages.find(peerId)
                                .map(DirectMessagePeer::shownName)
                                .orElse(playerName),
                        () -> directMessages.find(peerId)
                                .map(DirectMessagePeer::unreadCount)
                                .orElse(0),
                        () -> settings.state().badgeStyle(),
                        0,
                        y,
                        () -> selectPeer(playerName),
                        () -> views.selectedView() instanceof SelectedChatView.DirectMessage selected
                                && selected.peerId().equals(peerId)
                );
                dmTabButtons.put(peerId, button);
                Screens.getWidgets(screen).add(button);
            }
        }

        private String preferredDmId(List<DirectMessagePeer> visible) {
            Set<String> ids = visible.stream().map(DirectMessagePeer::id).collect(
                    java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            if (views.selectedView() instanceof SelectedChatView.DirectMessage selected
                    && ids.contains(selected.peerId())) {
                return selected.peerId();
            }
            return directMessages.currentPeerId()
                    .filter(ids::contains)
                    .orElse(visible.getFirst().id());
        }

        // 直接表示しているDMタブの未読は、一覧ボタンへ二重計上しない
        private int overflowDmUnread() {
            return eligibleVisiblePeers().stream()
                    .filter(peer -> !visibleDmTabIds.contains(peer.id()))
                    .mapToInt(DirectMessagePeer::unreadCount)
                    .sum();
        }

        // 開いたボタンの位置を基準に、ポップアップを画面の横幅へ収める
        private void positionPopup() {
            AbstractWidget anchor = switch (popupMode) {
                case TABS -> overflowButton;
                case ADD_DIRECT_MESSAGE -> configureButton;
                default -> dmButton;
            };
            int popupX = Math.max(2, Math.min(screenWidth - popup.getWidth() - 2,
                    anchor.getX() + anchor.getWidth() - popup.getWidth()));
            popup.setX(popupX);
            dmSearch.setX(popupX);
            addPeerButton.setX(popupX + dmSearch.getWidth() + 2);
        }

        private List<PopupEntry> popupEntries() {
            if (popupMode == PopupMode.TABS) {
                return tabs.tabs().stream()
                        .filter(tab -> !visibleTabIds.contains(tab.id()))
                        .map(tab -> new PopupEntry(
                                tab.name(),
                                tabs.unreadCount(tab.id()),
                                () -> {
                                    views.selectTab(tab.id());
                                    notifications.clearVisible(views::isVisible);
                                    closePopup();
                                    refreshLayout();
                                },
                                false))
                        .toList();
            }
            if (popupMode != PopupMode.DIRECT_MESSAGES
                    && popupMode != PopupMode.ADD_DIRECT_MESSAGE) {
                return List.of();
            }

            String query = dmSearch.getValue().strip().toLowerCase(Locale.ROOT);
            List<PopupEntry> entries = new ArrayList<>();
            if (popupMode == PopupMode.ADD_DIRECT_MESSAGE) {
                List<String> online = addableOnlinePlayerNames().stream()
                        .filter(name -> query.isEmpty() || name.toLowerCase(Locale.ROOT).startsWith(query))
                        .toList();
                if (!online.isEmpty()) {
                    entries.add(PopupEntry.header(
                            Component.translatable("chataber.dm.online").getString()));
                    online.forEach(playerName -> entries.add(
                            new PopupEntry(playerName, 0, () -> selectPeer(playerName), false)));
                }
                return entries;
            }

            List<DirectMessagePeer> known = eligibleVisiblePeers().stream()
                    .filter(peer -> !visibleDmTabIds.contains(peer.id()))
                    .filter(peer -> matches(peer, query))
                    .toList();
            if (query.isEmpty() && known.stream().anyMatch(DirectMessagePeer::pinned)) {
                entries.add(PopupEntry.header(Component.translatable("chataber.dm.pinned").getString()));
            }
            known.stream().filter(DirectMessagePeer::pinned).forEach(peer -> entries.add(peerEntry(peer)));
            if (query.isEmpty() && known.stream().anyMatch(peer -> !peer.pinned())) {
                entries.add(PopupEntry.header(Component.translatable("chataber.dm.recent").getString()));
            }
            known.stream().filter(peer -> !peer.pinned()).forEach(peer -> entries.add(peerEntry(peer)));
            return entries;
        }

        private List<String> onlinePlayerNames() {
            Map<String, String> unique = new LinkedHashMap<>();
            availability.eligibleOnlinePlayers().forEach(player -> {
                String name = player.getProfile().name();
                unique.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
            });
            return List.copyOf(unique.values());
        }

        private List<String> addableOnlinePlayerNames() {
            return onlinePlayerNames().stream()
                    .filter(this::isAddablePlayerName)
                    .toList();
        }

        private boolean isAddablePlayerName(String playerName) {
            return directMessages.find(playerName)
                    .map(peer -> !peer.listed() && !peer.pinned())
                    .orElse(true);
        }

        private boolean matches(DirectMessagePeer peer, String query) {
            return query.isEmpty()
                    || peer.playerName().toLowerCase(Locale.ROOT).contains(query)
                    || peer.shownName().toLowerCase(Locale.ROOT).contains(query);
        }

        private PopupEntry peerEntry(DirectMessagePeer peer) {
            return new PopupEntry(
                    (peer.pinned() ? "★ " : "") + peer.shownName(),
                    peer.unreadCount(),
                    () -> selectPeer(peer.playerName()),
                    false
            );
        }

        private void selectPeer(String playerName) {
            DirectMessagePeer existing = directMessages.find(playerName).orElse(null);
            String resolved = existing == null
                    ? availability.onlinePlayerName(playerName).orElse(null)
                    : existing.playerName();
            if (resolved == null || !availability.isEligiblePlayerName(resolved)) {
                return;
            }
            DirectMessagePeer peer = directMessages.ensurePeer(resolved);
            views.selectDirectMessage(peer.id());
            notifications.clearVisible(views::isVisible);
            closePopup();
            refreshLayout();
        }

        private void togglePopup(PopupMode target) {
            if (popupMode == target) {
                closePopup();
                return;
            }
            popupMode = target;
            popup.resetScroll();
            popup.visible = true;
            boolean searchable = target == PopupMode.DIRECT_MESSAGES
                    || target == PopupMode.ADD_DIRECT_MESSAGE;
            dmSearch.visible = searchable;
            addPeerButton.visible = target == PopupMode.ADD_DIRECT_MESSAGE;
            positionPopup();
            if (searchable) {
                screen.setFocused(dmSearch);
            }
        }

        private void closePopup() {
            popupMode = PopupMode.NONE;
            popup.visible = false;
            dmSearch.visible = false;
            addPeerButton.visible = false;
            restoreChatInputFocus();
        }

        private void addSearchedPeer() {
            availability.onlinePlayerName(dmSearch.getValue())
                    .filter(ignored -> availability.isAvailable())
                    .filter(this::isAddablePlayerName)
                    .ifPresent(this::selectPeer);
        }

        private void restoreChatInputFocus() {
            screen.children().stream()
                    .filter(child -> child instanceof EditBox && child != dmSearch)
                    .findFirst()
                    .ifPresent(screen::setFocused);
        }

        private final class PopupList extends AbstractWidget {
            private static final int ROW_HEIGHT = 13;
            private final Font font;
            private int scrollOffset;

            private PopupList(Font font, int x, int y, int width, int height) {
                super(x, y, width, height, Component.empty());
                this.font = font;
            }

            @Override
            protected void extractWidgetRenderState(
                    @NonNull GuiGraphicsExtractor graphics,
                    int mouseX,
                    int mouseY,
                    float partialTick
            ) {
                List<PopupEntry> entries = popupEntries();
                int capacity = getHeight() / ROW_HEIGHT;
                scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, entries.size() - capacity)));
                int rows = Math.max(0, Math.min(entries.size() - scrollOffset, capacity));
                graphics.fill(getX(), getY(), getRight(), getY() + rows * ROW_HEIGHT, 0xDD101010);
                for (int index = 0; index < rows; index++) {
                    PopupEntry entry = entries.get(scrollOffset + index);
                    int rowY = getY() + index * ROW_HEIGHT;
                    if (!entry.header() && mouseX >= getX() && mouseX < getRight()
                            && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                        graphics.fill(getX(), rowY, getRight(), rowY + ROW_HEIGHT, 0xAA406060);
                    }
                    drawLabel(graphics, font, entry.label(), entry.unread(), settings.state().badgeStyle(),
                            getX() + 3, rowY + 2, getWidth() - 6,
                            entry.header() ? 0xFF8ABDB8 : 0xFFFFFFFF);
                }
                handleCursor(graphics);
            }

            @Override
            public void onClick(@NonNull MouseButtonEvent event, boolean doubleClick) {
                int index = (int) ((event.y() - getY()) / ROW_HEIGHT);
                List<PopupEntry> entries = popupEntries();
                int target = scrollOffset + index;
                if (index >= 0 && target < entries.size() && index < getHeight() / ROW_HEIGHT) {
                    PopupEntry entry = entries.get(target);
                    if (!entry.header()) {
                        entry.action().run();
                    }
                }
            }

            @Override
            public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
                if (!isMouseOver(mouseX, mouseY)) {
                    return false;
                }
                int capacity = getHeight() / ROW_HEIGHT;
                int max = Math.max(0, popupEntries().size() - capacity);
                scrollOffset = Math.max(0, Math.min(max, scrollOffset - (int) Math.signum(scrollY)));
                return true;
            }

            private void resetScroll() {
                scrollOffset = 0;
            }

            @Override
            protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
                defaultButtonNarrationText(output);
            }
        }
    }

    private record TabButton(String id, TranslucentControl button) {
    }

    private record PopupEntry(String label, int unread, Runnable action, boolean header) {
        private static PopupEntry header(String label) {
            return new PopupEntry(label, 0, () -> { }, true);
        }
    }

    private enum PopupMode {
        NONE,
        TABS,
        DIRECT_MESSAGES,
        ADD_DIRECT_MESSAGE
    }

    private static final class Divider extends AbstractWidget {
        private Divider(int x, int y) {
            super(x, y, DIVIDER_WIDTH, HEIGHT, Component.empty());
        }

        @Override
        protected void extractWidgetRenderState(
                @NonNull GuiGraphicsExtractor graphics,
                int mouseX,
                int mouseY,
                float partialTick
        ) {
            int center = getX() + getWidth() / 2;
            graphics.fill(center, getY() + 2, center + 1, getBottom() - 2, 0xFF6A8583);
        }

        @Override
        public void onClick(@NonNull MouseButtonEvent event, boolean doubleClick) {
        }

        @Override
        public boolean shouldTakeFocusAfterInteraction() {
            return false;
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
        }
    }

    private static final class TranslucentControl extends AbstractWidget {
        private static final int NORMAL_BACKGROUND = 0x66000000;
        private static final int HOVER_BACKGROUND = 0x88404040;
        private static final int SELECTED_BACKGROUND = 0xAA202020;
        private static final int DISABLED_BACKGROUND = 0x44000000;
        private static final int ACCENT = 0xFF44B8B0;
        private static final int MIN_CONTENT_WIDTH = 18;
        private static final int MAX_CONTENT_WIDTH = 120;
        private static final int HORIZONTAL_PADDING = 3;
        private static final int BADGE_GAP = 2;

        private final Font font;
        private final Supplier<String> label;
        private final IntSupplier unread;
        private final Supplier<NotificationBadgeStyle> badgeStyle;
        private final Runnable onPress;
        private final BooleanSupplier selected;
        private final boolean contentSized;
        private final boolean reserveBadge;

        private TranslucentControl(
                Font font,
                Supplier<String> label,
                IntSupplier unread,
                Supplier<NotificationBadgeStyle> badgeStyle,
                int x,
                int y,
                int width,
                boolean contentSized,
                boolean reserveBadge,
                Runnable onPress,
                BooleanSupplier selected
        ) {
            super(x, y, width, HEIGHT, Component.empty());
            this.font = font;
            this.label = label;
            this.unread = unread;
            this.badgeStyle = badgeStyle;
            this.contentSized = contentSized;
            this.reserveBadge = reserveBadge;
            this.onPress = onPress;
            this.selected = selected;
        }

        private static TranslucentControl plain(
                Font font,
                Supplier<String> label,
                int x,
                int y,
                Runnable onPress,
                BooleanSupplier selected
        ) {
            return new TranslucentControl(font, label, () -> 0, () -> NotificationBadgeStyle.DOT,
                    x, y, MIN_CONTENT_WIDTH, true, false, onPress, selected);
        }

        private static TranslucentControl badged(
                Font font,
                Supplier<String> label,
                IntSupplier unread,
                Supplier<NotificationBadgeStyle> badgeStyle,
                int x,
                int y,
                Runnable onPress,
                BooleanSupplier selected
        ) {
            return new TranslucentControl(font, label, unread, badgeStyle,
                    x, y, MIN_CONTENT_WIDTH, true, true, onPress, selected);
        }

        private static TranslucentControl fixed(
                Font font,
                Supplier<String> label,
                int x,
                int y,
                int width,
                Runnable onPress,
                BooleanSupplier selected
        ) {
            return new TranslucentControl(font, label, () -> 0, () -> NotificationBadgeStyle.DOT,
                    x, y, width, false, false, onPress, selected);
        }

        private void fitToContent() {
            if (!contentSized) {
                return;
            }
            String badge = reserveBadge ? NotificationBadgeRenderer.text(unread.getAsInt(), badgeStyle.get()) : "";
            int badgeWidth = badge.isEmpty() ? 0 : BADGE_GAP + font.width(badge);
            setWidth(Math.max(MIN_CONTENT_WIDTH,
                    Math.min(MAX_CONTENT_WIDTH,
                            font.width(label.get()) + HORIZONTAL_PADDING * 2 + badgeWidth)));
        }

        @Override
        protected void extractWidgetRenderState(
                @NonNull GuiGraphicsExtractor graphics,
                int mouseX,
                int mouseY,
                float partialTick
        ) {
            boolean isSelected = selected.getAsBoolean();
            int background = !active
                    ? DISABLED_BACKGROUND
                    : isSelected ? SELECTED_BACKGROUND : isHovered() ? HOVER_BACKGROUND : NORMAL_BACKGROUND;
            graphics.fill(getX(), getY(), getRight(), getBottom(), background);
            if (isSelected) {
                graphics.fill(getX(), getBottom() - 2, getRight(), getBottom(), ACCENT);
            }
            drawLabel(graphics, font, label.get(), unread.getAsInt(), badgeStyle.get(),
                    getX() + HORIZONTAL_PADDING, getY() + (getHeight() - font.lineHeight) / 2,
                    getWidth() - HORIZONTAL_PADDING * 2, active ? 0xFFFFFFFF : 0xFF888888);
            handleCursor(graphics);
        }

        @Override
        public void onClick(@NonNull MouseButtonEvent event, boolean doubleClick) {
            onPress.run();
        }

        // タブをクリックしても、チャット入力欄からキーボードフォーカスを奪わない
        @Override
        public boolean shouldTakeFocusAfterInteraction() {
            return false;
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private static void drawLabel(
            @NonNull GuiGraphicsExtractor graphics,
            Font font,
            String label,
            int unread,
            NotificationBadgeStyle style,
            int x,
            int y,
            int width,
            int textColor
    ) {
        String badge = NotificationBadgeRenderer.text(unread, style);
        int badgeWidth = badge.isEmpty() ? 0 : font.width(badge) + TranslucentControl.BADGE_GAP;
        // バッジの幅を先に確保し、収まらないタブ名だけ省略
        int labelWidth = Math.max(0, width - badgeWidth);
        String clipped = label;
        if (font.width(clipped) > labelWidth) {
            int contentWidth = Math.max(0, labelWidth - font.width("…"));
            clipped = font.plainSubstrByWidth(clipped, contentWidth) + "…";
        }
        graphics.text(font, clipped, x, y, textColor);
        NotificationBadgeRenderer.draw(
                graphics,
                font,
                badge,
                x + Math.min(font.width(clipped), labelWidth) + TranslucentControl.BADGE_GAP,
                y
        );
    }
}
