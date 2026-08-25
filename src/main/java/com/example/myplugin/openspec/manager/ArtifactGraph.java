package com.example.myplugin.openspec.manager;

import java.util.*;

/**
 * Dependency graph for OpenSpec artifacts using topological sort (Kahn's algorithm).
 * Determines the order in which artifacts should be generated.
 */
public class ArtifactGraph {

    private final Map<String, List<String>> requires = new LinkedHashMap<>();
    private final Map<String, List<String>> dependedOnBy = new LinkedHashMap<>();

    public ArtifactGraph() {
        // Default artifact dependencies (OpenSpec standard)
        addArtifact("proposal", Collections.emptyList());
        addArtifact("specs", Collections.singletonList("proposal"));
        addArtifact("design", Arrays.asList("proposal", "specs"));
        addArtifact("tasks", Arrays.asList("proposal", "specs", "design"));
    }

    public void addArtifact(String name, List<String> dependencies) {
        requires.put(name, new ArrayList<>(dependencies));
        dependedOnBy.putIfAbsent(name, new ArrayList<>());
        for (String dep : dependencies) {
            dependedOnBy.computeIfAbsent(dep, k -> new ArrayList<>()).add(name);
        }
    }

    /**
     * Get the full build order using topological sort.
     */
    public List<String> getBuildOrder() {
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        for (String artifact : requires.keySet()) {
            inDegree.put(artifact, requires.get(artifact).size());
        }

        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<String> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            order.add(current);
            for (String dependent : dependedOnBy.getOrDefault(current, Collections.emptyList())) {
                int newDegree = inDegree.get(dependent) - 1;
                inDegree.put(dependent, newDegree);
                if (newDegree == 0) {
                    queue.add(dependent);
                }
            }
        }
        return order;
    }

    /**
     * Get artifacts that are ready to be built (all dependencies completed).
     */
    public List<String> getNextArtifacts(Set<String> completed) {
        List<String> next = new ArrayList<>();
        for (String artifact : requires.keySet()) {
            if (completed.contains(artifact)) continue;
            if (completed.containsAll(requires.get(artifact))) {
                next.add(artifact);
            }
        }
        return next;
    }

    /**
     * Get artifacts that are blocked (have unmet dependencies).
     */
    public List<String> getBlocked(Set<String> completed) {
        List<String> blocked = new ArrayList<>();
        for (String artifact : requires.keySet()) {
            if (completed.contains(artifact)) continue;
            if (!completed.containsAll(requires.get(artifact))) {
                blocked.add(artifact);
            }
        }
        return blocked;
    }

    public boolean isValid() {
        return getBuildOrder().size() == requires.size();
    }
}
