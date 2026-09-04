package com.example.myplugin.agent.git;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BranchManagerTest {

    @Test
    void testInitialState() {
        BranchManager manager = new BranchManager();
        assertNull(manager.getCurrentBranch());
        assertNull(manager.getBaseBranch());
        assertTrue(manager.getBranches().isEmpty());
    }

    @Test
    void testCreateBranch() {
        BranchManager manager = new BranchManager();
        String result = manager.createBranch("feature-1");

        assertEquals("Branch 'feature-1' created", result);
        assertEquals(1, manager.getBranches().size());
    }

    @Test
    void testCreateDuplicateBranch() {
        BranchManager manager = new BranchManager();
        manager.createBranch("feature-1");
        String result = manager.createBranch("feature-1");

        assertTrue(result.contains("already exists"));
    }

    @Test
    void testSwitchBranch() {
        BranchManager manager = new BranchManager();
        manager.createBranch("feature-1");
        manager.createBranch("feature-2");

        String result = manager.switchBranch("feature-2");
        assertEquals("Switched to branch 'feature-2'", result);
        assertEquals("feature-2", manager.getCurrentBranch());
    }

    @Test
    void testSwitchNonexistentBranch() {
        BranchManager manager = new BranchManager();
        String result = manager.switchBranch("feature-1");

        assertTrue(result.contains("does not exist"));
    }

    @Test
    void testDeleteBranch() {
        BranchManager manager = new BranchManager();
        manager.createBranch("feature-1");
        manager.createBranch("feature-2");
        manager.setCurrentBranch("feature-1");

        String result = manager.deleteBranch("feature-2", false);
        assertEquals("Branch 'feature-2' deleted", result);
        assertEquals(1, manager.getBranches().size());
    }

    @Test
    void testDeleteCurrentBranch() {
        BranchManager manager = new BranchManager();
        manager.createBranch("feature-1");
        manager.setCurrentBranch("feature-1");

        String result = manager.deleteBranch("feature-1", false);
        assertTrue(result.contains("Cannot delete current branch"));
    }

    @Test
    void testMergeBranch() {
        BranchManager manager = new BranchManager();
        manager.createBranch("feature-1");
        manager.createBranch("main");

        String result = manager.mergeBranch("feature-1", "main");
        assertTrue(result.contains("Merged"));
    }

    @Test
    void testGetBranchList() {
        BranchManager manager = new BranchManager();
        manager.createBranch("feature-1");
        manager.setCurrentBranch("feature-1");

        String list = manager.getBranchList();
        assertTrue(list.contains("BRANCHES"));
        assertTrue(list.contains("feature-1 *"));
    }
}
