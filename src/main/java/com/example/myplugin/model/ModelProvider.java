package com.example.myplugin.model;

public enum ModelProvider {

    LLaMA("LLaMA.c++", Type.LOCAL);

    public enum Type {
        LOCAL
    }

    private final String name;
    private final Type type;

    ModelProvider(String name, Type type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public Type getType() {
        return type;
    }

    @Override
    public String toString() {
        return name;
    }
}
