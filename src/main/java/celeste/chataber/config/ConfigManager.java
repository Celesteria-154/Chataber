package celeste.chataber.config;

import celeste.chataber.message.MessageCategory;
import celeste.chataber.message.DirectMessageManager;
import celeste.chataber.message.DirectMessagePeerPreference;
import celeste.chataber.tab.ChatTab;
import celeste.chataber.tab.ChatTabManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 設定JSONの読み書きと旧形式の移行。 */
public final class ConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Chataber/Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path configPath;

    public ConfigManager() {
        this(FabricLoader.getInstance().getConfigDir().resolve("chataber.json"));
    }

    public ConfigManager(Path configPath) {
        this.configPath = configPath;
    }

    public void loadInto(
            ChatTabManager tabManager,
            DirectMessageManager directMessages,
            ChatSettings settings
    ) {
        if (!Files.exists(configPath)) {
            tabManager.resetToDefaults();
            if (settings != null) {
                settings.apply(ChatSettings.defaults());
            }
            return;
        }

        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            ChataberConfig config = GSON.fromJson(reader, ChataberConfig.class);
            applyValidated(config, tabManager);
            applyExtendedConfig(config, tabManager, directMessages, settings);
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not load {}; using default tabs", configPath, exception);
            tabManager.resetToDefaults();
            if (settings != null) {
                settings.apply(ChatSettings.defaults());
            }
        }
    }

    public void save(
            ChatTabManager tabManager,
            DirectMessageManager directMessages,
            ChatSettings settings
    ) {
        ChatSettings.State state = settings == null ? ChatSettings.defaults() : settings.state();
        ChataberConfig config = new ChataberConfig(
                tabManager.selectedTabId(),
                tabManager.tabs().stream().map(ConfigManager::toConfig).toList(),
                new ChataberConfig.DirectMessageConfig(
                        state.dmGroupPlacement().mode().name(),
                        state.dmGroupPlacement().anchorTabId(),
                        state.dmAutoSend(),
                        state.dmCommandTemplate(),
                        directMessages == null
                                ? List.of()
                                : directMessages.preferences().stream().map(ConfigManager::toPeerConfig).toList(),
                        state.maxVisibleDmTabs()
                ),
                new ChataberConfig.NotificationConfig(
                        state.hudNotificationSeconds(),
                        state.badgeStyle().name(),
                        null,
                        state.persistentHudTargets().stream().sorted().toList()
                ),
                new ChataberConfig.TokenConfig(state.hereFormat())
        );

        Path parent = configPath.getParent();
        Path temporary = configPath.resolveSibling(configPath.getFileName() + ".tmp");
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
            // 書き込み途中の破損を避けるため、一時ファイルを完成させてから置き換える。
            try {
                Files.move(temporary, configPath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, configPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            LOGGER.error("Could not save {}", configPath, exception);
        }
    }

    private static void applyValidated(ChataberConfig config, ChatTabManager tabManager) {
        if (config == null || config.tabs() == null) {
            tabManager.resetToDefaults();
            return;
        }

        List<ChatTab> tabs = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (ChataberConfig.TabConfig tabConfig : config.tabs()) {
            ChatTab tab = validateTab(tabConfig, seenIds);
            if (tab != null) {
                tabs.add(tab);
            }
        }

        // 設定ファイルからAllが消えていても、表示先と削除時の戻り先を確保
        if (!seenIds.contains(ChatTabManager.ALL_TAB_ID)) {
            tabs.addFirst(ChatTabManager.defaultTabs().getFirst());
        }

        if (tabs.isEmpty()) {
            tabManager.resetToDefaults();
            return;
        }

        tabManager.replaceAll(tabs, config.selectedTab());
    }

    private static ChatTab validateTab(ChataberConfig.TabConfig config, Set<String> seenIds) {
        if (config == null || config.id() == null || config.name() == null) {
            return null;
        }
        String id = config.id().strip();
        String name = config.name().strip();
        if (id.isEmpty() || name.isEmpty() || !seenIds.add(id)) {
            return null;
        }
        if (name.length() > ChatTabManager.MAX_TAB_NAME_LENGTH) {
            name = name.substring(0, ChatTabManager.MAX_TAB_NAME_LENGTH);
        }
        // systemタブの旧既定名だけ置換し、他のIDや名前は変更しない
        if ("system".equals(id) && "System".equals(name)) {
            name = "Sys";
        }

        EnumSet<MessageCategory> categories = EnumSet.noneOf(MessageCategory.class);
        if (config.filters() != null) {
            for (String filter : config.filters()) {
                if (filter == null) {
                    continue;
                }
                try {
                    categories.add(MessageCategory.valueOf(filter));
                } catch (IllegalArgumentException ignored) {
                    // 未知または破損したカテゴリは、読み込める設定だけを残す。
                }
            }
        }
        if (ChatTabManager.ALL_TAB_ID.equals(id)) {
            categories = EnumSet.allOf(MessageCategory.class);
        }
        boolean includeDirectMessages = ChatTabManager.ALL_TAB_ID.equals(id)
                || Boolean.TRUE.equals(config.includeDirectMessages());
        return new ChatTab(id, name, categories, includeDirectMessages);
    }

    private static ChataberConfig.TabConfig toConfig(ChatTab tab) {
        List<String> filters = tab.enabledCategories().stream()
                .map(Enum::name)
                .sorted()
                .toList();
        return new ChataberConfig.TabConfig(tab.id(), tab.name(), filters, tab.includeDirectMessages());
    }

    private static void applyExtendedConfig(
            ChataberConfig config,
            ChatTabManager tabs,
            DirectMessageManager directMessages,
            ChatSettings settings
    ) {
        if (config == null) {
            return;
        }
        ChataberConfig.DirectMessageConfig dmConfig = config.directMessages();
        if (directMessages != null) {
            List<DirectMessagePeerPreference> preferences = new ArrayList<>();
            if (dmConfig != null && dmConfig.peers() != null) {
                for (ChataberConfig.PeerConfig peer : dmConfig.peers()) {
                    if (peer == null || !DirectMessageManager.isValidPlayerName(peer.playerName())) {
                        continue;
                    }
                    preferences.add(new DirectMessagePeerPreference(
                            peer.playerName(),
                            peer.displayName(),
                            Boolean.TRUE.equals(peer.pinned()),
                            peer.listed() == null || peer.listed(),
                            Boolean.TRUE.equals(peer.positionFixed()),
                            peer.displayOrder() == null ? 0 : peer.displayOrder()
                    ));
                }
            }
            directMessages.applyPreferences(preferences);
        }
        if (settings == null) {
            return;
        }

        ChatSettings.State defaults = ChatSettings.defaults();
        DmGroupPlacement placement = readPlacement(dmConfig, tabs);
        boolean autoSend = dmConfig == null || dmConfig.autoSend() == null
                ? defaults.dmAutoSend()
                : dmConfig.autoSend();
        String template = dmConfig == null || dmConfig.commandTemplate() == null
                ? defaults.dmCommandTemplate()
                : dmConfig.commandTemplate();
        int maxVisibleDmTabs = dmConfig == null || dmConfig.maxVisibleTabs() == null
                ? defaults.maxVisibleDmTabs()
                : dmConfig.maxVisibleTabs();

        ChataberConfig.NotificationConfig notificationConfig = config.notifications();
        int seconds = notificationConfig == null || notificationConfig.hudSeconds() == null
                ? defaults.hudNotificationSeconds()
                : notificationConfig.hudSeconds();
        NotificationBadgeStyle badgeStyle = defaults.badgeStyle();
        if (notificationConfig != null && notificationConfig.badgeStyle() != null) {
            try {
                badgeStyle = NotificationBadgeStyle.valueOf(notificationConfig.badgeStyle());
            } catch (IllegalArgumentException ignored) {
                // 未知の表示方式は既定値を使用
            }
        }
        ChataberConfig.TokenConfig tokenConfig = config.tokens();
        String hereFormat = tokenConfig == null || tokenConfig.hereFormat() == null
                ? defaults.hereFormat()
                : tokenConfig.hereFormat();
        settings.apply(new ChatSettings.State(
                placement,
                autoSend,
                template,
                seconds,
                readPersistentHudTargets(notificationConfig, tabs, directMessages),
                badgeStyle,
                maxVisibleDmTabs,
                hereFormat
        ));
    }

    private static Set<String> readPersistentHudTargets(
            ChataberConfig.NotificationConfig config, ChatTabManager tabs, DirectMessageManager directMessages
    ) {
        Set<String> targets = new HashSet<>();
        if (config == null) {
            return targets;
        }
        // 個別設定があれば、旧全体フラグより優先（空リストはすべてOFF）
        if (config.persistentHudTargets() != null) {
            for (String target : config.persistentHudTargets()) {
                if (target == null) {
                    continue;
                }
                if (target.startsWith("tab:") && tabs.findById(target.substring(4)).isPresent()) {
                    targets.add(target);
                } else if (target.startsWith("dm:") && DirectMessageManager.isValidPlayerName(target.substring(3))) {
                    targets.add("dm:" + celeste.chataber.message.MessageRoute.normalizePeerId(target.substring(3)));
                }
            }
        } else if (Boolean.TRUE.equals(config.persistentHud())) {
            // 旧全体設定は既存の対象だけへ移行し、新しいタブや相手へは引き継がない。
            tabs.tabs().stream().filter(tab -> !ChatTabManager.ALL_TAB_ID.equals(tab.id()))
                    .forEach(tab -> targets.add("tab:" + tab.id()));
            if (directMessages != null) {
                directMessages.allPeers().forEach(peer -> targets.add("dm:" + peer.id()));
            }
        }
        return targets;
    }

    private static DmGroupPlacement readPlacement(
            ChataberConfig.DirectMessageConfig config,
            ChatTabManager tabs
    ) {
        if (config == null || config.placementMode() == null) {
            return DmGroupPlacement.last();
        }
        try {
            DmGroupPlacement.Mode mode = DmGroupPlacement.Mode.valueOf(config.placementMode());
            if (mode == DmGroupPlacement.Mode.AFTER_TAB
                    && tabs.findById(config.placementAnchorTab()).isPresent()) {
                return DmGroupPlacement.after(config.placementAnchorTab());
            }
            return mode == DmGroupPlacement.Mode.FIRST
                    ? DmGroupPlacement.first()
                    : DmGroupPlacement.last();
        } catch (IllegalArgumentException ignored) {
            return DmGroupPlacement.last();
        }
    }

    private static ChataberConfig.PeerConfig toPeerConfig(DirectMessagePeerPreference peer) {
        return new ChataberConfig.PeerConfig(
                peer.playerName(),
                peer.displayName(),
                peer.pinned(),
                peer.listed(),
                peer.positionFixed(),
                peer.displayOrder()
        );
    }
}
