package com.opensourcefinder.model;

import java.util.List;

/**
 * A GitHub repository with an open issue that is a good entry point for contributors.
 */
public record Repository(
		String owner,
		String name,
		String description,
		String url,
		List<String> technologies,
		int stars,
		Issue issue) {

	public String fullName() {
		return owner + "/" + name;
	}
}
