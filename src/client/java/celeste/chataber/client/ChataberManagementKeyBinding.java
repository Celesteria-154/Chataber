package celeste.chataber.client;

import celeste.chataber.client.gui.ChataberConfigScreen;
import celeste.chataber.client.message.DirectMessageAvailability;
import celeste.chataber.config.ChatSettings;
import celeste.chataber.config.ConfigManager;
import celeste.chataber.message.DirectMessageManager;
import celeste.chataber.tab.ChatTabManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

/** タブ・DM管理画面のキー設定。 */
final class ChataberManagementKeyBinding {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("chataber", "general"));

    private final Minecraft client;
    private final ChatTabManager tabs;
    private final DirectMessageManager directMessages;
    private final ChatSettings settings;
    private final ConfigManager configManager;
    private final DirectMessageAvailability availability;

    ChataberManagementKeyBinding(
            Minecraft client,
            ChatTabManager tabs,
            DirectMessageManager directMessages,
            ChatSettings settings,
            ConfigManager configManager,
            DirectMessageAvailability availability
    ) {
        this.client = client;
        this.tabs = tabs;
        this.directMessages = directMessages;
        this.settings = settings;
        this.configManager = configManager;
        this.availability = availability;
    }

    void register() {
        KeyMapping openManagement = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.chataber.open_management",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                CATEGORY
        ));
        ClientTickEvents.END_CLIENT_TICK.register(ignored -> {
            while (openManagement.consumeClick()) {
                openManagementScreen();
            }
        });
    }

    private void openManagementScreen() {
        Screen parent = client.gui.screen();
        if (parent != null && !(parent instanceof ChatScreen)) {
            return;
        }
        if (parent instanceof ChatScreen) {
            client.gui.hud.getChat().preserveCurrentChatScreen();
        }
        client.gui.setScreen(new ChataberConfigScreen(
                parent,
                tabs,
                directMessages,
                settings,
                configManager,
                availability,
                ChataberConfigScreen.Section.TABS
        ));
    }
}
