package celeste.chataber.mixin.client;

import celeste.chataber.client.message.ClientMessageRouting;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Predicate;

/** 表示フィルターと履歴保存を分離し、GuiMessageへ経路情報を結び付ける。 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Shadow
    @Final
    private List<GuiMessage> allMessages;

    @Shadow
    private void addMessageToQueue(GuiMessage message) {
        throw new AssertionError();
    }

    /**
     * Minecraft 26.2では、表示フィルターから外れたメッセージは履歴にも追加されない。
     * 非表示メッセージだけ履歴へ追加し、タブを切り替えた際に表示できるようにする。
     */
    @WrapOperation(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/function/Predicate;test(Ljava/lang/Object;)Z"
            )
    )
    private boolean chataber$archiveFilteredMessage(
            Predicate<Object> filter,
            Object candidate,
            Operation<Boolean> original
    ) {
        ClientMessageRouting.attachPendingRoute((GuiMessage) candidate);
        boolean visible = original.call(filter, candidate);
        if (!visible) {
            addMessageToQueue((GuiMessage) candidate);
        }
        return visible;
    }

    @Inject(method = "addMessageToQueue", at = @At("TAIL"))
    private void chataber$discardExpiredRoutes(GuiMessage message, CallbackInfo ci) {
        ClientMessageRouting.retainAll(allMessages);
    }

    @Inject(method = "clearMessages", at = @At("TAIL"))
    private void chataber$clearMessageRoutes(boolean clearRecentChat, CallbackInfo ci) {
        ClientMessageRouting.clear();
    }
}
