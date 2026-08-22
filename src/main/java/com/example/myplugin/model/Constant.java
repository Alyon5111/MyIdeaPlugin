package com.example.myplugin.model;

public class Constant {

    private Constant() {
    }

    public static final String LLAMA_CPP_MODEL_URL = "http://localhost:8080";

    public static final Double TEMPERATURE = 0.7d;
    public static final Double TOP_P = 0.9d;
    public static final Integer MAX_OUTPUT_TOKENS = 4000;
    public static final Integer MAX_RETRIES = 1;
    public static final Integer TIMEOUT = 120;
}
