package com.example.myplugin.openspec.manager;

import com.example.myplugin.openspec.model.DeltaPlan;
import com.example.myplugin.openspec.model.MergeResult;
import com.example.myplugin.openspec.model.Spec;
import com.example.myplugin.openspec.merger.DeltaMerger;
import com.example.myplugin.openspec.parser.DeltaSpecParser;
import com.example.myplugin.openspec.parser.SpecParser;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages the OpenSpec directory structure and operations.
 */
public class OpenSpecManager {

    private static final String OPENSPEC_DIR = "openspec";
    private static final String SPECS_DIR = "specs";
    private static final String CHANGES_DIR = "changes";
    private static final String ARCHIVE_DIR = "archive";
    private static final DateTimeFormatter DATE_PREFIX = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final Path projectPath;
    private final DeltaMerger deltaMerger;
    private final DeltaSpecParser deltaSpecParser;
    private final SpecParser specParser;

    public OpenSpecManager(Path projectPath) {
        this.projectPath = projectPath;
        this.deltaMerger = new DeltaMerger();
        this.deltaSpecParser = new DeltaSpecParser();
        this.specParser = new SpecParser();
    }

    public Path getOpenspecDir() { return projectPath.resolve(OPENSPEC_DIR); }
    public Path getSpecsDir() { return getOpenspecDir().resolve(SPECS_DIR); }
    public Path getChangesDir() { return getOpenspecDir().resolve(CHANGES_DIR); }
    public Path getArchiveDir() { return getChangesDir().resolve(ARCHIVE_DIR); }

    /**
     * Initialize OpenSpec directory structure.
     */
    public void init() throws IOException {
        Files.createDirectories(getSpecsDir());
        Files.createDirectories(getChangesDir());
        Files.createDirectories(getArchiveDir());
    }

    public boolean isInitialized() {
        return Files.exists(getOpenspecDir()) &&
               Files.exists(getSpecsDir()) &&
               Files.exists(getChangesDir());
    }

