package com.mohistmc.youer.ai;

import com.mohistmc.mjson.Json;
import com.mohistmc.youer.YouerConfig;
import com.mohistmc.youer.ai.history.AiConversationStore;
import com.mohistmc.youer.ai.http.JdkAiHttpClient;
import com.mohistmc.youer.ai.metrics.AiMetrics;
import com.mohistmc.youer.ai.skill.AiCapabilitySnapshotProvider;
import com.mohistmc.youer.ai.skill.AiSkillAccess;
import com.mohistmc.youer.ai.skill.AiSkillCatalog;
import com.mohistmc.youer.ai.skill.AiSkillIndex;
import com.mohistmc.youer.ai.skill.AiSkillLoader;
import com.mohistmc.youer.ai.skill.AiSkillParser;
import com.mohistmc.youer.ai.skill.AiSkillRegistry;
import com.mohistmc.youer.ai.skill.BukkitAiSkillAccess;
import com.mohistmc.youer.ai.skill.LoadSkillTool;
import com.mohistmc.youer.ai.tool.AiAgentLoop;
import com.mohistmc.youer.ai.tool.AiExecutionDispatcher;
import com.mohistmc.youer.ai.tool.AiRegisteredTool;
import com.mohistmc.youer.ai.tool.AiToolExecutor;
import com.mohistmc.youer.ai.tool.AiToolAudit;
import com.mohistmc.youer.ai.tool.AiToolOwner;
import com.mohistmc.youer.ai.tool.AiToolPermissions;
import com.mohistmc.youer.ai.tool.AiToolRegistry;
import com.mohistmc.youer.ai.tool.AiToolSchemaValidator;
import com.mohistmc.youer.ai.tool.AiToolSource;
import com.mohistmc.youer.ai.tool.command.AiCommandSanitizer;
import com.mohistmc.youer.ai.tool.command.BukkitAiCommandGateway;
import com.mohistmc.youer.ai.tool.command.ConsoleCommandTool;
import com.mohistmc.youer.ai.tool.command.PlayerCommandTool;
import com.mohistmc.youer.ai.tool.command.SearchCommandsTool;
import com.mohistmc.youer.ai.tool.confirmation.AiConfirmationApproval;
import com.mohistmc.youer.ai.tool.confirmation.AiConfirmationNotifier;
import com.mohistmc.youer.ai.tool.confirmation.AiConfirmationStore;
import com.mohistmc.youer.ai.tool.http.AiExternalHttpTransport;
import com.mohistmc.youer.ai.tool.http.AiHttpToolDefinition;
import com.mohistmc.youer.ai.tool.http.AiHttpToolHandler;
import com.mohistmc.youer.ai.tool.http.AiHttpToolArgumentPreparer;
import com.mohistmc.youer.ai.tool.http.AiHttpToolParser;
import com.mohistmc.youer.ai.tool.http.AiHttpToolParseResult;
import com.mohistmc.youer.ai.tool.http.JdkAiExternalHttpTransport;
import com.mohistmc.youer.api.ai.tool.AiToolContext;
import com.mohistmc.youer.api.ai.tool.AiToolDefinition;
import com.mohistmc.youer.api.ai.tool.AiToolExecutionMode;
import com.mohistmc.youer.api.ai.tool.AiToolRisk;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class AiLifecycle {

    private static final Logger LOGGER = LogManager.getLogger(AiLifecycle.class);
    private static final AiToolSchemaValidator TOOL_SCHEMA = new AiToolSchemaValidator();
    private static final AiToolRegistry TOOL_REGISTRY = new AiToolRegistry(TOOL_SCHEMA);
    private static final AiConfirmationStore CONFIRMATIONS = new AiConfirmationStore(Clock.systemUTC());
    private static final AiLifecycleCoordinator COORDINATOR = new AiLifecycleCoordinator(
            TOOL_REGISTRY,
            CONFIRMATIONS,
            () -> YouerConfig.ai_enable,
            AiLifecycle::createCandidate,
            failure -> LOGGER.error("AI initialization failed: category={}, error={}",
                    failure.getClass().getSimpleName(), safeMessage(failure), failure));

    private AiLifecycle() {
    }

    public static void bootstrap() {
        COORDINATOR.bootstrap();
    }

    public static void initialize() {
        COORDINATOR.initialize();
    }

    public static void stopAccepting() {
        COORDINATOR.stopAccepting();
    }

    public static void shutdown() {
        COORDINATOR.shutdown();
    }

    public static AiChatService service() {
        return COORDINATOR.service();
    }

    public static AiInitializationStatus status() {
        return COORDINATOR.status();
    }

    public static boolean confirm(UUID playerId, String id) {
        return CONFIRMATIONS.confirm(playerId, id);
    }

    public static boolean cancel(UUID playerId, String id) {
        return CONFIRMATIONS.cancel(playerId, id);
    }

    public static AiToolRegistry.Snapshot tools(Player player) {
        return TOOL_REGISTRY.snapshot(permission -> player.hasPermission("youer.ai.use")
                && player.hasPermission("youer.ai.tools.use") && player.hasPermission(permission));
    }

    private static AiLifecycleCandidate createCandidate() {
        JdkAiHttpClient httpClient = new JdkAiHttpClient();
        try {
            AiRuntime runtime = AiRuntimeFactory.createFromConfig(httpClient);
            LOGGER.info("AI privacy notice: enabled AI sends player messages and bounded in-memory history to provider={}",
                    runtime.profile().provider());
            AiMetrics metrics = new AiMetrics();
            AiToolPermissions.registerDefaults();
            AiSkillCatalog skillCatalog = new AiSkillLoader(new AiSkillParser(), LOGGER).load(
                    AiLifecycle.class.getClassLoader(), "ai/skills/index.txt",
                    Path.of("youer-config", "ai", "skills"));
            AiSkillRegistry skillRegistry = new AiSkillRegistry(skillCatalog);
            skillRegistry.registerPermissions();
            Function<AiToolContext, AiSkillAccess> skillAccess = context ->
                    new BukkitAiSkillAccess(Bukkit.getPlayer(context.playerId()));
            AiToolOwner owner = new AiToolOwner("runtime", AiToolSource.BUILT_IN, () -> true);
            TOOL_REGISTRY.initializeRuntimeTools(owner, builtIns(owner, skillRegistry, skillAccess));
            AiConfirmationNotifier notifier = new AiConfirmationNotifier(Bukkit::getPlayer);
            AiConfirmationApproval approval = new AiConfirmationApproval(
                    CONFIRMATIONS,
                    Duration.ofSeconds(runtime.confirmationTimeoutSeconds()),
                    runtime.playerCommandsRequireConfirmation(),
                    notifier::notify);
            AiExecutionDispatcher dispatcher = new AiExecutionDispatcher(
                    ForkJoinPool.commonPool(), AiChatHandler::dispatch);
            AiToolExecutor toolExecutor = new AiToolExecutor(
                    TOOL_SCHEMA,
                    approval,
                    dispatcher,
                    (context, permission) -> {
                        Player player = Bukkit.getPlayer(context.playerId());
                        return player != null && player.hasPermission("youer.ai.use")
                                && player.hasPermission("youer.ai.tools.use")
                                && player.hasPermission(permission);
                    },
                    context -> Bukkit.getPlayer(context.playerId()) != null,
                    new AiToolAudit(java.util.logging.Logger.getLogger("Youer AI Tools"),
                            metrics::recordToolOutcome));
            AiCapabilitySnapshotProvider capabilitySnapshots = new AiCapabilitySnapshotProvider(
                    dispatcher, TOOL_REGISTRY, skillRegistry, skillAccess, new AiSkillIndex());
            int historyCharacterBudget = (int) Math.min(1_048_576L,
                    (long) runtime.maxHistory() * runtime.maxResponseChars());
            AiConversationStore history = new AiConversationStore(
                    Clock.systemUTC(), runtime.maxHistory(), historyCharacterBudget,
                    runtime.historyIdle(), 1_024);
            AiChatService service = new AiChatService(
                    runtime, history, new AiAgentLoop(toolExecutor), capabilitySnapshots, metrics);
            return new AiLifecycleCandidate(service, List.of(httpClient, toolExecutor));
        } catch (RuntimeException | Error failure) {
            httpClient.close();
            throw failure;
        }
    }

    private static List<AiRegisteredTool> builtIns(
            AiToolOwner owner,
            AiSkillRegistry skillRegistry,
            Function<AiToolContext, AiSkillAccess> skillAccess) {
        BukkitAiCommandGateway gateway = new BukkitAiCommandGateway();
        AiCommandSanitizer sanitizer = new AiCommandSanitizer();
        Json commandSchema = Json.object().set("type", "object")
                .set("properties", Json.object().set("command",
                        Json.object().set("type", "string").set("minLength", 1)))
                .set("required", Json.array().add("command")).set("additionalProperties", false);
        Json searchSchema = Json.object().set("type", "object")
                .set("properties", Json.object()
                        .set("query", Json.object().set("type", "string"))
                        .set("mode", Json.object().set("type", "string")
                                .set("enum", Json.array().add("player").add("console"))))
                .set("additionalProperties", false);
        List<AiRegisteredTool> tools = new ArrayList<>();
        tools.add(TOOL_REGISTRY.registered(owner, LoadSkillTool.definition(),
                new LoadSkillTool(skillRegistry, TOOL_REGISTRY, skillAccess)));
        tools.add(TOOL_REGISTRY.registered(owner, new AiToolDefinition(
                        "search_commands", "Search visible Minecraft commands", searchSchema,
                        "youer.ai.tools.use", AiToolRisk.READ_ONLY,
                        AiToolExecutionMode.MAIN_THREAD, Duration.ofSeconds(5)),
                new SearchCommandsTool(gateway, Bukkit::getPlayer)));
        tools.add(TOOL_REGISTRY.registered(owner, new AiToolDefinition(
                        "execute_player_command", "Execute one command as the player", commandSchema,
                        "youer.ai.tools.command.player", AiToolRisk.PLAYER_ACTION,
                        AiToolExecutionMode.MAIN_THREAD, Duration.ofSeconds(10)),
                new PlayerCommandTool(gateway, Bukkit::getPlayer), sanitizer));
        tools.add(TOOL_REGISTRY.registered(owner, new AiToolDefinition(
                        "execute_console_command", "Execute one command as the server console", commandSchema,
                        "youer.ai.tools.command.console", AiToolRisk.SERVER_ACTION,
                        AiToolExecutionMode.MAIN_THREAD, Duration.ofSeconds(10)),
                new ConsoleCommandTool(gateway), sanitizer));
        AiToolOwner httpOwner = new AiToolOwner(owner.id(), AiToolSource.CONFIGURED_HTTP, () -> true);
        AiExternalHttpTransport transport = new JdkAiExternalHttpTransport();
        AiHttpToolParseResult configuredHttpTools = new AiHttpToolParser().parse(YouerConfig.ai_http_tools);
        for (AiHttpToolParseResult.Failure failure : configuredHttpTools.failures()) {
            LOGGER.warn("Skipping invalid AI HTTP tool: index={}, name={}, category={}",
                    failure.index(), failure.name(), failure.category());
        }
        for (AiHttpToolDefinition definition : configuredHttpTools.validDefinitions()) {
            AiToolPermissions.ensureOpDefault(definition.tool().permission());
            tools.add(TOOL_REGISTRY.registered(httpOwner, definition.tool(),
                    new AiHttpToolHandler(definition, transport),
                    new AiHttpToolArgumentPreparer(definition)));
        }
        return List.copyOf(tools);
    }

    private static String safeMessage(Throwable failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }
}
