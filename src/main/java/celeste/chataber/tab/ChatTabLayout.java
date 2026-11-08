package celeste.chataber.tab;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 画面幅と優先順位から表示するタブを決定する。 */
public final class ChatTabLayout {
    private ChatTabLayout() {
    }

    public static Result fit(
            int availableWidth,
            List<Item> orderedTabs,
            List<String> priorityIds,
            int overflowWidth,
            int gap
    ) {
        return fit(availableWidth, orderedTabs, priorityIds, overflowWidth, gap, 0);
    }

    public static Result fit(
            int availableWidth,
            List<Item> orderedTabs,
            List<String> priorityIds,
            int overflowWidth,
            int gap,
            int maxVisibleTabs
    ) {
        List<Item> tabs = List.copyOf(orderedTabs);
        boolean limitedByCount = maxVisibleTabs > 0 && tabs.size() > maxVisibleTabs;
        if (!limitedByCount && totalWidth(tabs, gap) <= availableWidth) {
            return new Result(tabs.stream().map(Item::id).toList(), false);
        }

        // はみ出したタブを開くボタンと、その手前の隙間を先に確保
        int tabBudget = Math.max(0, availableWidth - overflowWidth - gap);
        Set<String> selected = new HashSet<>();
        int used = 0;
        // 選択中などの優先タブを確保してから、設定順で残りを詰める。
        for (String priorityId : priorityIds) {
            if (maxVisibleTabs > 0 && selected.size() >= maxVisibleTabs) {
                break;
            }
            Item item = tabs.stream().filter(candidate -> candidate.id().equals(priorityId)).findFirst().orElse(null);
            if (item != null && !selected.contains(item.id()) && fits(used, selected.size(), item.width(), gap, tabBudget)) {
                used += item.width() + (selected.isEmpty() ? 0 : gap);
                selected.add(item.id());
            }
        }
        for (Item item : tabs) {
            if (maxVisibleTabs > 0 && selected.size() >= maxVisibleTabs) {
                break;
            }
            if (!selected.contains(item.id()) && fits(used, selected.size(), item.width(), gap, tabBudget)) {
                used += item.width() + (selected.isEmpty() ? 0 : gap);
                selected.add(item.id());
            }
        }
        // 極端に狭い画面でも操作対象を一つは残す。
        if (selected.isEmpty() && !tabs.isEmpty()) {
            selected.add(tabs.getFirst().id());
        }
        // 優先順位は採用するタブの選定にだけ使用し、表示順は設定順を維持
        List<String> visible = new ArrayList<>();
        for (Item item : tabs) {
            if (selected.contains(item.id())) {
                visible.add(item.id());
            }
        }
        return new Result(visible, visible.size() < tabs.size());
    }

    public static boolean showDirectMessageOverflow(
            boolean directMessagesAvailable,
            int visibleTabCount,
            int overflowTabCount
    ) {
        return directMessagesAvailable && (visibleTabCount == 0 || overflowTabCount > 0);
    }

    private static boolean fits(int used, int count, int width, int gap, int budget) {
        return used + (count == 0 ? 0 : gap) + width <= budget;
    }

    private static int totalWidth(List<Item> items, int gap) {
        if (items.isEmpty()) {
            return 0;
        }
        return items.stream().mapToInt(Item::width).sum() + gap * (items.size() - 1);
    }

    public record Item(String id, int width) {
        public Item {
            Objects.requireNonNull(id, "id");
            if (width < 1) {
                throw new IllegalArgumentException("width must be positive");
            }
        }
    }

    public record Result(List<String> visibleIds, boolean overflow) {
        public Result {
            visibleIds = List.copyOf(visibleIds);
        }
    }
}
