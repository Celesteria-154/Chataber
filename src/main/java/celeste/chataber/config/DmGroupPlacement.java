package celeste.chataber.config;

/** 通常タブ列に対するDMグループの配置方法。 */
public record DmGroupPlacement(Mode mode, String anchorTabId) {
    public DmGroupPlacement {
        mode = mode == null ? Mode.LAST : mode;
        if (mode != Mode.AFTER_TAB) {
            anchorTabId = null;
        }
    }

    public static DmGroupPlacement first() {
        return new DmGroupPlacement(Mode.FIRST, null);
    }

    public static DmGroupPlacement last() {
        return new DmGroupPlacement(Mode.LAST, null);
    }

    public static DmGroupPlacement after(String tabId) {
        return new DmGroupPlacement(Mode.AFTER_TAB, tabId);
    }

    public enum Mode {
        FIRST,
        LAST,
        AFTER_TAB
    }
}