    /**
     * List all spec files.
     */
    public List<SpecInfo> listSpecs() throws IOException {
        List<SpecInfo> specs = new ArrayList<>();
        if (!Files.exists(getSpecsDir())) return specs;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(getSpecsDir())) {
            for (Path dir : stream) {
                if (Files.isDirectory(dir)) {
                    Path specFile = dir.resolve("spec.md");
                    if (Files.exists(specFile)) {
                        String content = Files.readString(specFile, StandardCharsets.UTF_8);
                        Spec spec = specParser.parse(content);
                        specs.add(new SpecInfo(
                            dir.getFileName().toString(),
                            spec.getTitle(),
                            spec.getPurpose(),
                            spec.getRequirements().size(),
                            specFile
                        ));
                    }
                }
            }
        }
        return specs;
    }

    /**
     * List all active changes.
     */
    public List<ChangeInfo> listChanges() throws IOException {
        List<ChangeInfo> changes = new ArrayList<>();
        if (!Files.exists(getChangesDir())) return changes;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(getChangesDir())) {
            for (Path dir : stream) {
                if (Files.isDirectory(dir) && !dir.getFileName().toString().equals(ARCHIVE_DIR)) {
                    ChangeInfo info = readChangeInfo(dir);
                    if (info != null) changes.add(info);
                }
            }
        }
        return changes;
    }

    /**
     * Read info about a specific change.
     */
    public ChangeInfo getChangeInfo(String changeName) throws IOException {
        Path changeDir = getChangesDir().resolve(changeName);
        if (!Files.exists(changeDir)) return null;
        return readChangeInfo(changeDir);
    }

    private ChangeInfo readChangeInfo(Path changeDir) {
        try {
            String name = changeDir.getFileName().toString();
            Path proposalFile = changeDir.resolve("proposal.md");
            Path tasksFile = changeDir.resolve("tasks.md");
            Path specsDir = changeDir.resolve(SPECS_DIR);

            String proposal = Files.exists(proposalFile)
                ? Files.readString(proposalFile, StandardCharsets.UTF_8)
                : "";

            // Count tasks
            int totalTasks = 0;
            int completedTasks = 0;
            if (Files.exists(tasksFile)) {
                List<String> lines = Files.readAllLines(tasksFile, StandardCharsets.UTF_8);
                for (String line : lines) {
                    if (line.matches("^\\s*-\\s*\\[\\s*\\]\\s*.*")) totalTasks++;
                    if (line.matches("^\\s*-\\s*\\[x\\]\\s*.*")) { totalTasks++; completedTasks++; }
                }
            }

            // Count delta specs
            int deltaSpecCount = 0;
            if (Files.exists(specsDir)) {
                deltaSpecCount = countFiles(specsDir);
            }

            return new ChangeInfo(name, proposal, totalTasks, completedTasks, deltaSpecCount, changeDir);
        } catch (IOException e) {
            return null;
        }
    }

    private int countFiles(Path dir) throws IOException {
        int count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    count += countFiles(entry);
                } else {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Create a new change with scaffolded structure.
     */
    public Path createChange(String changeName) throws IOException {
        Path changeDir = getChangesDir().resolve(changeName);
        Files.createDirectories(changeDir);
        Files.createDirectories(changeDir.resolve(SPECS_DIR));

        // Create proposal.md template
        String proposal = "# " + changeName + "\n\n## Why\n\n[Describe the motivation]\n\n## What Changes\n\n[Describe what will change]\n\n## Impact\n\n- Affected specs: [list]\n";
        Files.writeString(changeDir.resolve("proposal.md"), proposal, StandardCharsets.UTF_8);

        // Create tasks.md template
        String tasks = "# Tasks\n\n- [ ] 1. [First task]\n- [ ] 2. [Second task]\n";
        Files.writeString(changeDir.resolve("tasks.md"), tasks, StandardCharsets.UTF_8);

        return changeDir;
    }

    /**
     * Archive a change: merge delta specs into main specs, then move to archive.
     */
    public ArchiveResult archiveChange(String changeName) throws IOException {
        return archiveChange(changeName, true);
    }

    public ArchiveResult archiveChange(String changeName, boolean mergeSpecs) throws IOException {
        Path changeDir = getChangesDir().resolve(changeName);
        if (!Files.exists(changeDir)) {
            throw new IOException("Change '" + changeName + "' not found");
        }

        List<String> warnings = new ArrayList<>();
        List<String> updatedSpecs = new ArrayList<>();

        // Merge delta specs if requested
        if (mergeSpecs) {
            Path changeSpecsDir = changeDir.resolve(SPECS_DIR);
            if (Files.exists(changeSpecsDir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(changeSpecsDir)) {
                    for (Path domainDir : stream) {
                        if (Files.isDirectory(domainDir)) {
                            Path deltaSpecFile = domainDir.resolve("spec.md");
                            if (Files.exists(deltaSpecFile)) {
                                String domain = domainDir.getFileName().toString();
                                String deltaContent = Files.readString(deltaSpecFile, StandardCharsets.UTF_8);
                                DeltaPlan delta = deltaSpecParser.parse(deltaContent);

                                if (!delta.isEmpty()) {
                                    Path mainSpecFile = getSpecsDir().resolve(domain).resolve("spec.md");
                                    String mainContent = Files.exists(mainSpecFile)
                                        ? Files.readString(mainSpecFile, StandardCharsets.UTF_8)
                                        : "# " + domain + "\n\n## Purpose\n\nTBD\n\n## Requirements\n";

                                    MergeResult result = deltaMerger.merge(mainContent, delta);

                                    if (result.isRetired()) {
                                        Files.deleteIfExists(mainSpecFile);
                                        Path parentDir = mainSpecFile.getParent();
                                        if (Files.exists(parentDir) && isDirEmpty(parentDir)) {
                                            Files.deleteIfExists(parentDir);
                                        }
                                        warnings.add("Retired spec: " + domain);
                                    } else {
                                        Files.createDirectories(mainSpecFile.getParent());
                                        Files.writeString(mainSpecFile, result.getRebuilt(), StandardCharsets.UTF_8);
                                    }

                                    updatedSpecs.add(domain + " (" + result.getCounts().getTotal() + " operations)");
                                    warnings.addAll(result.getWarnings());
                                }
                            }
                        }
                    }
                }
            }
        }

        // Move to archive
        String archiveName = generateArchiveName(changeName);
        Path archiveDir = getArchiveDir().resolve(archiveName);
        moveDirectory(changeDir, archiveDir);

        return new ArchiveResult(changeName, archiveName, updatedSpecs, warnings);
    }

    private String generateArchiveName(String changeName) {
        if (changeName.matches("^\\d{4}-\\d{2}-\\d{2}-.*")) {
            return changeName;
        }
        return LocalDate.now().format(DATE_PREFIX) + "-" + changeName;
    }

    private void moveDirectory(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private boolean isDirEmpty(Path dir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            return !stream.iterator().hasNext();
        }
    }

    // --- Data classes ---

    public static class SpecInfo {
        private final String name;
        private final String title;
        private final String purpose;
        private final int requirementCount;
        private final Path path;

        public SpecInfo(String name, String title, String purpose, int requirementCount, Path path) {
            this.name = name;
            this.title = title;
            this.purpose = purpose;
            this.requirementCount = requirementCount;
            this.path = path;
        }

        public String getName() { return name; }
        public String getTitle() { return title; }
        public String getPurpose() { return purpose; }
        public int getRequirementCount() { return requirementCount; }
        public Path getPath() { return path; }
    }

    public static class ChangeInfo {
        private final String name;
        private final String proposal;
        private final int totalTasks;
        private final int completedTasks;
        private final int deltaSpecCount;
        private final Path path;

        public ChangeInfo(String name, String proposal, int totalTasks, int completedTasks,
                          int deltaSpecCount, Path path) {
            this.name = name;
            this.proposal = proposal;
            this.totalTasks = totalTasks;
            this.completedTasks = completedTasks;
            this.deltaSpecCount = deltaSpecCount;
            this.path = path;
        }

        public String getName() { return name; }
        public String getProposal() { return proposal; }
        public int getTotalTasks() { return totalTasks; }
        public int getCompletedTasks() { return completedTasks; }
        public int getDeltaSpecCount() { return deltaSpecCount; }
        public Path getPath() { return path; }
        public boolean isComplete() { return totalTasks > 0 && completedTasks == totalTasks; }
    }

    public static class ArchiveResult {
        private final String changeName;
        private final String archiveName;
        private final List<String> updatedSpecs;
        private final List<String> warnings;

        public ArchiveResult(String changeName, String archiveName,
                             List<String> updatedSpecs, List<String> warnings) {
            this.changeName = changeName;
            this.archiveName = archiveName;
            this.updatedSpecs = updatedSpecs;
            this.warnings = warnings;
        }

        public String getChangeName() { return changeName; }
        public String getArchiveName() { return archiveName; }
        public List<String> getUpdatedSpecs() { return updatedSpecs; }
        public List<String> getWarnings() { return warnings; }
    }
}
