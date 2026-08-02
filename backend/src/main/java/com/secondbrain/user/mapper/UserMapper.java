package com.secondbrain.user.mapper;

import org.springframework.stereotype.Component;

import com.secondbrain.user.dto.UserResponse;
import com.secondbrain.user.entity.User;

@Component
public class UserMapper {

	public UserResponse toResponse(User user) {
		return new UserResponse(
				user.getId(),
				user.getEmail(),
				user.getName(),
				user.getCreatedAt(),
				user.getUpdatedAt()
		);
	}
}
