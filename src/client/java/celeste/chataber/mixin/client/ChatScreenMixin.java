package celeste.chataber.mixin.client;

import celeste.chataber.client.message.ClientDirectMessageInputBridge;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DM送信前の入力変換と履歴操作後のフォーカス復帰。 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Shadow
    protected EditBox input;

    @ModifyExpressionValue(
            method = "handleChatInput",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/ChatScreen;normalizeChatMessage(Ljava/lang/String;)Ljava/lang/String;"
            )
    )
    private String chataber$prepareDirectMessage(String normalizedInput) {
        return ClientDirectMessageInputBridge.transform(normalizedInput);
    }

    /** 履歴移動後の入力フォーカス復帰。 */
    @Inject(method = "moveInHistory", at = @At("RETURN"))
    private void chataber$restoreInputFocusAfterHistory(int direction, CallbackInfo callbackInfo) {
        ((ChatScreen) (Object) this).setFocused(input);
    }
}
