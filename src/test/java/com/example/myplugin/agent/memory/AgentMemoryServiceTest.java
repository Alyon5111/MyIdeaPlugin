package com.example.myplugin.agent.memory;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AgentMemoryServiceTest {

    private static class InMemoryPersistence implements AgentMemoryService.Persistence {
        private final List<MemoryEntry> backing = new ArrayList<>();

        @Override
        public List<MemoryEntry> load() {
            return new ArrayList<>(backing);
        }

        @Override
        public void save(List<MemoryEntry> entries) {
            backing.clear();
            backing.addAll(entries);
        }
    }

    private AgentMemoryService newService() {
        return new AgentMemoryService(new InMemoryPersistence());
    }

    @Test
    void testAddAndGetEntry() {
        AgentMemoryService service = newService();
        MemoryEntry entry = service.addEntry(MemoryCategory.FACT, "Project uses Java 21", "test", 0.9);

        assertNotNull(entry);
        assertNotNull(entry.getId());
        assertEquals(MemoryCategory.FACT, entry.getCategory());
        assertEquals("Project uses Java 21", entry.getContent());
        assertEquals(1, service.size());

        MemoryEntry fetched = service.getEntry(entry.getId());
        assertNotNull(fetched);
        assertEquals(entry.getId(), fetched.getId());
        assertEquals(1, fetched.getAccessCount());
    }

    @Test
    void testDeduplicateByContent() {
        AgentMemoryService service = newService();
        service.addEntry(MemoryCategory.FACT, "Project uses Java 21", "test", 0.9);
        MemoryEntry dup = service.addEntry(MemoryCategory.FACT, "project uses java 21", "test", 0.5);

        assertEquals(1, service.size());
        assertEquals(0.9, dup.getConfidence());
    }

    @Test
    void testRemoveEntry() {
        AgentMemoryService service = newService();
        MemoryEntry entry = service.addEntry(MemoryCategory.FACT, "content", "test", 0.8);

        assertTrue(service.removeEntry(entry.getId()));
        assertEquals(0, service.size());
        assertFalse(service.removeEntry("nonexistent"));
    }

    @Test
    void testSearchByContent() {
        AgentMemoryService service = newService();
        service.addEntry(MemoryCategory.TECH_STACK, "Spring Boot 3", "test", 0.9);
        service.addEntry(MemoryCategory.FACT, "Uses PostgreSQL", "test", 0.8);

        List<MemoryEntry> results = service.search("spring");
        assertEquals(1, results.size());
        assertTrue(results.get(0).getContent().contains("Spring"));
    }

    @Test
    void testGetByCategory() {
        AgentMemoryService service = newService();
        service.addEntry(MemoryCategory.CONVENTION, "Use camelCase", "test", 0.9);
        service.addEntry(MemoryCategory.FACT, "Use Java", "test", 0.8);

        assertEquals(1, service.getByCategory(MemoryCategory.CONVENTION).size());
        assertEquals(1, service.getByCategory(MemoryCategory.FACT).size());
        assertEquals(0, service.getByCategory(MemoryCategory.DECISION).size());
    }

    @Test
    void testGetHighConfidence() {
        AgentMemoryService service = newService();
        service.addEntry(MemoryCategory.FACT, "Low confidence fact", "test", 0.3);
        service.addEntry(MemoryCategory.FACT, "High confidence fact", "test", 0.8);
        service.addEntry(MemoryCategory.FACT, "Another high fact", "test", 0.9);

        List<MemoryEntry> top = service.getHighConfidence(2);
        assertEquals(2, top.size());
        assertEquals("Another high fact", top.get(0).getContent());
    }

    @Test
    void testFormatForSystemInjection() {
        AgentMemoryService service = newService();
        String empty = service.formatForSystemInjection(5);
        assertEquals("", empty);

        service.addEntry(MemoryCategory.TECH_STACK, "Java 21", "test", 0.95);
        service.addEntry(MemoryCategory.CONVENTION, "camelCase", "test", 0.9);

        String inj = service.formatForSystemInjection(5);
        assertTrue(inj.contains("Java 21"));
        assertTrue(inj.contains("PROJECT MEMORY"));
        assertTrue(inj.contains("camelCase"));
    }

    @Test
    void testPrune() {
        AgentMemoryService service = newService();
        for (int i = 0; i < 10; i++) {
            service.addEntry(MemoryCategory.FACT, "Memory entry " + i, "test", 0.5);
        }
        assertEquals(10, service.size());

        service.prune(5);
        assertEquals(5, service.size());
    }

    @Test
    void testPersistenceSaveCalled() {
        AtomicReference<List<MemoryEntry>> saved = new AtomicReference<>(new ArrayList<>());
        AgentMemoryService service = new AgentMemoryService(new AgentMemoryService.Persistence() {
            @Override
            public List<MemoryEntry> load() {
                return new ArrayList<>(saved.get());
            }

            @Override
            public void save(List<MemoryEntry> entries) {
                saved.set(new ArrayList<>(entries));
            }
        });

        service.addEntry(MemoryCategory.FACT, "persisted", "test", 0.8);
        assertEquals(1, saved.get().size());
    }

    @Test
    void testSummary() {
        AgentMemoryService service = newService();
        service.addEntry(MemoryCategory.FACT, "a", "test", 0.8);

        String summary = service.getSummary();
        assertTrue(summary.contains("Total entries: 1"));
    }
}
