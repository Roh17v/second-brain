package com.secondbrain.user.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.secondbrain.user.dto.UserResponse;
import com.secondbrain.user.service.UserService;

@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping("/{id}")
	public UserResponse getById(@PathVariable UUID id) {
		return userService.getById(id);
	}

	@GetMapping
	public List<UserResponse> list() {
		return userService.listAll();
	}
}
