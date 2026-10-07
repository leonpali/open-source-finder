package com.opensourcefinder.web;

import java.util.ArrayList;
import java.util.List;

import com.opensourcefinder.model.Repository;

import org.springframework.web.util.UriComponentsBuilder;

/**
 * The selected technologies, plus the URLs the page links to for changing them. The URL is the source of truth for
 * the filter, so every view can be bookmarked and the back button works.
 */
public record FilterState(List<String> selected) {

	public boolean isEmpty() {
		return selected.isEmpty();
	}

	public boolean isSelected(String tech) {
		return selected.stream().anyMatch(tech::equalsIgnoreCase);
	}

	/** The page URL for the current filter. */
	public String url() {
		return url(selected, null);
	}

	/** The page URL with {@code tech} added, or removed if it is already selected. */
	public String toggleUrl(String tech) {
		var next = new ArrayList<>(selected);
		if (!next.removeIf(tech::equalsIgnoreCase)) {
			next.add(tech);
		}
		return url(next, null);
	}

	public String clearUrl() {
		return url(List.of(), null);
	}

	/** The page URL with the detail view of {@code repo} open. */
	public String repoUrl(Repository repo) {
		return url(selected, repo.fullName());
	}

	private static String url(List<String> technologies, String repo) {
		var builder = UriComponentsBuilder.fromPath("/");
		if (!technologies.isEmpty()) {
			builder.queryParam("tech", technologies);
		}
		if (repo != null) {
			builder.queryParam("repo", repo);
		}
		return builder.encode().build().toUriString();
	}
}
