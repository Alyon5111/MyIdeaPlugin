package com.example.myplugin.agent.git;

import com.example.myplugin.agent.AgentTool;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

public class GitWorkflowTool implements AgentTool {

    private final WorktreeManager worktreeManager;
    private final BranchManager branchManager;

    public GitWorkflowTool() {
        this.worktreeManager = new WorktreeManager();
        this.branchManager = new BranchManager();
    }

    @Override
    public String name() {
        return "git_workflow";
    }

    @Override
    public String description() {
        return "Git workflow management: worktree detection, branch operations, merge";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
            .name(name())
            .description(description())
            .parameters(JsonObjectSchema.builder()
                .addProperty("action", JsonStringSchema.builder()
                    .description("Action to perform: detect_environment, create_branch, switch_branch, delete_branch, merge_branch, list_branches, get_worktree_info")
                    .build())
                .addProperty("branch_name", JsonStringSchema.builder()
                    .description("Branch name (for branch operations)")
                    .build())
                .addProperty("target_branch", JsonStringSchema.builder()
                    .description("Target branch for merge")
                    .build())
                .addProperty("git_dir", JsonStringSchema.builder()
                    .description("Git directory path (for detect_environment)")
                    .build())
                .addProperty("git_common_dir", JsonStringSchema.builder()
                    .description("Git common directory path (for detect_environment)")
                    .build())
                .addProperty("current_branch", JsonStringSchema.builder()
                    .description("Current branch name (for detect_environment)")
                    .build())
                .addProperty("worktree_path", JsonStringSchema.builder()
                    .description("Worktree path (for detect_environment)")
                    .build())
                .addProperty("base_branch", JsonStringSchema.builder()
                    .description("Base branch name (for detect_environment)")
                    .build())
                .addProperty("force", JsonStringSchema.builder()
                    .description("Force delete (for delete_branch)")
                    .build())
                .build())
            .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        try {
            String action = arguments.has("action") ? arguments.get("action").getAsString() : "";

            switch (action.toLowerCase()) {
                case "detect_environment":
                    return detectEnvironment(arguments);
                case "create_branch":
                    return createBranch(arguments);
                case "switch_branch":
                    return switchBranch(arguments);
                case "delete_branch":
                    return deleteBranch(arguments);
                case "merge_branch":
                    return mergeBranch(arguments);
                case "list_branches":
                    return listBranches();
                case "get_worktree_info":
                    return getWorktreeInfo();
                default:
                    return "Unknown action: " + action + ". Use: detect_environment, create_branch, switch_branch, delete_branch, merge_branch, list_branches, get_worktree_info";
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String detectEnvironment(JsonObject arguments) {
        String gitDir = arguments.has("git_dir") ? arguments.get("git_dir").getAsString() : "";
        String gitCommonDir = arguments.has("git_common_dir") ? arguments.get("git_common_dir").getAsString() : "";
        String currentBranch = arguments.has("current_branch") ? arguments.get("current_branch").getAsString() : "";
        String worktreePath = arguments.has("worktree_path") ? arguments.get("worktree_path").getAsString() : "";
        String baseBranch = arguments.has("base_branch") ? arguments.get("base_branch").getAsString() : "main";

        worktreeManager.detectEnvironment(gitDir, gitCommonDir, currentBranch, worktreePath);
        branchManager.setCurrentBranch(currentBranch);
        branchManager.setBaseBranch(baseBranch);

        StringBuilder sb = new StringBuilder();
        sb.append("=== GIT ENVIRONMENT DETECTED ===\n\n");
        sb.append(worktreeManager.getWorktreeInfo());
        sb.append("\n");
        sb.append("Recommendations:\n");

        switch (worktreeManager.getStatus()) {
            case NORMAL_REPO:
                sb.append("- You are in a normal repository checkout.\n");
                sb.append("- Consider creating a worktree for isolated work.\n");
                break;
            case IN_WORKTREE:
                sb.append("- You are already in an isolated worktree.\n");
                sb.append("- No need to create another worktree.\n");
                break;
            case DETACHED_HEAD:
                sb.append("- You are in a detached HEAD state.\n");
                sb.append("- Create a branch before making changes.\n");
                break;
        }

        return sb.toString();
    }

    private String createBranch(JsonObject arguments) {
        String branchName = arguments.has("branch_name") ? arguments.get("branch_name").getAsString() : null;
        if (branchName == null || branchName.isEmpty()) {
            return "Error: 'branch_name' is required";
        }

        String result = branchManager.createBranch(branchName);
        return "=== BRANCH CREATED ===\n" + result;
    }

    private String switchBranch(JsonObject arguments) {
        String branchName = arguments.has("branch_name") ? arguments.get("branch_name").getAsString() : null;
        if (branchName == null || branchName.isEmpty()) {
            return "Error: 'branch_name' is required";
        }

        String result = branchManager.switchBranch(branchName);
        return "=== BRANCH SWITCHED ===\n" + result;
    }

    private String deleteBranch(JsonObject arguments) {
        String branchName = arguments.has("branch_name") ? arguments.get("branch_name").getAsString() : null;
        boolean force = arguments.has("force") && arguments.get("force").getAsBoolean();

        if (branchName == null || branchName.isEmpty()) {
            return "Error: 'branch_name' is required";
        }

        String result = branchManager.deleteBranch(branchName, force);
        return "=== BRANCH DELETED ===\n" + result;
    }

    private String mergeBranch(JsonObject arguments) {
        String branchName = arguments.has("branch_name") ? arguments.get("branch_name").getAsString() : null;
        String targetBranch = arguments.has("target_branch") ? arguments.get("target_branch").getAsString() : null;

        if (branchName == null || branchName.isEmpty()) {
            return "Error: 'branch_name' (source) is required";
        }
        if (targetBranch == null || targetBranch.isEmpty()) {
            targetBranch = branchManager.getCurrentBranch();
        }

        String result = branchManager.mergeBranch(branchName, targetBranch);
        return "=== BRANCH MERGED ===\n" + result;
    }

    private String listBranches() {
        return branchManager.getBranchList();
    }

    private String getWorktreeInfo() {
        return worktreeManager.getWorktreeInfo();
    }
}
