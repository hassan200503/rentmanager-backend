package com.rentmanager.ai.api.dto;

public record AiTaskResponse(
        String result
) {

    public static AiTaskResponse from(String result) {
        return new AiTaskResponse(result);
    }
}