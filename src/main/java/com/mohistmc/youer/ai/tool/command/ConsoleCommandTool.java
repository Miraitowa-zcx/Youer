package com.mohistmc.youer.ai.tool.command;

import com.mohistmc.mjson.Json;
import com.mohistmc.youer.api.ai.tool.AiToolContext;
import com.mohistmc.youer.api.ai.tool.AiToolHandler;
import com.mohistmc.youer.api.ai.tool.AiToolResult;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class ConsoleCommandTool implements AiToolHandler {
    private final AiCommandGateway gateway;
    public ConsoleCommandTool(AiCommandGateway gateway) {
        this.gateway = gateway;
    }
    @Override public CompletionStage<AiToolResult> execute(AiToolContext context, Json arguments) {
        String command = arguments.at("command").asString();
        return CompletableFuture.completedFuture(gateway.dispatchConsole(command)
                ? AiToolResult.success("Bukkit accepted the console command dispatch; this does not prove the "
                        + "command's business outcome. Validate observable server state before claiming success.")
                : AiToolResult.error("Console command was not dispatched"));
    }
}
