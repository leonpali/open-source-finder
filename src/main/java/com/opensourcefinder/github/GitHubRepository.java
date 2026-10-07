package com.opensourcefinder.github;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** A repository as returned by the GitHub REST API (only the fields we use). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubRepository(
		String name,
		Owner owner,
		String description,
		@JsonProperty("html_url") String htmlUrl,
		@JsonProperty("stargazers_count") int stars,
		String language,
		List<String> topics) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Owner(String login) {
	}
}
