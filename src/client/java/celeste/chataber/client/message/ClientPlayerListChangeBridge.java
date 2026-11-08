package celeste.chataber.client.message;

/** プレイヤー一覧のパケット更新を開いているUIへ通知する。 */
public final class ClientPlayerListChangeBridge {
    private static Runnable listener;

    private ClientPlayerListChangeBridge() {
    }

    public static void setListener(Runnable newListener) {
        listener = newListener;
    }

    public static void clearListener(Runnable expectedListener) {
        if (listener == expectedListener) {
            listener = null;
        }
    }

    public static void notifyChanged() {
        if (listener != null) {
            listener.run();
        }
    }
}
