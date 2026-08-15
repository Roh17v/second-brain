package com.secondbrain.email;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.secondbrain.document.entity.Document;
import com.secondbrain.document.entity.DocumentStatus;
import com.secondbrain.document.repository.DocumentRepository;
import com.secondbrain.document.service.DocumentIngestProgress;
import com.secondbrain.user.entity.User;
import com.secondbrain.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class DocumentReadyMailerTest {

	@Mock
	DocumentRepository documentRepository;
	@Mock
	UserRepository userRepository;
	@Mock
	EmailSender emailSender;
	@Mock
	DocumentIngestProgress ingestProgress;

	EmailProperties props = new EmailProperties();
	DocumentReadyMailer mailer;

	@BeforeEach
	void setUp() {
		props.setPublicBaseUrl("https://secondbrain.example");
		mailer = new DocumentReadyMailer(
				documentRepository,
				userRepository,
				emailSender,
				props,
				ingestProgress
		);
	}

	@Test
	void skipsWhenNotPromised() throws Exception {
		Document doc = document(DocumentStatus.READY, false);
		when(documentRepository.findById(doc.getId())).thenReturn(Optional.of(doc));

		mailer.notifyIfNeeded(doc.getId());

		verify(emailSender, never()).send(anyString(), anyString(), anyString(), anyString());
		verify(ingestProgress, never()).markNotified(any());
	}

	@Test
	void sendsReadyOnceWhenPromised() throws Exception {
		Document doc = document(DocumentStatus.READY, true);
		User owner = owner(doc.getOwnerId());
		when(documentRepository.findById(doc.getId())).thenReturn(Optional.of(doc));
		when(userRepository.findById(doc.getOwnerId())).thenReturn(Optional.of(owner));
		when(ingestProgress.markNotified(doc.getId())).thenReturn(true);

		mailer.notifyIfNeeded(doc.getId());

		verify(emailSender).send(
				eq("owner@test.com"),
				contains("notes.pdf is ready to chat"),
				contains("/collections/" + doc.getWorkspaceId() + "/chat"),
				contains("https://secondbrain.example/collections/")
		);
		verify(ingestProgress).markNotified(doc.getId());
	}

	@Test
	void sendsFailureWhenPromised() throws Exception {
		Document doc = document(DocumentStatus.FAILED, true);
		doc.setFailureReason("Ollama timeout");
		User owner = owner(doc.getOwnerId());
		when(documentRepository.findById(doc.getId())).thenReturn(Optional.of(doc));
		when(userRepository.findById(doc.getOwnerId())).thenReturn(Optional.of(owner));
		when(ingestProgress.markNotified(doc.getId())).thenReturn(true);

		mailer.notifyIfNeeded(doc.getId());

		verify(emailSender).send(
				eq("owner@test.com"),
				contains("We couldn’t finish notes.pdf"),
				contains("Ollama timeout"),
				contains("Ollama timeout")
		);
	}

	private static Document document(DocumentStatus status, boolean notify) throws Exception {
		UUID workspaceId = UUID.randomUUID();
		UUID ownerId = UUID.randomUUID();
		Document doc = new Document(
				workspaceId,
				ownerId,
				"notes.pdf",
				"notes.pdf",
				"application/pdf",
				1000,
				"path",
				status
		);
		Field id = Document.class.getDeclaredField("id");
		id.setAccessible(true);
		id.set(doc, UUID.randomUUID());
		doc.setNotifyOnReady(notify);
		return doc;
	}

	private static User owner(UUID ownerId) throws Exception {
		User user = new User("owner@test.com", "Owner", "hash");
		Field id = User.class.getDeclaredField("id");
		id.setAccessible(true);
		id.set(user, ownerId);
		return user;
	}
}
