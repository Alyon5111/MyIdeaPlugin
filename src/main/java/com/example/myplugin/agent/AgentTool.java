package com.example.myplugin.agent;

import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolSpecification;

public interface AgentTool {

    String name();

    String description();

    ToolSpecification specification();

    String execute(JsonObject arguments);
}
