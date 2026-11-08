package celeste.chataber.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/** プレイヤー名入力に前方一致候補を重ねて表示する。 */
final class PlayerNameSuggestionList extends AbstractWidget {
    private static final int ROW_HEIGHT = 13;

    private final Font font;
    private final PlayerNameEditBox input;
    private final Supplier<List<String>> candidates;

    PlayerNameSuggestionList(
            Font font,
            int x,
            int y,
            int width,
            int height,
            PlayerNameEditBox input,
            Supplier<List<String>> candidates
    ) {
        super(x, y, width, height, Component.translatable("chataber.dm.completions"));
        this.font = font;
        this.input = input;
        this.candidates = candidates;
    }

    @Override
    protected void extractWidgetRenderState(
            @NonNull GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float tickProgress
    ) {
        List<String> matches = matches();
        if (!shouldShow(matches)) {
            return;
        }
        int rows = Math.min(matches.size(), getHeight() / ROW_HEIGHT);
        graphics.fill(getX(), getY(), getRight(), getY() + rows * ROW_HEIGHT, 0xEE101010);
        for (int index = 0; index < rows; index++) {
            int rowY = getY() + index * ROW_HEIGHT;
            if (mouseX >= getX() && mouseX < getRight()
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                graphics.fill(getX(), rowY, getRight(), rowY + ROW_HEIGHT, 0xAA406060);
            }
            graphics.text(font, matches.get(index), getX() + 3, rowY + 2, 0xFFFFFFFF);
        }
        handleCursor(graphics);
    }

    @Override
    public void onClick(@NonNull MouseButtonEvent event, boolean doubleClick) {
        List<String> matches = matches();
        if (!shouldShow(matches)) {
            return;
        }
        int index = (int) ((event.y() - getY()) / ROW_HEIGHT);
        int rows = Math.min(matches.size(), getHeight() / ROW_HEIGHT);
        if (index >= 0 && index < rows) {
            input.setValue(matches.get(index));
            input.moveCursorToEnd(false);
            input.setFocused(true);
        }
    }

    @Override
    public boolean shouldTakeFocusAfterInteraction() {
        return false;
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    private boolean shouldShow(List<String> matches) {
        return input.isFocused() && !input.getValue().isBlank() && !matches.isEmpty();
    }

    private List<String> matches() {
        String prefix = input.getValue().strip().toLowerCase(Locale.ROOT);
        if (prefix.isEmpty()) {
            return List.of();
        }
        return candidates.get().stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .limit(Math.max(1, getHeight() / ROW_HEIGHT))
                .toList();
    }
}
