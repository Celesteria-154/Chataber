package celeste.chataber.token;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** 登録されたトークン変換を順番に適用する。 */
public final class ChatTokenProcessor {
    private final List<ChatToken> tokens;

    public ChatTokenProcessor(List<ChatToken> tokens) {
        this.tokens = List.copyOf(tokens);
    }

    public static ChatTokenProcessor createDefault(Supplier<String> hereFormat) {
        return new ChatTokenProcessor(List.of(new HereToken(hereFormat)));
    }

    public String process(String message, ChatTokenContext context) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(context, "context");
        String result = message;
        // 登録順にトークンを展開
        for (ChatToken token : tokens) {
            result = token.replace(result, context);
        }
        return result;
    }
}
