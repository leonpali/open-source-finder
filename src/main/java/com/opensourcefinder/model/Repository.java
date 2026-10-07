package com.opensourcefinder.model;

import java.util.List;

/**
 * A popular GitHub repository with open "good first issue" issues.
 *
 * @param technologies the primary language first, then technologies from the repository's topics
 */
public record Repository(
		String owner,
		String name,
		String description,
		String url,
		List<String> technologies,
		int stars) {

	public String fullName() {
		return owner + "/" + name;
	}

	public String goodFirstIssuesUrl() {
		return url + "/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22";
	}
}
