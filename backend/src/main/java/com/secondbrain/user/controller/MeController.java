package com.secondbrain.user.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.secondbrain.user.dto.LibraryStatsResponse;
import com.secondbrain.user.service.LibraryStatsService;

@RestController
@RequestMapping("/api/me")
public class MeController {

	private final LibraryStatsService libraryStatsService;

	public MeController(LibraryStatsService libraryStatsService) {
		this.libraryStatsService = libraryStatsService;
	}

	@GetMapping("/stats")
	public LibraryStatsResponse stats() {
		return libraryStatsService.mine();
	}
}
