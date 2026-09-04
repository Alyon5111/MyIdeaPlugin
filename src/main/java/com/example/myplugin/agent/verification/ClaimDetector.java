package com.example.myplugin.agent.verification;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ClaimDetector {

    private static final List<Pattern> CLAIM_PATTERNS = new ArrayList<>();

    static {
        CLAIM_PATTERNS.add(Pattern.compile("(?i)should\\s+work"));
        CLAIM_PATTERNS.add(Pattern.compile("(?i)probably\\s+\\w+"));
        CLAIM_PATTERNS.add(Pattern.compile("(?i)seems\\s+to\\s+\\w+"));
        CLAIM_PATTERNS.add(Pattern.compile("(?i)looks\\s+correct"));
        CLAIM_PATTERNS.add(Pattern.compile("(?i)looks\\s+good"));
        CLAIM_PATTERNS.add(Pattern.compile("(?i)all\\s+tests\\s+pass"));
        CLAIM_PATTERNS.add(Pattern.compile("(?i)tests\\s+pass"));
        CLAIM_PATTERNS.add(Pattern.compile("(?i)build\\s+passes"));
        CLAIM_PATTERNS.add(Pattern.compile("(?i)linter\\s+passes"));
        CLAIM_PATTERNS.add(Pattern.compile("(?i)\\bdone\\b"));
        CLAIM_PATTERNS.add(Pattern.compile("(?i)\\bcomplete\\b"));
        CLAIM_PATTERNS.add(Pattern.compile("(?i)\\bfixed\\b"));
        CLAIM_PATTERNS.add(Pattern.compile("(?i)\\bworks\\b"));
        CLAIM_PATTERNS.add(Pattern.compile("(?i)\\bsuccess\\b"));
        CLAIM_PATTERNS.add(Pattern.compile("(?i)\\bverified\\b"));
    }

    public static List<String> detectClaims(String input) {
        List<String> detected = new ArrayList<>();
        if (input == null) return detected;

        for (Pattern pattern : CLAIM_PATTERNS) {
            if (pattern.matcher(input).find()) {
                detected.add(pattern.pattern());
            }
        }
        return detected;
    }

    public static boolean hasClaims(String input) {
        return !detectClaims(input).isEmpty();
    }

    public static String getClaimMessage(List<String> claims) {
        if (claims.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("CLAIMS DETECTED WITHOUT VERIFICATION:\n");
        for (String claim : claims) {
            sb.append("- ").append(claim).append("\n");
        }
        sb.append("\nVERIFICATION REQUIRED:\n");
        sb.append("1. IDENTIFY: What command proves this claim?\n");
        sb.append("2. RUN: Execute the FULL command (fresh, complete)\n");
        sb.append("3. READ: Full output, check exit code, count failures\n");
        sb.append("4. VERIFY: Does output confirm the claim?\n");
        sb.append("5. ONLY THEN: Make the claim\n");
        return sb.toString();
    }
}
