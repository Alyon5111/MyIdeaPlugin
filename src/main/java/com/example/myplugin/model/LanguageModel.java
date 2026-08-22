package com.example.myplugin.model;

public class LanguageModel {

    private ModelProvider provider;
    private String modelName;
    private String displayName;
    private String status;
    private boolean apiKeyUsed;
    private double inputCost;
    private double outputCost;
    private int inputMaxTokens;

    public LanguageModel() {}

    public LanguageModel(ModelProvider provider, String modelName, String displayName,
                         boolean apiKeyUsed, double inputCost, double outputCost, int inputMaxTokens) {
        this.provider = provider;
        this.modelName = modelName;
        this.displayName = displayName;
        this.apiKeyUsed = apiKeyUsed;
        this.inputCost = inputCost;
        this.outputCost = outputCost;
        this.inputMaxTokens = inputMaxTokens;
    }

    public ModelProvider getProvider() { return provider; }
    public void setProvider(ModelProvider provider) { this.provider = provider; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isApiKeyUsed() { return apiKeyUsed; }
    public void setApiKeyUsed(boolean apiKeyUsed) { this.apiKeyUsed = apiKeyUsed; }

    public double getInputCost() { return inputCost; }
    public void setInputCost(double inputCost) { this.inputCost = inputCost; }

    public double getOutputCost() { return outputCost; }
    public void setOutputCost(double outputCost) { this.outputCost = outputCost; }

    public int getInputMaxTokens() { return inputMaxTokens; }
    public void setInputMaxTokens(int inputMaxTokens) { this.inputMaxTokens = inputMaxTokens; }

    @Override
    public String toString() {
        return displayName;
    }

    public static LanguageModelBuilder builder() { return new LanguageModelBuilder(); }

    public static class LanguageModelBuilder {
        private final LanguageModel model = new LanguageModel();

        public LanguageModelBuilder provider(ModelProvider provider) { model.provider = provider; return this; }
        public LanguageModelBuilder modelName(String modelName) { model.modelName = modelName; return this; }
        public LanguageModelBuilder displayName(String displayName) { model.displayName = displayName; return this; }
        public LanguageModelBuilder status(String status) { model.status = status; return this; }
        public LanguageModelBuilder apiKeyUsed(boolean apiKeyUsed) { model.apiKeyUsed = apiKeyUsed; return this; }
        public LanguageModelBuilder inputCost(double inputCost) { model.inputCost = inputCost; return this; }
        public LanguageModelBuilder outputCost(double outputCost) { model.outputCost = outputCost; return this; }
        public LanguageModelBuilder inputMaxTokens(int inputMaxTokens) { model.inputMaxTokens = inputMaxTokens; return this; }
        public LanguageModel build() { return model; }
    }
}
