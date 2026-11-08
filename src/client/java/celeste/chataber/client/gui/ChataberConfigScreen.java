package celeste.chataber.client.gui;

import celeste.chataber.client.message.DirectMessageAvailability;
import celeste.chataber.config.ChatSettings;
import celeste.chataber.config.ChataberManagementDraft;
import celeste.chataber.config.ConfigManager;
import celeste.chataber.message.DirectMessageManager;
import celeste.chataber.message.DirectMessagePeer;
import celeste.chataber.message.MessageCategory;
import celeste.chataber.tab.ChatTab;
import celeste.chataber.tab.ChatTabManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** タブ・DM相手の管理画面。 */
public final class ChataberConfigScreen extends Screen {
    private static final int MAX_PANEL_WIDTH = 380;
    private static final int MAX_SECTION_WIDTH = 120;
    private static final int MAX_EDITOR_WIDTH = 220;
    private static final int MAX_ADD_ROW_WIDTH = 300;
    private static final int ROW_HEIGHT = 22;

    private final Screen parent;
    private final ChatTabManager liveTabs;
    private final DirectMessageManager liveDirectMessages;
    private final ChatSettings liveSettings;
    private final ConfigManager configManager;
    private final DirectMessageAvailability availability;
    private final ChataberManagementDraft draft;
    private Section section;
    private String editingTabId;
    private String editingPeerId;
    private int page;
    private boolean advancedFilters;
    private final Map<String, String> dmDisplayOrderInputs = new HashMap<>();
    private boolean dmOrderLabelVisible;
    private boolean dmOrderLabelEnabled;
    private int dmOrderLabelX;
    private int dmOrderLabelY;
    private Button doneButton;

    public ChataberConfigScreen(
            Screen parent,
            ChatTabManager tabs,
            DirectMessageManager directMessages,
            ChatSettings settings,
            ConfigManager configManager,
            DirectMessageAvailability availability,
            Section initialSection
    ) {
        super(Component.translatable("chataber.config.title"));
        this.parent = parent;
        this.liveTabs = tabs;
        this.liveDirectMessages = directMessages;
        this.liveSettings = settings;
        this.configManager = configManager;
        this.availability = availability;
        this.draft = new ChataberManagementDraft(tabs, directMessages, settings);
        this.section = initialSection;
        this.editingTabId = tabs.selectedTabId();
        this.editingPeerId = directMessages.allPeers().stream()
                .filter(availability::isEligiblePeer)
                .findFirst()
                .map(DirectMessagePeer::id)
                .orElse(null);
    }

