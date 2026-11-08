package celeste.chataber.client.config;

import celeste.chataber.client.gui.ChataberConfigScreen;
import celeste.chataber.config.ChatSettings;
import celeste.chataber.config.ChataberSettingsDraft;
import celeste.chataber.config.NotificationBadgeStyle;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;

/** Cloth Config上にChataberの一般設定画面を構築する。 */
public final class ChataberClothConfigScreen {
    private ChataberClothConfigScreen() {
    }

    public static Screen create(Screen parent, ChataberClientContext context) {
        // 一般設定は下書きへ保存し、Done時にまとめてライブ状態へ反映する。
        ChataberSettingsDraft draft = new ChataberSettingsDraft(context.settings());
        ChatSettings.State defaults = ChatSettings.defaults();
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("chataber.config.general.title"))
                .setDoesConfirmSave(true);
        ConfigEntryBuilder entries = builder.entryBuilder();
        ConfigCategory general = builder.getOrCreateCategory(
                Component.translatable("chataber.config.category.general"));

        general.addEntry(entries.startBooleanToggle(
                        Component.translatable("chataber.config.dm.auto_send"),
                        draft.state().dmAutoSend())
                .setDefaultValue(defaults.dmAutoSend())
                .setSaveConsumer(draft::setDmAutoSend)
                .build());
        general.addEntry(entries.startStrField(
                        Component.translatable("chataber.config.dm.command"),
                        draft.state().dmCommandTemplate())
                .setDefaultValue(defaults.dmCommandTemplate())
                .setErrorSupplier(value -> ChatSettings.isValidDmCommandTemplate(value)
                        ? Optional.empty()
                        : Optional.of(Component.translatable("chataber.config.dm.command.invalid")))
                .setSaveConsumer(draft::setDmCommandTemplate)
                .build());
        general.addEntry(entries.startStrField(
                        Component.translatable("chataber.config.here.format"),
                        draft.state().hereFormat())
                .setDefaultValue(defaults.hereFormat())
                .setTooltip(Component.translatable("chataber.config.here.format.tooltip"))
                .setErrorSupplier(value -> ChatSettings.isValidHereFormat(value)
                        ? Optional.empty()
                        : Optional.of(Component.translatable("chataber.config.here.format.invalid")))
                .setSaveConsumer(draft::setHereFormat)
                .build());
        general.addEntry(entries.startIntField(
                        Component.translatable("chataber.config.notification.duration"),
                        draft.state().hudNotificationSeconds())
                .setDefaultValue(defaults.hudNotificationSeconds())
                .setMin(0)
                .setMax(ChatSettings.MAX_HUD_NOTIFICATION_SECONDS)
                .setTooltip(Component.translatable("chataber.config.notification.duration.tooltip"))
                .setSaveConsumer(draft::setHudNotificationSeconds)
                .build());
        general.addEntry(entries.startEnumSelector(
                        Component.translatable("chataber.config.notification.badge"),
                        NotificationBadgeStyle.class,
                        draft.state().badgeStyle())
                .setDefaultValue(defaults.badgeStyle())
                .setEnumNameProvider(style -> Component.translatable(style == NotificationBadgeStyle.DOT
                        ? "chataber.config.badge.dot"
                        : "chataber.config.badge.count"))
                .setSaveConsumer(draft::setBadgeStyle)
                .build());
        general.addEntry(entries.startIntField(
                        Component.translatable("chataber.config.dm.max_tabs"),
                        draft.state().maxVisibleDmTabs())
                .setDefaultValue(defaults.maxVisibleDmTabs())
                .setMin(0)
                .setMax(ChatSettings.MAX_VISIBLE_DM_TABS)
                .setTooltip(Component.translatable("chataber.config.dm.max_tabs.tooltip"))
                .setSaveConsumer(draft::setMaxVisibleDmTabs)
                .build());
        general.addEntry(new OpenManagementEntry(context));

        builder.setSavingRunnable(() -> {
            draft.applyTo(context.settings());
            context.configManager().save(context.tabs(), context.directMessages(), context.settings());
        });
        return builder.build();
    }

    /** Cloth Configから、タブとDM相手を扱う専用画面へ遷移させる項目。 */
    private static final class OpenManagementEntry extends AbstractConfigListEntry<Void> {
        private final ChataberClientContext context;
        private final Button button;
        private final List<AbstractWidget> widgets;

        private OpenManagementEntry(ChataberClientContext context) {
            super(Component.translatable("chataber.config.tabs"), false);
            this.context = context;
            button = Button.builder(
                            Component.translatable("chataber.config.open_tabs"),
                            ignored -> openManagement())
                    .bounds(0, 0, 150, 20)
                    .build();
            widgets = List.of(button);
        }

        private void openManagement() {
            Screen clothScreen = getConfigScreen();
            Minecraft.getInstance().gui.setScreen(new ChataberConfigScreen(
                    clothScreen,
                    context.tabs(),
                    context.directMessages(),
                    context.settings(),
                    context.configManager(),
                    context.directMessageAvailability(),
                    ChataberConfigScreen.Section.TABS
            ));
        }

        @Override
        public Void getValue() {
            return null;
        }

        @Override
        public Optional<Void> getDefaultValue() {
            return Optional.empty();
        }

        @Override
        public boolean isEdited() {
            return false;
        }

        @Override
        public void extractRenderState(
                GuiGraphicsExtractor graphics,
                int index,
                int y,
                int x,
                int entryWidth,
                int entryHeight,
                int mouseX,
                int mouseY,
                boolean hovered,
                float tickProgress
        ) {
            super.extractRenderState(
                    graphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY, hovered, tickProgress);
            graphics.text(
                    Minecraft.getInstance().font,
                    getDisplayedFieldName(),
                    x,
                    y + 6,
                    getPreferredTextColor()
            );
            int buttonWidth = Math.min(150, Math.max(80, entryWidth / 2));
            button.setWidth(buttonWidth);
            button.setX(x + entryWidth - buttonWidth);
            button.setY(y);
            button.extractRenderState(graphics, mouseX, mouseY, tickProgress);
        }

        @Override
        public @NonNull List<? extends GuiEventListener> children() {
            return widgets;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return widgets;
        }
    }
}
