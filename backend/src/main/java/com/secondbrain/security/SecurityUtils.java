package com.secondbrain.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.secondbrain.common.exception.UnauthorizedException;

public final class SecurityUtils {

	private SecurityUtils() {
	}

	public static UserPrincipal requireCurrentUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
			throw new UnauthorizedException("Authentication required");
		}
		return principal;
	}
}
