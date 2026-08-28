package com.mohistmc.youer.ai.skill;

import com.mohistmc.youer.ai.tool.AiExecutionDispatcher;
import com.mohistmc.youer.ai.tool.AiToolRegistry;
import com.mohistmc.youer.api.ai.tool.AiToolContext;
import com.mohistmc.youer.api.ai.tool.AiToolDefinition;
import com.mohistmc.youer.api.ai.tool.AiToolExecutionMode;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public final class AiCapabilitySnapshotProvider {

    private final AiExecutionDispatcher dispatcher;
    private final AiToolRegistry toolRegistry;
    private final AiSkillRegistry skillRegistry;
    private final Function<AiToolContext, AiSkillAccess> accessFactory;
    private final AiSkillIndex index;
    private final Map<String, AiSkillIndex.Catalog> catalogs = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, AiSkillIndex.Catalog> eldest) {
            return size() > 256;
        }
    };

    public AiCapabilitySnapshotProvider(
            AiExecutionDispatcher dispatcher,
            AiToolRegistry toolRegistry,
            AiSkillRegistry skillRegistry,
            Function<AiToolContext, AiSkillAccess> accessFactory,
            AiSkillIndex index) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
        this.skillRegistry = Objects.requireNonNull(skillRegistry, "skillRegistry");
        this.accessFactory = Objects.requireNonNull(accessFactory, "accessFactory");
        this.index = Objects.requireNonNull(index, "index");
    }

    public CompletionStage<AiCapabilitySnapshot> snapshot(AiToolContext context) {
        return snapshot(context, true, "");
    }

    public CompletionStage<AiCapabilitySnapshot> snapshot(AiToolContext context, boolean toolsEnabled) {
        return snapshot(context, toolsEnabled, "");
    }

    public CompletionStage<AiCapabilitySnapshot> snapshot(
            AiToolContext context, boolean toolsEnabled, String userMessage) {
        return dispatcher.dispatch(AiToolExecutionMode.MAIN_THREAD,
                () -> CompletableFuture.completedFuture(capture(context, toolsEnabled, userMessage)));
    }

    int cachedCatalogCount() {
        synchronized (catalogs) {
            return catalogs.size();
        }
    }

    private AiCapabilitySnapshot capture(
            AiToolContext context, boolean toolsEnabled, String userMessage) {
        AiSkillAccess access = accessFactory.apply(context);
        AiToolRegistry.Snapshot tools = toolRegistry.snapshot(permission -> toolsEnabled
                        && access.hasPermission("youer.ai.use")
                        && access.hasPermission("youer.ai.tools.use")
                        && access.hasPermission(permission));
        AiSkillSnapshot skills = skillRegistry.snapshot(access, tools);
        String fingerprint = fingerprint(tools, skills);
        AiSkillIndex.Catalog catalog;
        synchronized (catalogs) {
            catalog = catalogs.computeIfAbsent(fingerprint, ignored -> index.catalog(skills));
        }
        return new AiCapabilitySnapshot(tools, skills, index.format(catalog, userMessage));
    }

    private static String fingerprint(AiToolRegistry.Snapshot tools, AiSkillSnapshot skills) {
        List<String> toolNames = tools.definitions().stream()
                .map(AiToolDefinition::name).sorted().toList();
        List<String> skillIds = skills.skills().stream().map(AiSkill::id).sorted().toList();
        return String.join("\u0000", toolNames) + "\u0001" + String.join("\u0000", skillIds);
    }
}
