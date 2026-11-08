package celeste.chataber.mixin.client;

import celeste.chataber.client.message.ClientPlayerListChangeBridge;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** プレイヤー一覧更新後にDM候補UIへ再計算を通知する。 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handlePlayerInfoUpdate", at = @At("TAIL"))
    private void chataber$playerInfoUpdated(ClientboundPlayerInfoUpdatePacket packet, CallbackInfo ci) {
        ClientPlayerListChangeBridge.notifyChanged();
    }

    @Inject(method = "handlePlayerInfoRemove", at = @At("TAIL"))
    private void chataber$playerInfoRemoved(ClientboundPlayerInfoRemovePacket packet, CallbackInfo ci) {
        ClientPlayerListChangeBridge.notifyChanged();
    }
}
