package celeste.chataber.mixin.client;

import celeste.chataber.client.message.ClientMessageRouting;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** DMビューだけで会話向けの表示文を行分割へ渡す。 */
@Mixin(GuiMessage.class)
public abstract class GuiMessageMixin {
    @ModifyExpressionValue(
            method = "splitLines",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/multiplayer/chat/GuiMessage;content:Lnet/minecraft/network/chat/Component;",
                    opcode = Opcodes.GETFIELD
            )
    )
    private Component chataber$useDirectMessagePresentation(Component original) {
        return ClientMessageRouting.displayContent((GuiMessage) (Object) this, original);
    }
}
