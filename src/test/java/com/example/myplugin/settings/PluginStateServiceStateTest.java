package com.example.myplugin.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PluginStateServiceStateTest {

    @Test
    void agentEnabledDefaultsTrue() {
        PluginStateService.State state = new PluginStateService.State();
        assertTrue(state.agentEnabled, "agentEnabled should default to true");
    }

    @Test
    void agentEnabledRoundTripsThroughJson() {
        PluginStateService.State state = new PluginStateService.State();
        state.agentEnabled = false;
        Gson gson = new GsonBuilder().create();
        String json = gson.toJson(state);
        assertTrue(json.contains("\"agentEnabled\":false"), json);
        PluginStateService.State loaded = gson.fromJson(json, PluginStateService.State.class);
        assertFalse(loaded.agentEnabled);
    }
}