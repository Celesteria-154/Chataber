package celeste.chataber.mixin.client;

import celeste.chataber.client.message.ClientDirectMessageInputBridge;
import celeste.chataber.token.HereToken;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.BitSet;

/** チャット入力欄の装飾、DM送信前の入力変換、履歴操作後のフォーカス復帰。 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Shadow
    protected EditBox input;

    @ModifyReturnValue(method = "formatChat", at = @At("RETURN"))
    private FormattedCharSequence chataber$highlightHere(FormattedCharSequence original, String visibleText, int offset) {
        String value = input.getValue();
        if (!value.contains("@here") || value.stripLeading().startsWith("/")) {
            return original;
        }
        // 描画範囲は横スクロールやカーソルで分割される。判定には入力欄の全文を使う。
        var match = HereToken.matcher(value);
        int visibleEnd = offset + visibleText.length();
        BitSet highlight = new BitSet(visibleText.length());
        while (match.find() && match.start() < visibleEnd) {
            if (match.end() > offset) {
                highlight.set(Math.max(match.start(), offset) - offset, Math.min(match.end(), visibleEnd) - offset);
            }
        }
        if (highlight.isEmpty()) {
            return original;
        }

        FormattedCharSequence text = original == null
                ? FormattedCharSequence.forward(visibleText, Style.EMPTY)
                : original;
        return sink -> text.accept((index, style, codePoint) ->
                sink.accept(index, highlight.get(index) ? style.withColor(ChatFormatting.AQUA) : style, codePoint));
    }

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
