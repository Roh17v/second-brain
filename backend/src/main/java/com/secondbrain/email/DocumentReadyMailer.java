package com.secondbrain.email;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.secondbrain.document.entity.Document;
import com.secondbrain.document.entity.DocumentStatus;
import com.secondbrain.document.repository.DocumentRepository;
import com.secondbrain.document.service.DocumentIngestProgress;
import com.secondbrain.user.entity.User;
import com.secondbrain.user.repository.UserRepository;

/**
 * One “ready” or “failed” email per long ingest. Never fails the pipeline.
 */
@Service
public class DocumentReadyMailer {

	private static final Logger log = LoggerFactory.getLogger(DocumentReadyMailer.class);

	private final DocumentRepository documentRepository;
	private final UserRepository userRepository;
	private final EmailSender emailSender;
	private final EmailProperties emailProperties;
	private final DocumentIngestProgress ingestProgress;

	public DocumentReadyMailer(
			DocumentRepository documentRepository,
			UserRepository userRepository,
			EmailSender emailSender,
			EmailProperties emailProperties,
			DocumentIngestProgress ingestProgress
	) {
		this.documentRepository = documentRepository;
		this.userRepository = userRepository;
		this.emailSender = emailSender;
		this.emailProperties = emailProperties;
		this.ingestProgress = ingestProgress;
	}

	public void notifyIfNeeded(UUID documentId) {
		if (documentId == null) {
			return;
		}
		Document document = documentRepository.findById(documentId).orElse(null);
		if (document == null
				|| !document.isNotifyOnReady()
				|| document.getReadyNotifiedAt() != null) {
			return;
		}
		DocumentStatus status = document.getStatus();
		if (status != DocumentStatus.READY && status != DocumentStatus.FAILED) {
			return;
		}
		User owner = userRepository.findById(document.getOwnerId()).orElse(null);
		if (owner == null || owner.getEmail() == null || owner.getEmail().isBlank()) {
			log.warn("Skip ingest email for {}: no owner email", documentId);
			return;
		}

		try {
			if (status == DocumentStatus.READY) {
				sendReady(owner, document);
			}
			else {
				sendFailed(owner, document);
			}
			ingestProgress.markNotified(documentId);
		}
		catch (Exception ex) {
			log.error("Ingest email failed for {}: {}", documentId, ex.getMessage());
		}
	}

	private void sendReady(User owner, Document document) {
		String name = displayName(owner);
		String filename = safeFilename(document.getOriginalFilename());
		String link = collectionUrl(document.getWorkspaceId(), "chat");
		String subject = filename + " is ready to chat";
		String text = """
				Hi %s,

				%s is ready. You can ask questions about it in SecondBrain.

				%s

				— SecondBrain
				""".formatted(name, filename, link);
		String html = """
				<div style="font-family:system-ui,sans-serif;max-width:480px;margin:0 auto;padding:24px;color:#0f172a">
				  <h2 style="margin:0 0 12px">Your document is ready</h2>
				  <p style="margin:0 0 16px">Hi %s,</p>
				  <p style="margin:0 0 16px"><strong>%s</strong> is indexed and ready to chat.</p>
				  <p style="margin:24px 0">
				    <a href="%s" style="display:inline-block;background:#1d4ed8;color:#fff;text-decoration:none;padding:10px 16px;border-radius:8px;font-weight:600">Open chat</a>
				  </p>
				  <p style="margin:16px 0 0;color:#64748b;font-size:13px">— SecondBrain</p>
				</div>
				""".formatted(escapeHtml(name), escapeHtml(filename), escapeHtml(link));
		emailSender.send(owner.getEmail(), subject, html, text);
	}

	private void sendFailed(User owner, Document document) {
		String name = displayName(owner);
		String filename = safeFilename(document.getOriginalFilename());
		String link = collectionUrl(document.getWorkspaceId(), "documents");
		String reason = document.getFailureReason() == null || document.getFailureReason().isBlank()
				? "Something went wrong while indexing."
				: document.getFailureReason();
		String subject = "We couldn’t finish " + filename;
		String text = """
				Hi %s,

				We couldn’t finish preparing %s.

				%s

				You can retry from your collection:
				%s

				— SecondBrain
				""".formatted(name, filename, reason, link);
		String html = """
				<div style="font-family:system-ui,sans-serif;max-width:480px;margin:0 auto;padding:24px;color:#0f172a">
				  <h2 style="margin:0 0 12px">We couldn’t finish this file</h2>
				  <p style="margin:0 0 16px">Hi %s,</p>
				  <p style="margin:0 0 16px">We couldn’t finish preparing <strong>%s</strong>.</p>
				  <p style="margin:0 0 16px;color:#64748b;font-size:14px">%s</p>
				  <p style="margin:24px 0">
				    <a href="%s" style="display:inline-block;background:#1d4ed8;color:#fff;text-decoration:none;padding:10px 16px;border-radius:8px;font-weight:600">Open documents</a>
				  </p>
				  <p style="margin:16px 0 0;color:#64748b;font-size:13px">— SecondBrain</p>
				</div>
				""".formatted(escapeHtml(name), escapeHtml(filename), escapeHtml(reason), escapeHtml(link));
		emailSender.send(owner.getEmail(), subject, html, text);
	}

	private String collectionUrl(UUID workspaceId, String page) {
		String base = emailProperties.getPublicBaseUrl();
		if (base == null || base.isBlank()) {
			base = "http://localhost:5173";
		}
		if (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		return base + "/collections/" + workspaceId + "/" + page;
	}

	private static String displayName(User owner) {
		String name = owner.getName();
		return name == null || name.isBlank() ? "there" : name.trim();
	}

	private static String safeFilename(String filename) {
		if (filename == null || filename.isBlank()) {
			return "Your document";
		}
		return filename.trim();
	}

	private static String escapeHtml(String s) {
		return s.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;");
	}
}
