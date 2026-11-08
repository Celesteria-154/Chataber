package celeste.chataber.message;

import celeste.chataber.config.ChatSettings;
import celeste.chataber.token.ChatMessageLengthValidator;

import java.util.Optional;

/** 設定された雛形からVanilla制限内のDMコマンドを組み立てる。 */
public final class DirectMessageCommandFormatter {
    private DirectMessageCommandFormatter() {
    }

    public static Optional<String> format(String template, String playerName, String body) {
        if (!ChatSettings.isValidDmCommandTemplate(template)
                || !DirectMessageManager.isValidPlayerName(playerName)
                || body == null
                || body.isBlank()) {
            return Optional.empty();
        }
        String command = template.strip().replace("{player}", playerName) + " " + body;
        // 本文だけでなく、コマンド名・宛先を含む送信文字列全体で制限を判定
        return ChatMessageLengthValidator.isWithinVanillaLimit(command)
                ? Optional.of(command)
                : Optional.empty();
    }
}
