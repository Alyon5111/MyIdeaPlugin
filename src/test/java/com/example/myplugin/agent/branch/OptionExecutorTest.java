package com.example.myplugin.agent.branch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OptionExecutorTest {

    @Test
    void testInitialState() {
        OptionExecutor executor = new OptionExecutor();
        assertNull(executor.getSelectedOption());
        assertFalse(executor.isConfirmed());
    }

    @Test
    void testSelectOption() {
        OptionExecutor executor = new OptionExecutor();
        executor.selectOption(OptionExecutor.Option.MERGE_LOCALLY);

        assertEquals(OptionExecutor.Option.MERGE_LOCALLY, executor.getSelectedOption());
        assertFalse(executor.isConfirmed());
    }

    @Test
    void testConfirmDiscard() {
        OptionExecutor executor = new OptionExecutor();
        executor.selectOption(OptionExecutor.Option.DISCARD);

        String result = executor.confirmDiscard("wrong");
        assertTrue(result.contains("Type 'discard'"));
        assertFalse(executor.isConfirmed());

        result = executor.confirmDiscard("discard");
        assertTrue(result.contains("confirmed"));
        assertTrue(executor.isConfirmed());
    }

    @Test
    void testConfirmDiscardWrongOption() {
        OptionExecutor executor = new OptionExecutor();
        executor.selectOption(OptionExecutor.Option.MERGE_LOCALLY);

        String result = executor.confirmDiscard("discard");
        assertTrue(result.contains("Error"));
    }

    @Test
    void testGetOptionDescription() {
        OptionExecutor executor = new OptionExecutor();

        executor.selectOption(OptionExecutor.Option.MERGE_LOCALLY);
        assertTrue(executor.getOptionDescription().contains("Merge"));

        executor.selectOption(OptionExecutor.Option.CREATE_PR);
        assertTrue(executor.getOptionDescription().contains("Pull Request"));

        executor.selectOption(OptionExecutor.Option.KEEP_AS_IS);
        assertTrue(executor.getOptionDescription().contains("Keep"));

        executor.selectOption(OptionExecutor.Option.DISCARD);
        assertTrue(executor.getOptionDescription().contains("Discard"));
    }

    @Test
    void testGetOptionsMenu() {
        OptionExecutor executor = new OptionExecutor();

        String menu = executor.getOptionsMenu(false, false);
        assertTrue(menu.contains("1. Merge"));
        assertTrue(menu.contains("2. Push"));
        assertTrue(menu.contains("3. Keep"));
        assertTrue(menu.contains("4. Discard"));
    }

    @Test
    void testGetOptionsMenuDetachedHead() {
        OptionExecutor executor = new OptionExecutor();

        String menu = executor.getOptionsMenu(false, true);
        assertTrue(menu.contains("1. Push"));
        assertTrue(menu.contains("2. Keep"));
        assertTrue(menu.contains("3. Discard"));
        assertFalse(menu.contains("1. Merge"));
    }
}
