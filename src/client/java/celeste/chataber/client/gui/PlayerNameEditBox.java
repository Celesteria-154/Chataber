package celeste.chataber.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

/** プレイヤー名の候補循環とEnter確定を扱う入力欄。 */
final class PlayerNameEditBox extends EditBox {
    private final Supplier<List<String>> candidates;
    private Runnable submitHandler = () -> { };

    // 補完中は最初に入力された文字列を基準にし、置換後の候補間だけを循環する。
    private String completionPrefix;
    private String lastCompletion;
    private int completionIndex;

    PlayerNameEditBox(
            Font font,
            int x,
            int y,
            int width,
            int height,
            Component message,
            Supplier<List<String>> candidates
    ) {
        super(font, x, y, width, height, message);
        this.candidates = Objects.requireNonNull(candidates, "candidates");
    }

    void setSubmitHandler(Runnable submitHandler) {
        this.submitHandler = Objects.requireNonNull(submitHandler, "submitHandler");
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        // Tab補完とEnter確定は入力欄側で処理
        if (event.key() == InputConstants.KEY_TAB) {
            complete((event.modifiers() & InputConstants.MOD_SHIFT) != 0);
            return true;
        }
        if (event.key() == InputConstants.KEY_RETURN
                || event.key() == InputConstants.KEY_NUMPADENTER) {
            submitHandler.run();
            return true;
        }
        boolean handled = super.keyPressed(event);
        if (handled) {
            resetCompletion();
        }
        return handled;
    }

    @Override
    public boolean charTyped(@NonNull CharacterEvent event) {
        boolean handled = super.charTyped(event);
        if (handled) {
            resetCompletion();
        }
        return handled;
    }

    private void complete(boolean backwards) {
        String current = getValue().strip();
        // 編集後の最初のTabで補完を開始
        if (!current.equals(lastCompletion)) {
            completionPrefix = current;
            completionIndex = backwards ? -1 : 0;
        }
        String prefix = completionPrefix == null ? current : completionPrefix;
        String normalizedPrefix = prefix.toLowerCase(Locale.ROOT);

        // 接続順に左右されないよう、重複を除いて大文字小文字を無視した順序へ揃える。
        List<String> matches = candidates.get().stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(candidate -> !candidate.isEmpty())
                .filter(candidate -> candidate.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix))
                .distinct()
                .sorted(Comparator.comparing(value -> value.toLowerCase(Locale.ROOT)))
                .toList();
        if (matches.isEmpty()) {
            return;
        }
        if (current.equals(lastCompletion)) {
            completionIndex += backwards ? -1 : 1;
        }
        completionIndex = Math.floorMod(completionIndex, matches.size());
        String completed = matches.get(completionIndex);
        setValue(completed);
        moveCursorToEnd(false);
        lastCompletion = completed;
    }

    private void resetCompletion() {
        // 次のTabで現在の入力値から補完し直す
        completionPrefix = null;
        lastCompletion = null;
        completionIndex = 0;
    }
}
