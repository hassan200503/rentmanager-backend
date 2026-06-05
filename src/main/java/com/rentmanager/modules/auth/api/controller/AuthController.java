package com.rentmanager.modules.auth.api.controller;

import com.rentmanager.modules.auth.api.request.LoginRequest;
import com.rentmanager.modules.auth.api.request.RegisterRequest;
import com.rentmanager.modules.auth.api.response.MessageResponse;
import com.rentmanager.modules.auth.application.usecase.LoginUserUseCase;
import com.rentmanager.modules.auth.application.usecase.RegisterUserUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegisterUserUseCase registerUserUseCase;
    private final LoginUserUseCase loginUserUseCase;

    public AuthController(
            RegisterUserUseCase registerUserUseCase,
            LoginUserUseCase loginUserUseCase
    ) {
        this.registerUserUseCase = registerUserUseCase;
        this.loginUserUseCase = loginUserUseCase;
    }

    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@RequestBody RegisterRequest request) {

        registerUserUseCase.execute(
                request.email(),
                request.password()
        );

        return ResponseEntity.ok(
                new MessageResponse("User registered successfully")
        );
    }

    @PostMapping("/login")
    public ResponseEntity<LoginUserUseCase.LoginResponse> login(@RequestBody LoginRequest request) {

        LoginUserUseCase.LoginResponse response = loginUserUseCase.execute(
                request.email(),
                request.password()
        );

        return ResponseEntity.ok(response);
    }
}