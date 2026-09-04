package com.example.myplugin.agent;

public class AgentErrorMessages {
    public static String connectionError(String url, String originalError) {
        return "Cannot connect to LLM server at " + url + "\n" +
               "Make sure your llama.cpp server is running.\n" +
               "Original error: " + originalError;
    }

    public static String jsonParseError(String originalError) {
        return "The agent received an invalid response format from the LLM.\n" +
               "Troubleshooting: Try a different model or a simpler prompt.\n" +
               "Original error: " + originalError;
    }

    public static String toolExecutionError(String toolName, String error) {
        return "Error executing tool '" + toolName + "': " + error + "\n" +
               "Troubleshooting: Check the tool parameters and configuration.";
    }

    public static String contextLimitError() {
        return "The conversation has become too long for the current model's context window.\n" +
               "Troubleshooting: Start a new conversation to clear history.";
    }

    public static String genericError(String error) {
        return "An unexpected error occurred in the agent: " + error;
    }
}
