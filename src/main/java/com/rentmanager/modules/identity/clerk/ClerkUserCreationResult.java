package com.rentmanager.modules.identity.clerk;

public record ClerkUserCreationResult(String clerkUserId, boolean newlyCreated) {}