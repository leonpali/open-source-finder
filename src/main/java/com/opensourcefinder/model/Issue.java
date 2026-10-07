package com.opensourcefinder.model;

import java.util.List;

/**
 * @param bodyHtml the issue description, rendered by GitHub and sanitized
 */
public record Issue(int number, String title, String bodyHtml, String url, List<String> labels, int comments,
		String author) {
}
