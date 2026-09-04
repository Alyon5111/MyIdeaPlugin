package com.example.myplugin.agent.branch;

import com.example.myplugin.agent.AgentTool;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

public class FinishingBranchTool implements AgentTool {

    private final CompletionHandler completionHandler;
    private final OptionExecutor optionExecutor;

    public FinishingBranchTool() {
        this.completionHandler = new CompletionHandler();
        this.optionExecutor = new OptionExecutor();
    }

    @Override
    public String name() {
        return "finishing_branch";
    }

    @Override
    public String description() {
        return "Complete development work with structured options: merge, PR, keep, or discard";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
            .name(name())
            .description(description())
            .parameters(JsonObjectSchema.builder()
                .addProperty("action", JsonStringSchema.builder()
                    .description("Action to perform: verify_tests, detect_environment, present_options, select_option, confirm_discard, get_status")
                    .build())
                .addProperty("tests_pass", JsonStringSchema.builder()
                    .description("Whether tests pass (true/false) for verify_tests")
                    .build())
                .addProperty("environment", JsonStringSchema.builder()
                    .description("Environment type (normal_repo, worktree, detached_head) for detect_environment")
                    .build())
                .addProperty("option", JsonStringSchema.builder()
                    .description("Option number (1-4) or name (merge, pr, keep, discard) for select_option")
                    .build())
                .addProperty("confirmation", JsonStringSchema.builder()
                    .description("Confirmation text ('discard' to confirm) for confirm_discard")
                    .build())
                .addProperty("is_worktree", JsonStringSchema.builder()
                    .description("Whether in a worktree (true/false) for present_options")
                    .build())
                .addProperty("is_detached_head", JsonStringSchema.builder()
                    .description("Whether in detached HEAD (true/false) for present_options")
                    .build())
                .build())
            .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        try {
            String action = arguments.has("action") ? arguments.get("action").getAsString() : "";

            switch (action.toLowerCase()) {
                case "verify_tests":
                    return verifyTests(arguments);
                case "detect_environment":
                    return detectEnvironment(arguments);
                case "present_options":
                    return presentOptions(arguments);
                case "select_option":
                    return selectOption(arguments);
                case "confirm_discard":
                    return confirmDiscard(arguments);
                case "get_status":
                    return getStatus();
                default:
                    return "Unknown action: " + action + ". Use: verify_tests, detect_environment, present_options, select_option, confirm_discard, get_status";
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String verifyTests(JsonObject arguments) {
        String testsPassStr = arguments.has("tests_pass") ? arguments.get("tests_pass").getAsString() : "false";
        boolean testsPass = testsPassStr.toLowerCase().startsWith("t");

        completionHandler.verifyTests(testsPass);

        StringBuilder sb = new StringBuilder();
        sb.append("=== TEST VERIFICATION ===\n");

        if (testsPass) {
            sb.append("[PASS] All tests passing.\n");
            sb.append("Ready to proceed with completion options.");
        } else {
            sb.append("[FAIL] Tests are failing.\n");
            sb.append("Cannot proceed with merge/PR until tests pass.");
        }

        return sb.toString();
    }

    private String detectEnvironment(JsonObject arguments) {
        String environment = arguments.has("environment") ? arguments.get("environment").getAsString() : "normal_repo";
        completionHandler.detectEnvironment(environment);

        StringBuilder sb = new StringBuilder();
        sb.append("=== ENVIRONMENT DETECTED ===\n");
        sb.append("Type: ").append(environment).append("\n\n");

        switch (environment.toLowerCase()) {
            case "normal_repo":
                sb.append("You are in a normal repository checkout.\n");
                sb.append("All 4 options available.");
                break;
            case "worktree":
                sb.append("You are in a linked worktree.\n");
                sb.append("All 4 options available. Worktree cleanup may be needed.");
                break;
            case "detached_head":
                sb.append("You are in a detached HEAD state.\n");
                sb.append("3 options available (no local merge).");
                break;
        }

        return sb.toString();
    }

    private String presentOptions(JsonObject arguments) {
        boolean isWorktree = arguments.has("is_worktree") && arguments.get("is_worktree").getAsBoolean();
        boolean isDetachedHead = arguments.has("is_detached_head") && arguments.get("is_detached_head").getAsBoolean();

        completionHandler.presentOptions();

        return optionExecutor.getOptionsMenu(isWorktree, isDetachedHead);
    }

    private String selectOption(JsonObject arguments) {
        String optionStr = arguments.has("option") ? arguments.get("option").getAsString() : null;
        if (optionStr == null || optionStr.isEmpty()) {
            return "Error: 'option' is required";
        }

        OptionExecutor.Option option;
        switch (optionStr.toLowerCase()) {
            case "1":
            case "merge":
                option = OptionExecutor.Option.MERGE_LOCALLY;
                break;
            case "2":
            case "pr":
                option = OptionExecutor.Option.CREATE_PR;
                break;
            case "3":
            case "keep":
                option = OptionExecutor.Option.KEEP_AS_IS;
                break;
            case "4":
            case "discard":
                option = OptionExecutor.Option.DISCARD;
                break;
            default:
                return "Invalid option: " + optionStr + ". Use 1-4 or merge/pr/keep/discard";
        }

        optionExecutor.selectOption(option);
        completionHandler.selectOption(option.toString());

        StringBuilder sb = new StringBuilder();
        sb.append("=== OPTION SELECTED ===\n");
        sb.append(optionExecutor.getOptionDescription()).append("\n\n");

        if (option == OptionExecutor.Option.DISCARD) {
            sb.append("WARNING: This will permanently delete:\n");
            sb.append("- Current branch\n");
            sb.append("- All commits on this branch\n\n");
            sb.append("Type 'discard' to confirm.");
        } else if (option == OptionExecutor.Option.MERGE_LOCALLY) {
            sb.append("Next: Merge will be executed.");
        } else if (option == OptionExecutor.Option.CREATE_PR) {
            sb.append("Next: Branch will be pushed and PR created.");
        } else {
            sb.append("Branch preserved. You can handle it later.");
        }

        return sb.toString();
    }

    private String confirmDiscard(JsonObject arguments) {
        String confirmation = arguments.has("confirmation") ? arguments.get("confirmation").getAsString() : "";

        String result = optionExecutor.confirmDiscard(confirmation);

        if (optionExecutor.isConfirmed()) {
            completionHandler.startExecution();
            return "=== DISCARD CONFIRMED ===\n" + result + "\n\nExecuting discard...";
        }

        return result;
    }

    private String getStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append(completionHandler.getStatusReport());
        sb.append("\n");
        sb.append("Selected Option: ").append(optionExecutor.getOptionDescription()).append("\n");
        sb.append("Confirmed: ").append(optionExecutor.isConfirmed() ? "YES" : "NO").append("\n");
        return sb.toString();
    }
}
