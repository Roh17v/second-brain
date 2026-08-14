package com.secondbrain.workspace.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.secondbrain.security.UserPrincipal;
import com.secondbrain.user.entity.User;
import com.secondbrain.workspace.entity.Workspace;
import com.secondbrain.workspace.mapper.WorkspaceMapper;
import com.secondbrain.workspace.repository.WorkspaceRepository;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceDeleteTest {

	@Mock
	WorkspaceRepository workspaceRepository;
	@Mock
	WorkspaceMapper workspaceMapper;
	@Mock
	WorkspaceSearchIndexPurge searchIndexPurge;

	@AfterEach
	void clearSecurity() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void softDeleteHidesWorkspaceAndPurgesSearchIndex() throws Exception {
		UUID ownerId = UUID.randomUUID();
		UUID workspaceId = UUID.randomUUID();
		authenticate(ownerId);

		Workspace workspace = new Workspace("DSA notes", null, ownerId);
		setId(workspace, workspaceId);
		when(workspaceRepository.findByIdAndOwnerIdAndDeletedAtIsNull(workspaceId, ownerId))
				.thenReturn(Optional.of(workspace));

		WorkspaceService service = new WorkspaceService(
				workspaceRepository,
				workspaceMapper,
				searchIndexPurge
		);
		service.softDeleteMine(workspaceId);

		assertNotNull(workspace.getDeletedAt());
		verify(workspaceRepository).save(workspace);
		verify(searchIndexPurge).purge(workspaceId);
	}

	private static void authenticate(UUID ownerId) throws Exception {
		User user = new User("owner@test.com", "Owner", "hash");
		Field id = User.class.getDeclaredField("id");
		id.setAccessible(true);
		id.set(user, ownerId);
		Field verified = User.class.getDeclaredField("emailVerified");
		verified.setAccessible(true);
		verified.set(user, true);

		UserPrincipal principal = new UserPrincipal(user);
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(principal, null, List.of())
		);
	}

	private static void setId(Workspace workspace, UUID id) throws Exception {
		Field field = Workspace.class.getDeclaredField("id");
		field.setAccessible(true);
		field.set(workspace, id);
	}
}
