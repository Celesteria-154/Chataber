package celeste.chataber.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod MenuからChataber設定画面を開くための連携口。 */
public final class ChataberModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> ChataberClothConfigScreen.create(parent, ChataberClientContext.current());
    }
}
