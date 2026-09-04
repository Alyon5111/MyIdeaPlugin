package com.example.myplugin.model;

public class Constant {

    private Constant() {
    }

    public static final String LLAMA_CPP_MODEL_URL = "http://localhost:8080";

    public static final Double TEMPERATURE = 0.7d;
    public static final Double TOP_P = 0.9d;
    public static final Integer MAX_OUTPUT_TOKENS = 4000;
    public static final Integer MAX_RETRIES = 1;
    public static final Integer TIMEOUT = 300;
    public static final Integer AGENT_MAX_ITERATIONS = 15;
    public static final Integer AGENT_MAX_TOOL_CALLS = 8;
    public static final Integer AGENT_MAX_HISTORY = 20;
    public static final Integer AGENT_MAX_CONTEXT_MESSAGES = 6;

    public static final Boolean MEMORY_ENABLED = true;
    public static final Boolean MEMORY_EXTRACTION_ENABLED = true;
    public static final Integer MEMORY_MAX_INJECTION = 5;
    public static final Boolean MEMORY_AUTO_PRUNE = true;
    public static final Integer MEMORY_MAX_ENTRIES = 500;
}
