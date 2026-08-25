package com.example.myplugin.openspec.model;

public class RenamePair {
    private final String from;
    private final String to;

    public RenamePair(String from, String to) {
        this.from = from;
        this.to = to;
    }

    public String getFrom() { return from; }
    public String getTo() { return to; }

    @Override
    public String toString() {
        return "RenamePair{from='" + from + "', to='" + to + "'}";
    }
}
