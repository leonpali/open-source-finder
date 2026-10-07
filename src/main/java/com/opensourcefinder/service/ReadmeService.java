package com.opensourcefinder.service;

import java.time.Duration;
import java.util.Optional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.opensourcefinder.github.GitHubClient;
import com.opensourcefinder.github.GitHubException;
import com.opensourcefinder.model.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

/**
 * A repository's README as sanitized HTML.
 */
@Service
public class ReadmeService {

	private static final Logger log = LoggerFactory.getLogger(ReadmeService.class);

	private final GitHubClient github;

	// Failed lookups throw and are not cached, so they are retried next time.
	private final Cache<String, Optional<String>> cache = Caffeine.newBuilder()
			.expireAfterWrite(Duration.ofHours(1))
			.maximumSize(500)
			.build();

	public ReadmeService(GitHubClient github) {
		this.github = github;
	}

	/** Empty when the repository has no README or GitHub could not be reached. */
	public Optional<String> findReadmeHtml(Repository repo) {
		try {
			return cache.get(repo.fullName().toLowerCase(), key -> github.readmeHtml(repo.owner(), repo.name())
					.map(html -> HtmlSanitizer.sanitize(html, repo)));
		}
		catch (GitHubException ex) {
			log.warn("Could not load README for {}: {}", repo.fullName(), ex.getMessage());
			return Optional.empty();
		}
	}
}
