package com.secondbrain.user.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.secondbrain.common.exception.ResourceNotFoundException;
import com.secondbrain.user.dto.UserResponse;
import com.secondbrain.user.entity.User;
import com.secondbrain.user.mapper.UserMapper;
import com.secondbrain.user.repository.UserRepository;

@Service
public class UserService {

	private final UserRepository userRepository;
	private final UserMapper userMapper;

	public UserService(UserRepository userRepository, UserMapper userMapper) {
		this.userRepository = userRepository;
		this.userMapper = userMapper;
	}

	@Transactional(readOnly = true)
	public UserResponse getById(UUID id) {
		User user = userRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
		return userMapper.toResponse(user);
	}

	@Transactional(readOnly = true)
	public List<UserResponse> listAll() {
		return userRepository.findAll().stream()
				.map(userMapper::toResponse)
				.toList();
	}
}