    @Override
    protected void init() {
        // 画面サイズと編集中の設定からUIを再構築
        dmOrderLabelVisible = false;
        int panelWidth = panelWidth();
        int panelX = (width - panelWidth) / 2;
        int sectionWidth = Math.min(MAX_SECTION_WIDTH, (panelWidth - 4) / 2);
        int sectionX = (width - sectionWidth * 2 - 4) / 2;
        addSectionButton(Section.TABS, "chataber.config.section.tabs", sectionX, sectionWidth);
        addSectionButton(Section.DIRECT_MESSAGES, "chataber.config.section.dm",
                sectionX + sectionWidth + 4, sectionWidth);

        if (section == Section.TABS) {
            initTabs(panelX, panelWidth, 56);
        } else {
            initDirectMessages(panelX, panelWidth, 56);
        }

        int bottomWidth = Math.max(70, Math.min(150, (width - 28) / 2));
        int bottomGap = 8;
        int bottomX = (width - bottomWidth * 2 - bottomGap) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> returnToParent())
                .bounds(bottomX, height - 30, bottomWidth, 20)
                .build());
        doneButton = Button.builder(Component.translatable("gui.done"), button -> commitAndReturn())
                .bounds(bottomX + bottomWidth + bottomGap, height - 30, bottomWidth, 20)
                .build();
        addRenderableWidget(doneButton);
        updateDoneState();
    }

    private void addSectionButton(Section target, String key, int x, int buttonWidth) {
        Button button = Button.builder(Component.translatable(key), ignored -> {
                    section = target;
                    page = 0;
                    rebuildWidgets();
                }).bounds(x, 28, buttonWidth, 20).build();
        button.active = section != target;
        addRenderableWidget(button);
    }

    private void initTabs(int panelX, int panelWidth, int panelTop) {
        ChatTabManager tabs = draft.tabs();
        List<ChatTab> tabList = tabs.tabs();
        if (tabs.findById(editingTabId).isEmpty()) {
            editingTabId = tabList.getFirst().id();
        }

        // 狭い画面では一覧と編集欄を縦に配置し、高度な設定を開いたら編集欄を優先
        boolean stacked = panelWidth < 340;
        int listWidth = stacked ? panelWidth : Math.max(100, Math.min(142, panelWidth / 3));
        int availableRows = stacked
                ? (advancedFilters ? 0 : Math.max(1, Math.min(4, (height - 220) / ROW_HEIGHT + 1)))
                : Math.max(3, Math.min(7, (height - 150) / ROW_HEIGHT));
        int editorTop;
        if (availableRows > 0) {
            int pageCount = Math.max(1, (tabList.size() + availableRows - 1) / availableRows);
            page = Math.max(0, Math.min(page, pageCount - 1));
            int start = page * availableRows;
            int end = Math.min(tabList.size(), start + availableRows);
            for (int index = start; index < end; index++) {
                ChatTab tab = tabList.get(index);
                Button select = Button.builder(Component.literal(tab.name()), button -> {
                            editingTabId = tab.id();
                            rebuildWidgets();
                        }).bounds(panelX, panelTop + (index - start) * ROW_HEIGHT, listWidth, 20).build();
                select.active = !tab.id().equals(editingTabId);
                addRenderableWidget(select);
            }
            int pagerY = panelTop + availableRows * ROW_HEIGHT + 2;
            addTabPager(panelX, listWidth, pagerY, pageCount, availableRows);
            editorTop = stacked ? pagerY + 26 : panelTop;
        } else {
            editorTop = panelTop;
        }

        ChatTab editing = tabs.findById(editingTabId).orElse(tabList.getFirst());
        int editorAreaX = stacked ? panelX : panelX + listWidth + 18;
        int editorAreaWidth = stacked ? panelWidth : panelWidth - listWidth - 18;
        int editorWidth = Math.min(MAX_EDITOR_WIDTH, editorAreaWidth);
        int editorX = editorAreaX + (editorAreaWidth - editorWidth) / 2;
        EditBox name = new EditBox(font, editorX, editorTop, editorWidth, 20,
                Component.translatable("chataber.config.name"));
        name.setMaxLength(ChatTabManager.MAX_TAB_NAME_LENGTH);
        name.setValue(editing.name());
        name.setResponder(value -> tabs.rename(editing.id(), value));
        addRenderableWidget(name);

        int editingIndex = tabList.indexOf(editing);
        int halfWidth = (editorWidth - 4) / 2;
        Button moveLeft = Button.builder(Component.translatable("chataber.config.move_left"), button -> {
                    tabs.move(editing.id(), -1);
                    rebuildWidgets();
                }).bounds(editorX, editorTop + 26, halfWidth, 20).build();
        moveLeft.active = editingIndex > 0;
        addRenderableWidget(moveLeft);
        Button moveRight = Button.builder(Component.translatable("chataber.config.move_right"), button -> {
                    tabs.move(editing.id(), 1);
                    rebuildWidgets();
                }).bounds(editorX + halfWidth + 4, editorTop + 26, halfWidth, 20).build();
        moveRight.active = editingIndex >= 0 && editingIndex < tabList.size() - 1;
        addRenderableWidget(moveRight);
        Button delete = Button.builder(Component.translatable("chataber.config.delete"), button -> {
                    tabs.delete(editing.id());
                    editingTabId = tabs.tabs().getFirst().id();
                    rebuildWidgets();
                }).bounds(editorX, editorTop + 52, editorWidth, 20).build();
        delete.active = !ChatTabManager.ALL_TAB_ID.equals(editing.id());
        addRenderableWidget(delete);

        int advancedY = editorTop + 74;
        Checkbox persistentNotification = Checkbox.builder(
                        Component.translatable("chataber.config.notification.persistent"), font)
                .pos(editorX, advancedY)
                .maxWidth(editorWidth)
                .selected(draft.persistentHudNotifications("tab:" + editing.id()))
                .onValueChange((box, selected) -> draft.setPersistentHudNotifications("tab:" + editing.id(), selected))
                .build();
        persistentNotification.setTooltip(Tooltip.create(
                Component.translatable("chataber.config.notification.persistent.tooltip")));
        persistentNotification.active = !ChatTabManager.ALL_TAB_ID.equals(editing.id());
        if (!persistentNotification.active) {
            persistentNotification.setAlpha(0.45F);
            persistentNotification.setTooltip(Tooltip.create(
                    Component.translatable("chataber.config.notification.persistent.all")));
        }
        addRenderableWidget(persistentNotification);

        int advancedToggleY = advancedY + 18;
        addRenderableWidget(new TextToggle(
                font,
                editorX,
                advancedToggleY,
                editorWidth,
                () -> Component.translatable(advancedFilters
                        ? "chataber.config.advanced.hide"
                        : "chataber.config.advanced.show").getString(),
                () -> {
                    advancedFilters = !advancedFilters;
                    rebuildWidgets();
                }
        ));
        if (advancedFilters) {
            int filterGap = 18;
            addCategoryCheckbox(editing, MessageCategory.PLAYER_CHAT, "chataber.category.player_chat",
                    editorX, advancedToggleY + 16, editorWidth);
            addDirectMessageCheckbox(editing, editorX, advancedToggleY + 16 + filterGap, editorWidth);
            addCategoryCheckbox(editing, MessageCategory.SYSTEM, "chataber.category.system",
                    editorX, advancedToggleY + 16 + filterGap * 2, editorWidth);
            addCategoryCheckbox(editing, MessageCategory.COMMAND_FEEDBACK, "chataber.category.command_feedback",
                    editorX, advancedToggleY + 16 + filterGap * 3, editorWidth);
        }
    }

    private void addTabPager(int panelX, int listWidth, int pagerY, int pageCount, int rows) {
        Button previous = Button.builder(Component.literal("<"), button -> {
                    page--;
                    rebuildWidgets();
                }).bounds(panelX, pagerY, 26, 20).build();
        previous.active = page > 0;
        addRenderableWidget(previous);
        addRenderableWidget(Button.builder(Component.translatable("chataber.config.new_tab"), button -> {
                    ChatTab created = draft.tabs().create("New Tab");
                    editingTabId = created.id();
                    page = Math.max(0, (draft.tabs().tabs().size() - 1) / rows);
                    rebuildWidgets();
                }).bounds(panelX + 30, pagerY, Math.max(30, listWidth - 60), 20).build());
        Button next = Button.builder(Component.literal(">"), button -> {
                    page++;
                    rebuildWidgets();
                }).bounds(panelX + listWidth - 26, pagerY, 26, 20).build();
        next.active = page + 1 < pageCount;
        addRenderableWidget(next);
    }

    private void initDirectMessages(int panelX, int panelWidth, int panelTop) {
        DirectMessageManager directMessages = draft.directMessages();
        // 高さが足りない場合は候補リストを省略し、編集項目と完了ボタンの領域を確保
        boolean compact = height < 300;
        int addRowWidth = Math.min(MAX_ADD_ROW_WIDTH, panelWidth);
        int addRowX = panelX + (panelWidth - addRowWidth) / 2;
        int addWidth = Math.max(42, Math.min(70, addRowWidth / 4));
        PlayerNameEditBox playerSearch = new PlayerNameEditBox(
                font,
                addRowX,
                panelTop,
                addRowWidth - addWidth - 4,
                20,
                Component.translatable("chataber.config.dm.player"),
                this::availableOnlinePlayerNames
        );
        playerSearch.setMaxLength(16);
        addRenderableWidget(playerSearch);
        Button addPlayer = Button.builder(Component.translatable("chataber.config.dm.add"), button -> {
                    addPlayer(playerSearch);
                }).bounds(addRowX + addRowWidth - addWidth, panelTop, addWidth, 20).build();
        addPlayer.active = false;
        playerSearch.setResponder(value -> addPlayer.active = resolvePlayerName(value) != null);
        playerSearch.setSubmitHandler(() -> addPlayer(playerSearch));
        addRenderableWidget(addPlayer);
        if (!compact) {
            addRenderableWidget(new PlayerNameSuggestionList(
                font,
                addRowX,
                panelTop + 22,
                playerSearch.getWidth(),
                52,
                playerSearch,
                this::availableOnlinePlayerNames
            ));
        }

        List<DirectMessagePeer> peers = directMessages.allPeers().stream()
                .filter(availability::isEligiblePeer)
                .toList();
        boolean stacked = panelWidth < 340;
        int listWidth = stacked ? panelWidth - (compact ? 60 : 0)
                : Math.max(110, Math.min(162, panelWidth / 3 + 20));
        int listTop = panelTop + (compact ? 28 : 80);
        int availableRows = stacked ? 1 : Math.max(2, Math.min(4, (height - listTop - 70) / ROW_HEIGHT));
        int pageCount = Math.max(1, (peers.size() + availableRows - 1) / availableRows);
        page = Math.max(0, Math.min(page, pageCount - 1));
        int start = page * availableRows;
        int end = Math.min(peers.size(), start + availableRows);
        for (int index = start; index < end; index++) {
            DirectMessagePeer peer = peers.get(index);
            Button select = Button.builder(
                            Component.literal((peer.pinned() ? "★ " : "") + peer.shownName()),
                            button -> {
                                editingPeerId = peer.id();
                                rebuildWidgets();
                            }).bounds(panelX, listTop + (index - start) * ROW_HEIGHT, listWidth, 20).build();
            select.active = !peer.id().equals(editingPeerId);
            addRenderableWidget(select);
        }

        int pagerY = listTop + availableRows * ROW_HEIGHT + 2;
        if (stacked && compact) {
            Button previous = Button.builder(Component.literal("<"), button -> {
                page--;
                rebuildWidgets();
            }).bounds(panelX + listWidth + 4, listTop, 26, 20).build();
            previous.active = page > 0;
            addRenderableWidget(previous);
            Button next = Button.builder(Component.literal(">"), button -> {
                page++;
                rebuildWidgets();
            }).bounds(panelX + listWidth + 34, listTop, 26, 20).build();
            next.active = page + 1 < pageCount;
            addRenderableWidget(next);
        } else {
            addSimplePager(panelX, listWidth, pagerY, pageCount);
        }
        DirectMessagePeer editing = editingPeerId == null
                ? null
                : directMessages.find(editingPeerId).filter(availability::isEligiblePeer).orElse(null);
        if (editing == null) {
            return;
        }
        int editorAreaX = stacked ? panelX : panelX + listWidth + 18;
        int editorAreaWidth = stacked ? panelWidth : panelWidth - listWidth - 18;
        int editorWidth = Math.min(MAX_EDITOR_WIDTH, editorAreaWidth);
        int editorX = editorAreaX + (editorAreaWidth - editorWidth) / 2;
        int editorTop = stacked ? (compact ? listTop + 26 : pagerY + 26) : listTop;
        int deleteWidth = Math.min(64, Math.max(48, editorWidth / 3));
        EditBox alias = new EditBox(font, editorX, editorTop, editorWidth - deleteWidth - 4, 20,
                Component.translatable("chataber.config.dm.alias"));
        alias.setMaxLength(ChatTabManager.MAX_TAB_NAME_LENGTH);
        alias.setValue(editing.displayName());
        alias.setResponder(value -> directMessages.find(editing.id()).ifPresent(current ->
                directMessages.updatePreference(current.id(), value, current.pinned(), current.listed())));
        addRenderableWidget(alias);
        addRenderableWidget(Button.builder(Component.translatable("chataber.config.dm.delete"), button -> {
                    draft.deletePeer(editing.id());
                    dmDisplayOrderInputs.remove(editing.id());
                    editingPeerId = directMessages.allPeers().stream()
                            .filter(availability::isEligiblePeer)
                            .findFirst()
                            .map(DirectMessagePeer::id)
                            .orElse(null);
                    rebuildWidgets();
                }).bounds(editorX + editorWidth - deleteWidth, editorTop, deleteWidth, 20).build());
        int halfEditor = (editorWidth - 4) / 2;
        addRenderableWidget(Checkbox.builder(Component.translatable("chataber.config.dm.pinned"), font)
                .pos(editorX, editorTop + 26)
                .maxWidth(halfEditor)
                .selected(editing.pinned())
                .onValueChange((box, selected) -> {
                    directMessages.find(editing.id()).ifPresent(current ->
                            directMessages.updatePreference(
                                    current.id(),
                                    current.displayName(),
                                    selected,
                                    current.listed(),
                                    current.positionFixed(),
                                    current.displayOrder()
                            ));
                    rebuildWidgets();
                })
                .build());
        addRenderableWidget(Checkbox.builder(Component.translatable("chataber.config.dm.listed"), font)
                .pos(editorX + halfEditor + 4, editorTop + 26)
                .maxWidth(halfEditor)
                .selected(editing.listed())
                .onValueChange((box, selected) -> directMessages.find(editing.id()).ifPresent(current ->
                        directMessages.updatePreference(
                                current.id(), current.displayName(), current.pinned(), selected)))
                .build());

        int positionY = editorTop + 52;
        Component fixedPositionLabel = editing.pinned()
                ? Component.translatable("chataber.config.dm.position_fixed")
                : Component.translatable("chataber.config.dm.position_fixed").withStyle(ChatFormatting.DARK_GRAY);
        Checkbox fixedPosition = Checkbox.builder(fixedPositionLabel, font)
                .pos(editorX, positionY)
                .maxWidth(halfEditor)
                .selected(editing.pinned() && editing.positionFixed())
                .onValueChange((box, selected) -> directMessages.find(editing.id()).ifPresent(current -> {
                    directMessages.updatePreference(
                            current.id(),
                            current.displayName(),
                            current.pinned(),
                            current.listed(),
                            current.pinned() && selected,
                            current.displayOrder()
                    );
                    dmDisplayOrderInputs.putIfAbsent(
                            current.id(), Integer.toString(current.displayOrder()));
                    rebuildWidgets();
                }))
                .build();
        fixedPosition.active = editing.pinned();
        if (!editing.pinned()) {
            fixedPosition.setAlpha(0.45F);
            fixedPosition.setTooltip(Tooltip.create(
                    Component.translatable("chataber.config.dm.position_fixed.pin_required")));
        }
        addRenderableWidget(fixedPosition);

        int orderAreaX = editorX + halfEditor + 4;
        Component orderLabel = Component.translatable("chataber.config.dm.display_order");
        int orderLabelWidth = Math.min(font.width(orderLabel) + 5, Math.max(30, halfEditor - 42));
        EditBox displayOrder = new EditBox(
                font,
                orderAreaX + orderLabelWidth,
                positionY,
                Math.max(38, halfEditor - orderLabelWidth),
                20,
                orderLabel
        );
        String orderValue = dmDisplayOrderInputs.computeIfAbsent(
                editing.id(), ignored -> Integer.toString(editing.displayOrder()));
        displayOrder.setMaxLength(11);
        displayOrder.setValue(orderValue);
        boolean displayOrderEnabled = editing.pinned() && editing.positionFixed();
        displayOrder.setEditable(displayOrderEnabled);
        displayOrder.active = displayOrderEnabled;
        displayOrder.setTextColorUneditable(0xFF666666);
        displayOrder.setTextColor(!displayOrderEnabled || parseDisplayOrder(orderValue).isPresent()
                ? 0xFFFFFFFF
                : 0xFFFF6666);
        displayOrder.setAlpha(displayOrderEnabled ? 1.0F : 0.45F);
        displayOrder.setResponder(value -> {
            updateDisplayOrder(editing.id(), value);
            displayOrder.setTextColor(parseDisplayOrder(value).isPresent()
                    ? 0xFFFFFFFF
                    : 0xFFFF6666);
        });
        addRenderableWidget(displayOrder);
        Checkbox persistentNotification = Checkbox.builder(
                        Component.translatable("chataber.config.notification.persistent"), font)
                .pos(editorX, positionY + 24)
                .maxWidth(editorWidth)
                .selected(draft.persistentHudNotifications("dm:" + editing.id()))
                .onValueChange((box, selected) ->
                        draft.setPersistentHudNotifications("dm:" + editing.id(), selected))
                .build();
        persistentNotification.setTooltip(Tooltip.create(
                Component.translatable("chataber.config.notification.persistent.tooltip")));
        addRenderableWidget(persistentNotification);
        dmOrderLabelVisible = true;
        dmOrderLabelEnabled = displayOrderEnabled;
        dmOrderLabelX = orderAreaX;
        dmOrderLabelY = positionY + 6;
    }

    private void addSimplePager(int x, int pagerWidth, int y, int pageCount) {
        Button previous = Button.builder(Component.literal("<"), button -> {
                    page--;
                    rebuildWidgets();
                }).bounds(x, y, 26, 20).build();
        previous.active = page > 0;
        addRenderableWidget(previous);
        Button indicator = Button.builder(Component.literal((page + 1) + " / " + pageCount), button -> { })
                .bounds(x + 30, y, Math.max(30, pagerWidth - 60), 20).build();
        indicator.active = false;
        addRenderableWidget(indicator);
        Button next = Button.builder(Component.literal(">"), button -> {
                    page++;
                    rebuildWidgets();
                }).bounds(x + pagerWidth - 26, y, 26, 20).build();
        next.active = page + 1 < pageCount;
        addRenderableWidget(next);
    }

    private void addCategoryCheckbox(ChatTab tab, MessageCategory category, String key, int x, int y, int maxWidth) {
        boolean allTab = ChatTabManager.ALL_TAB_ID.equals(tab.id());
        Component label = allTab
                ? Component.translatable(key).withStyle(ChatFormatting.DARK_GRAY)
                : Component.translatable(key);
        Checkbox checkbox = Checkbox.builder(label, font)
                .pos(x, y)
                .maxWidth(maxWidth)
                .selected(tab.enabledCategories().contains(category))
                .onValueChange((box, selected) -> {
                    if (draft.tabs().setCategory(tab.id(), category, selected)
                            && category == MessageCategory.PLAYER_CHAT) {
                        rebuildWidgets();
                    }
                })
                .build();
        checkbox.active = !allTab;
        if (!checkbox.active) {
            checkbox.setAlpha(0.45F);
            checkbox.setTooltip(Tooltip.create(Component.translatable("chataber.config.disabled.all")));
        }
        addRenderableWidget(checkbox);
    }

    private void addDirectMessageCheckbox(ChatTab tab, int x, int y, int maxWidth) {
        boolean allTab = ChatTabManager.ALL_TAB_ID.equals(tab.id());
        boolean playerChatEnabled = tab.matches(MessageCategory.PLAYER_CHAT);
        boolean enabled = !allTab && playerChatEnabled;
        Component label = enabled
                ? Component.translatable("chataber.config.include_dm")
                : Component.translatable("chataber.config.include_dm").withStyle(ChatFormatting.DARK_GRAY);
        Checkbox checkbox = Checkbox.builder(label, font)
                .pos(x, y)
                .maxWidth(maxWidth)
                .selected(tab.includeDirectMessages())
                .onValueChange((box, selected) ->
                        draft.tabs().setIncludeDirectMessages(tab.id(), selected))
                .build();
        checkbox.active = enabled;
        if (!enabled) {
            checkbox.setAlpha(0.45F);
        }
        if (allTab) {
            checkbox.setTooltip(Tooltip.create(Component.translatable("chataber.config.disabled.all")));
        } else if (!playerChatEnabled) {
            checkbox.setTooltip(Tooltip.create(
                    Component.translatable("chataber.config.disabled.player_chat_required")));
        }
        addRenderableWidget(checkbox);
    }

    private String resolvePlayerName(String query) {
        String normalized = query == null ? "" : query.strip();
        return availability.onlinePlayerName(normalized)
                .filter(name -> draft.directMessages().find(name).isEmpty())
                .orElse(null);
    }

    private List<String> availableOnlinePlayerNames() {
        return availability.eligibleOnlinePlayers().stream()
                .map(player -> player.getProfile().name())
                .filter(name -> draft.directMessages().find(name).isEmpty())
                .distinct()
                .sorted((left, right) -> left.toLowerCase(Locale.ROOT)
                        .compareTo(right.toLowerCase(Locale.ROOT)))
                .toList();
    }

    private void addPlayer(PlayerNameEditBox playerSearch) {
        String resolved = resolvePlayerName(playerSearch.getValue());
        if (resolved != null) {
            DirectMessagePeer peer = draft.ensurePeer(resolved);
            editingPeerId = peer.id();
            page = 0;
            rebuildWidgets();
        }
    }

    private void updateDisplayOrder(String peerId, String value) {
        // 入力途中の空欄なども保持し、再描画で文字列を戻さない
        dmDisplayOrderInputs.put(peerId, value);
        parseDisplayOrder(value).ifPresent(order -> draft.directMessages().find(peerId)
                .filter(DirectMessagePeer::positionFixed)
                .ifPresent(current -> draft.directMessages().updatePreference(
                        current.id(),
                        current.displayName(),
                        current.pinned(),
                        current.listed(),
                        true,
                        order
                )));
        updateDoneState();
    }

    private java.util.OptionalInt parseDisplayOrder(String value) {
        if (value == null || value.isBlank()) {
            return java.util.OptionalInt.empty();
        }
        try {
            return java.util.OptionalInt.of(Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return java.util.OptionalInt.empty();
        }
    }

    private boolean hasInvalidDisplayOrder() {
        return draft.directMessages().allPeers().stream()
                .filter(DirectMessagePeer::positionFixed)
                .anyMatch(peer -> parseDisplayOrder(dmDisplayOrderInputs.getOrDefault(
                        peer.id(), Integer.toString(peer.displayOrder()))).isEmpty());
    }

    private void updateDoneState() {
        if (doneButton != null) {
            doneButton.active = !hasInvalidDisplayOrder();
        }
    }

    private int panelWidth() {
        return Math.max(180, Math.min(MAX_PANEL_WIDTH, width - 20));
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickProgress) {
        super.extractRenderState(graphics, mouseX, mouseY, tickProgress);
        graphics.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
        if (dmOrderLabelVisible) {
            graphics.text(
                    font,
                    Component.translatable("chataber.config.dm.display_order"),
                    dmOrderLabelX,
                    dmOrderLabelY,
                    dmOrderLabelEnabled ? 0xFFFFFFFF : 0xFF666666
            );
        }
    }

    private void commitAndReturn() {
        if (hasInvalidDisplayOrder()) {
            updateDoneState();
            return;
        }
        draft.directMessages().removePeers(peer -> availability.isSelfName(peer.playerName()));
        // 「完了」で初めてライブ状態へ反映し、続けてJSONへ保存
        draft.applyTo(liveTabs, liveDirectMessages, liveSettings);
        liveDirectMessages.removePeers(peer -> availability.isSelfName(peer.playerName()));
        configManager.save(liveTabs, liveDirectMessages, liveSettings);
        returnToParent();
    }

    private void returnToParent() {
        if (parent instanceof ChatScreen) {
            // 保存しておいたチャット画面を復元し、入力途中の内容を引き継ぐ
            ChatScreen restored = minecraft.gui.hud.getChat().restoreChatScreen();
            minecraft.gui.setScreen(restored == null ? parent : restored);
        } else {
            minecraft.gui.setScreen(parent);
        }
    }

    @Override
    public void onClose() {
        returnToParent();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public enum Section {
        TABS,
        DIRECT_MESSAGES
    }

    private static final class TextToggle extends AbstractWidget {
        private static final int ACCENT = 0xFF8ABDB8;
        private final Font font;
        private final java.util.function.Supplier<String> label;
        private final Runnable onPress;

        private TextToggle(
                Font font,
                int x,
                int y,
                int width,
                java.util.function.Supplier<String> label,
                Runnable onPress
        ) {
            super(x, y, width, 14, Component.empty());
            this.font = font;
            this.label = label;
            this.onPress = onPress;
        }

        @Override
        protected void extractWidgetRenderState(
                @NonNull GuiGraphicsExtractor graphics,
                int mouseX,
                int mouseY,
                float partialTick
        ) {
            graphics.text(font, label.get(), getX(), getY() + 2, isHovered() ? 0xFFFFFFFF : ACCENT);
            handleCursor(graphics);
        }

        @Override
        public void onClick(@NonNull MouseButtonEvent event, boolean doubleClick) {
            onPress.run();
        }

        @Override
        public boolean shouldTakeFocusAfterInteraction() {
            return false;
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
