package com.example.myplugin.openspec;

import com.example.myplugin.openspec.manager.ArtifactGraph;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ArtifactGraphTest {

    @Test
    void getBuildOrder_DefaultGraph() {
        ArtifactGraph graph = new ArtifactGraph();
        List<String> order = graph.getBuildOrder();

        assertEquals(4, order.size());
        assertTrue(order.indexOf("proposal") < order.indexOf("specs"));
        assertTrue(order.indexOf("specs") < order.indexOf("design"));
        assertTrue(order.indexOf("design") < order.indexOf("tasks"));
    }

    @Test
    void getBuildOrder_LinearDependency() {
        ArtifactGraph graph = new ArtifactGraph();
        graph.addArtifact("step1", Collections.emptyList());
        graph.addArtifact("step2", Collections.singletonList("step1"));
        graph.addArtifact("step3", Collections.singletonList("step2"));

        List<String> order = graph.getBuildOrder();

        assertTrue(order.indexOf("step1") < order.indexOf("step2"));
        assertTrue(order.indexOf("step2") < order.indexOf("step3"));
    }

    @Test
    void getBuildOrder_CustomDependencies() {
        ArtifactGraph graph = new ArtifactGraph();
        graph.addArtifact("a", Collections.emptyList());
        graph.addArtifact("b", Collections.singletonList("a"));
        graph.addArtifact("c", Collections.singletonList("a"));
        graph.addArtifact("d", Arrays.asList("b", "c"));

        List<String> order = graph.getBuildOrder();

        assertTrue(order.indexOf("a") < order.indexOf("b"));
        assertTrue(order.indexOf("a") < order.indexOf("c"));
        assertTrue(order.indexOf("b") < order.indexOf("d"));
        assertTrue(order.indexOf("c") < order.indexOf("d"));
    }

    @Test
    void getNextArtifacts_AfterPartialCompletion() {
        ArtifactGraph graph = new ArtifactGraph();

        Set<String> completed = new HashSet<>(Collections.singletonList("proposal"));
        List<String> next = graph.getNextArtifacts(completed);

        assertTrue(next.contains("specs"));
        assertFalse(next.contains("proposal"));
        assertFalse(next.contains("design"));
    }

    @Test
    void getBlocked_WithUnmetDependencies() {
        ArtifactGraph graph = new ArtifactGraph();

        Set<String> completed = new HashSet<>(Collections.singletonList("proposal"));
        List<String> blocked = graph.getBlocked(completed);

        assertTrue(blocked.contains("tasks"));
        assertFalse(blocked.contains("proposal"));
    }

    @Test
    void isValid_NoCycle() {
        ArtifactGraph graph = new ArtifactGraph();
        assertTrue(graph.isValid());
    }

    @Test
    void isValid_WithCycle() {
        ArtifactGraph graph = new ArtifactGraph();
        graph.addArtifact("x", Collections.singletonList("y"));
        graph.addArtifact("y", Collections.singletonList("x"));

        assertFalse(graph.isValid());
    }
}
