package com.example.myplugin.agent.git;

import java.util.ArrayList;
import java.util.List;

public class BranchManager {

    public enum BranchAction {
        CREATE,
        SWITCH,
        DELETE,
        MERGE,
        LIST
    }

    private String currentBranch;
    private String baseBranch;
    private final List<String> branches;
    private final List<String> history;

    public BranchManager() {
        this.branches = new ArrayList<>();
        this.history = new ArrayList<>();
    }

    public String getCurrentBranch() { return currentBranch; }
    public String getBaseBranch() { return baseBranch; }
    public List<String> getBranches() { return branches; }
    public List<String> getHistory() { return history; }

    public void setCurrentBranch(String branch) {
        this.currentBranch = branch;
        history.add("CURRENT BRANCH: " + branch);
    }

    public void setBaseBranch(String branch) {
        this.baseBranch = branch;
        history.add("BASE BRANCH: " + branch);
    }

    public String createBranch(String branchName) {
        if (branches.contains(branchName)) {
            return "Error: Branch '" + branchName + "' already exists";
        }

        branches.add(branchName);
        history.add("CREATED: " + branchName);
        return "Branch '" + branchName + "' created";
    }

    public String switchBranch(String branchName) {
        if (!branches.contains(branchName)) {
            return "Error: Branch '" + branchName + "' does not exist";
        }

        this.currentBranch = branchName;
        history.add("SWITCHED TO: " + branchName);
        return "Switched to branch '" + branchName + "'";
    }

    public String deleteBranch(String branchName, boolean force) {
        if (!branches.contains(branchName)) {
            return "Error: Branch '" + branchName + "' does not exist";
        }

        if (branchName.equals(currentBranch)) {
            return "Error: Cannot delete current branch";
        }

        if (!force && branchName.equals(baseBranch)) {
            return "Error: Cannot delete base branch without force";
        }

        branches.remove(branchName);
        history.add((force ? "FORCE DELETED" : "DELETED") + ": " + branchName);
        return "Branch '" + branchName + "' deleted";
    }

    public String mergeBranch(String sourceBranch, String targetBranch) {
        if (!branches.contains(sourceBranch)) {
            return "Error: Source branch '" + sourceBranch + "' does not exist";
        }
        if (!branches.contains(targetBranch)) {
            return "Error: Target branch '" + targetBranch + "' does not exist";
        }

        history.add("MERGED: " + sourceBranch + " into " + targetBranch);
        return "Merged '" + sourceBranch + "' into '" + targetBranch + "'";
    }

    public String getBranchList() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== BRANCHES ===\n");
        sb.append("Current: ").append(currentBranch).append("\n");
        sb.append("Base: ").append(baseBranch).append("\n\n");
        for (String branch : branches) {
            String marker = branch.equals(currentBranch) ? " *" : "";
            sb.append(branch).append(marker).append("\n");
        }
        return sb.toString();
    }
}
