package celeste.chataber.token;

import celeste.chataber.config.ChatSettings;
import net.minecraft.core.BlockPos;

import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** {@code @here}を設定された形式の現在座標へ展開する。 */
public final class HereToken implements ChatToken {
    // 英数字や_に接する@hereは、名前などの一部として置換対象から除外
    private static final Pattern TOKEN = Pattern.compile("(?<![A-Za-z0-9_])@here(?![A-Za-z0-9_])");
    private final Supplier<String> format;

    public HereToken(Supplier<String> format) {
        this.format = Objects.requireNonNull(format, "format");
    }

    @Override
    public String replace(String message, ChatTokenContext context) {
        return context.position()
                .map(position -> replaceAtPosition(message, position, format.get()))
                .orElse(message);
    }

    private static String replaceAtPosition(String message, BlockPos position, String format) {
        // 不正な書式は既定形式に置換
        String safeFormat = ChatSettings.isValidHereFormat(format)
                ? format.strip()
                : ChatSettings.DEFAULT_HERE_FORMAT;
        String replacement = safeFormat
                .replace("{x}", Integer.toString(position.getX()))
                .replace("{y}", Integer.toString(position.getY()))
                .replace("{z}", Integer.toString(position.getZ()));
        // 書式に含まれる$やバックスラッシュを正規表現の置換命令として扱わない
        return TOKEN.matcher(message).replaceAll(Matcher.quoteReplacement(replacement));
    }
}
