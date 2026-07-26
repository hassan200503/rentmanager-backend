package com.rentmanager.modules.identity.clerk;

public record SignInTokenResult(
        String tokenId,
        String token,
        String url
) {}
