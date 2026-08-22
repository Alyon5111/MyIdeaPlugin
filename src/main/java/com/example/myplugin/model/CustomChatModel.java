package com.example.myplugin.model;

public class CustomChatModel {

    private String baseUrl;
    private String modelName;
    private double temperature = Constant.TEMPERATURE;
    private double topP = Constant.TOP_P;
    private int maxTokens = Constant.MAX_OUTPUT_TOKENS;
    private int maxRetries = Constant.MAX_RETRIES;
    private int timeout = Constant.TIMEOUT;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public double getTopP() { return topP; }
    public void setTopP(double topP) { this.topP = topP; }

    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

    public int getTimeout() { return timeout; }
    public void setTimeout(int timeout) { this.timeout = timeout; }
}
