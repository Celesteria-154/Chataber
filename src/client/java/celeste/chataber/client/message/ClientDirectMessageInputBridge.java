package celeste.chataber.client.message;

/** ChatScreen MixinからDM入力変換へ渡す静的ブリッジ。 */
public final class ClientDirectMessageInputBridge {
    private static ClientDirectMessageInput handler;

    private ClientDirectMessageInputBridge() {
    }

    public static void initialize(ClientDirectMessageInput inputHandler) {
        handler = inputHandler;
    }

    public static String transform(String input) {
        return handler == null ? input : handler.transform(input);
    }
}
