package com.example.myplugin.agent.git;

import java.util.ArrayList;
import java.util.List;

public class WorktreeManager {

    public enum WorktreeStatus {
        NORMAL_REPO,
        IN_WORKTREE,
        DETACHED_HEAD
    }

    private WorktreeStatus status;
    private String worktreePath;
    private String branchName;
    private String gitDir;
    private String gitCommonDir;
    private final List<String> history;

    public WorktreeManager() {
        this.status = WorktreeStatus.NORMAL_REPO;
        this.history = new ArrayList<>();
    }

    public WorktreeStatus getStatus() { return status; }
    public String getWorktreePath() { return worktreePath; }
    public String getBranchName() { return branchName; }
    public String getGitDir() { return gitDir; }
    public String getGitCommonDir() { return gitCommonDir; }
    public List<String> getHistory() { return history; }

    public void detectEnvironment(String gitDir, String gitCommonDir, String branchName, String worktreePath) {
        this.gitDir = gitDir;
        this.gitCommonDir = gitCommonDir;
        this.branchName = branchName;
        this.worktreePath = worktreePath;

        if (gitDir.equals(gitCommonDir)) {
            this.status = WorktreeStatus.NORMAL_REPO;
            history.add("ENVIRONMENT: Normal repository");
        } else if (branchName.equals("HEAD")) {
            this.status = WorktreeStatus.DETACHED_HEAD;
            history.add("ENVIRONMENT: Detached HEAD in worktree");
        } else {
            this.status = WorktreeStatus.IN_WORKTREE;
            history.add("ENVIRONMENT: In worktree on branch " + branchName);
        }
    }

    public String getWorktreeInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== WORKTREE INFO ===\n");
        sb.append("Status: ").append(status).append("\n");
        sb.append("Path: ").append(worktreePath).append("\n");
        sb.append("Branch: ").append(branchName).append("\n");
        sb.append("Git Dir: ").append(gitDir).append("\n");
        sb.append("Git Common Dir: ").append(gitCommonDir).append("\n");
        return sb.toString();
    }

    public boolean isInWorktree() {
        return status == WorktreeStatus.IN_WORKTREE || status == WorktreeStatus.DETACHED_HEAD;
    }

    public boolean isDetachedHead() {
        return status == WorktreeStatus.DETACHED_HEAD;
    }

    public boolean isNormalRepo() {
        return status == WorktreeStatus.NORMAL_REPO;
    }
}
