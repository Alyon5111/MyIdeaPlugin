package com.example.myplugin.openspec.model;

public class ScenarioBlock {
    private final String name;
    private final String raw;

    public ScenarioBlock(String name, String raw) {
        this.name = name;
        this.raw = raw;
    }

    public String getName() { return name; }
    public String getRaw() { return raw; }

    @Override
    public String toString() {
        return "ScenarioBlock{name='" + name + "'}";
    }
}
