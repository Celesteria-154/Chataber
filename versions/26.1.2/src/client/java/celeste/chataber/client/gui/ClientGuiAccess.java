package celeste.chataber.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;

/** Minecraftのバージョンごとに異なる画面とチャット欄の操作。 */
public final class ClientGuiAccess {
    private ClientGuiAccess() {
    }

    public static Screen screen(Minecraft client) {
        return client.screen;
    }

    public static void setScreen(Minecraft client, Screen screen) {
        client.setScreen(screen);
    }

    public static ChatComponent chat(Minecraft client) {
        return client.gui.getChat();
    }
}
