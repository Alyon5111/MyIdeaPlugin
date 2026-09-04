package com.example.myplugin.agent.verification;

import com.example.myplugin.agent.AgentTool;
import com.google.gson.JsonObject;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

import java.util.List;

public class VerificationGateTool implements AgentTool {

    private final VerificationGate gate;

    public VerificationGateTool() {
        this.gate = new VerificationGate();
    }

    @Override
    public String name() {
        return "verification_gate";
    }

    @Override
    public String description() {
        return "Prevents completion claims without verification evidence (5-step gate)";
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
            .name(name())
            .description(description())
            .parameters(JsonObjectSchema.builder()
                .addProperty("action", JsonStringSchema.builder()
                    .description("Action to perform: start_verification, identify_command, run_command, read_output, verify_result, make_claim, get_status, cancel")
                    .build())
                .addProperty("input", JsonStringSchema.builder()
                    .description("Input for the action (claim, command, output, analysis)")
                    .build())
                .build())
            .build();
    }

    @Override
    public String execute(JsonObject arguments) {
        try {
            String action = arguments.has("action") ? arguments.get("action").getAsString() : "";
            String input = arguments.has("input") ? arguments.get("input").getAsString() : "";

            switch (action.toLowerCase()) {
                case "start_verification":
                    return startVerification(input);
                case "identify_command":
                    return identifyCommand(input);
                case "run_command":
                    return runCommand(input);
                case "read_output":
                    return readOutput(input);
                case "verify_result":
                    return verifyResult(input);
                case "make_claim":
                    return makeClaim(input);
                case "get_status":
                    return getStatus();
                case "cancel":
                    return cancel();
                default:
                    return "Unknown action: " + action + ". Use: start_verification, identify_command, run_command, read_output, verify_result, make_claim, get_status, cancel";
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String startVerification(String claim) {
        if (claim.isEmpty()) {
            return "Please provide the claim you want to verify.";
        }

        List<String> claims = ClaimDetector.detectClaims(claim);
        if (!claims.isEmpty()) {
            return ClaimDetector.getClaimMessage(claims);
        }

        gate.startVerification(claim);
        return "=== VERIFICATION GATE STARTED ===\n\n" +
            "Claim: " + claim + "\n\n" +
            "Step 1: IDENTIFY\n" +
            "What command proves this claim?\n" +
            "Examples: ./gradlew test, npm run lint, mvn verify";
    }

    private String identifyCommand(String command) {
        if (gate.isIdle()) {
            return "No active verification. Use 'start_verification' first.";
        }

        VerificationGate.TransitionResult result = gate.identifyCommand(command);
        return formatResult(result);
    }

    private String runCommand(String output) {
        if (gate.isIdle()) {
            return "No active verification. Use 'start_verification' first.";
        }

        VerificationGate.TransitionResult result = gate.runCommand(output);
        return formatResult(result);
    }

    private String readOutput(String analysis) {
        if (gate.isIdle()) {
            return "No active verification. Use 'start_verification' first.";
        }

        VerificationGate.TransitionResult result = gate.readOutput(analysis);
        return formatResult(result);
    }

    private String verifyResult(String confirmed) {
        if (gate.isIdle()) {
            return "No active verification. Use 'start_verification' first.";
        }

        boolean isConfirmed = confirmed.toLowerCase().startsWith("y") ||
            confirmed.toLowerCase().contains("pass") ||
            confirmed.toLowerCase().contains("confirm");

        VerificationGate.TransitionResult result = gate.verifyResult(isConfirmed);
        return formatResult(result);
    }

    private String makeClaim(String claimText) {
        if (gate.isIdle()) {
            return "No active verification. Use 'start_verification' first.";
        }

        VerificationGate.TransitionResult result = gate.makeClaim(claimText);
        return formatResult(result);
    }

    private String getStatus() {
        if (gate.isIdle()) {
            return "No active verification.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== VERIFICATION STATUS ===\n");
        sb.append("Step: ").append(gate.getCurrentStep()).append("\n");
        sb.append("Claim: ").append(gate.getClaim()).append("\n");
        sb.append("Command: ").append(gate.getCommand()).append("\n");
        sb.append("Verified: ").append(gate.isVerified() ? "YES" : "NO").append("\n");
        return sb.toString();
    }

    private String cancel() {
        gate.cancel();
        return "Verification cancelled.";
    }

    private String formatResult(VerificationGate.TransitionResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== VERIFICATION UPDATE ===\n\n");

        if (result.isSuccess()) {
            sb.append("[OK] ").append(result.getMessage()).append("\n");
        } else {
            sb.append("[FAIL] ").append(result.getMessage()).append("\n");
        }

        sb.append("\nCurrent Step: ").append(gate.getCurrentStep()).append("\n");
        sb.append("Verified: ").append(gate.isVerified() ? "YES" : "NO").append("\n");

        return sb.toString();
    }
}
