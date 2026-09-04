package com.example.myplugin.agent.branch;

import java.util.ArrayList;
import java.util.List;

public class OptionExecutor {

    public enum Option {
        MERGE_LOCALLY,
        CREATE_PR,
        KEEP_AS_IS,
        DISCARD
    }

    private Option selectedOption;
    private boolean confirmed;
    private final List<String> history;

    public OptionExecutor() {
        this.selectedOption = null;
        this.confirmed = false;
        this.history = new ArrayList<>();
    }

    public Option getSelectedOption() { return selectedOption; }
    public boolean isConfirmed() { return confirmed; }
    public List<String> getHistory() { return history; }

    public void selectOption(Option option) {
        this.selectedOption = option;
        this.confirmed = false;
        history.add("SELECTED: " + option);
    }

    public String confirmDiscard(String confirmationText) {
        if (selectedOption != Option.DISCARD) {
            return "Error: No discard option selected";
        }

        if (confirmationText.equals("discard")) {
            this.confirmed = true;
            history.add("CONFIRMED: Discard");
            return "Discard confirmed. Proceeding...";
        } else {
            return "Type 'discard' to confirm. Operation cancelled.";
        }
    }

    public String getOptionDescription() {
        if (selectedOption == null) {
            return "No option selected";
        }

        switch (selectedOption) {
            case MERGE_LOCALLY:
                return "Merge back to base branch locally";
            case CREATE_PR:
                return "Push and create a Pull Request";
            case KEEP_AS_IS:
                return "Keep the branch as-is (handle later)";
            case DISCARD:
                return "Discard this work";
            default:
                return "Unknown option";
        }
    }

    public String getOptionsMenu(boolean isWorktree, boolean isDetachedHead) {
        StringBuilder sb = new StringBuilder();
        sb.append("Implementation complete. ");

        if (isDetachedHead) {
            sb.append("You're on a detached HEAD.\n\n");
            sb.append("1. Push as new branch and create a Pull Request\n");
            sb.append("2. Keep as-is (I'll handle it later)\n");
            sb.append("3. Discard this work\n");
        } else if (isWorktree) {
            sb.append("You're in a worktree.\n\n");
            sb.append("1. Merge back to base branch locally\n");
            sb.append("2. Push and create a Pull Request\n");
            sb.append("3. Keep the branch as-is (I'll handle it later)\n");
            sb.append("4. Discard this work\n");
        } else {
            sb.append("You're in a normal repository.\n\n");
            sb.append("1. Merge back to base branch locally\n");
            sb.append("2. Push and create a Pull Request\n");
            sb.append("3. Keep the branch as-is (I'll handle it later)\n");
            sb.append("4. Discard this work\n");
        }

        sb.append("\nWhich option?");
        return sb.toString();
    }
}
