package com.example.myplugin.agent.git;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorktreeManagerTest {

    @Test
    void testInitialState() {
        WorktreeManager manager = new WorktreeManager();
        assertEquals(WorktreeManager.WorktreeStatus.NORMAL_REPO, manager.getStatus());
        assertTrue(manager.getHistory().isEmpty());
    }

    @Test
    void testDetectNormalRepo() {
        WorktreeManager manager = new WorktreeManager();
        manager.detectEnvironment("/repo/.git", "/repo/.git", "main", "/repo");

        assertEquals(WorktreeManager.WorktreeStatus.NORMAL_REPO, manager.getStatus());
        assertFalse(manager.isInWorktree());
        assertTrue(manager.isNormalRepo());
    }

    @Test
    void testDetectWorktree() {
        WorktreeManager manager = new WorktreeManager();
        manager.detectEnvironment("/repo/.git/worktrees/feature", "/repo/.git", "feature-branch", "/repo/.worktrees/feature");

        assertEquals(WorktreeManager.WorktreeStatus.IN_WORKTREE, manager.getStatus());
        assertTrue(manager.isInWorktree());
        assertFalse(manager.isNormalRepo());
    }

    @Test
    void testDetectDetachedHead() {
        WorktreeManager manager = new WorktreeManager();
        manager.detectEnvironment("/repo/.git/worktrees/feature", "/repo/.git", "HEAD", "/repo/.worktrees/feature");

        assertEquals(WorktreeManager.WorktreeStatus.DETACHED_HEAD, manager.getStatus());
        assertTrue(manager.isInWorktree());
        assertTrue(manager.isDetachedHead());
    }

    @Test
    void testGetWorktreeInfo() {
        WorktreeManager manager = new WorktreeManager();
        manager.detectEnvironment("/repo/.git", "/repo/.git", "main", "/repo");

        String info = manager.getWorktreeInfo();
        assertTrue(info.contains("WORKTREE INFO"));
        assertTrue(info.contains("Status: NORMAL_REPO"));
    }
}
