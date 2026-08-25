package com.example.myplugin.openspec.model;

import java.util.ArrayList;
import java.util.List;

public class RequirementBlock {
    private final String headerLine;
    private final String name;
    private final String raw;

    public RequirementBlock(String headerLine, String name, String raw) {
        this.headerLine = headerLine;
        this.name = name;
        this.raw = raw;
    }

    public String getHeaderLine() { return headerLine; }
    public String getName() { return name; }
    public String getRaw() { return raw; }

    @Override
    public String toString() {
        return "RequirementBlock{name='" + name + "'}";
    }
}
