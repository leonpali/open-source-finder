package com.opensourcefinder.github;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** An issue as returned by the GitHub REST API with the HTML media type (only the fields we use). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubIssue(
		int number,
		String title,
		@JsonProperty("body_html") String bodyHtml,
		@JsonProperty("html_url") String htmlUrl,
		List<Label> labels,
		int comments,
		User user,
		List<User> assignees,
		@JsonProperty("pull_request") Map<String, Object> pullRequest) {

	/** The issues endpoint also returns pull requests; they have this field set. */
	public boolean isPullRequest() {
		return pullRequest != null;
	}

	public boolean isAssigned() {
		return assignees != null && !assignees.isEmpty();
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Label(String name) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record User(String login) {
	}
}
