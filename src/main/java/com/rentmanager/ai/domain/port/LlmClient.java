package com.rentmanager.ai.domain.port;

public interface LlmClient {

    String generate(String prompt);
}