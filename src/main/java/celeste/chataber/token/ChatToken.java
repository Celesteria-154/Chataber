package celeste.chataber.token;

/** 送信前のチャット本文を文脈に応じて変換するトークン。 */
public interface ChatToken {
    String replace(String message, ChatTokenContext context);
}
