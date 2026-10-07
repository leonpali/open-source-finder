package com.opensourcefinder.service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.opensourcefinder.model.Repository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Fetches a repository's README as rendered HTML from the GitHub API and sanitizes it for embedding in the page.
 */
@Service
public class ReadmeService {

	private static final Logger log = LoggerFactory.getLogger(ReadmeService.class);

	private static final Safelist SAFELIST = Safelist.relaxed()
			.addAttributes("a", "target", "rel")
			.preserveRelativeLinks(true);

	private final RestClient github;

	// Successful lookups only, so a failed request (e.g. rate limit) is retried next time.
	private final Map<String, String> cache = new ConcurrentHashMap<>();

	public ReadmeService(@Value("${github.token:}") String token) {
		this.github = RestClient.builder()
				.baseUrl("https://api.github.com")
				.defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github.html+json")
				.defaultHeaders(headers -> {
					if (!token.isBlank()) {
						headers.setBearerAuth(token);
					}
				})
				.build();
	}

	public Optional<String> findReadmeHtml(Repository repo) {
		String key = repo.fullName().toLowerCase();
		String cached = cache.get(key);
		if (cached != null) {
			return Optional.of(cached);
		}

		try {
			String raw = github.get()
					.uri("/repos/{owner}/{name}/readme", repo.owner(), repo.name())
					.retrieve()
					.body(String.class);
			if (raw == null) {
				return Optional.empty();
			}
			String html = sanitize(raw, repo);
			cache.put(key, html);
			return Optional.of(html);
		}
		catch (RestClientException ex) {
			log.warn("Could not load README for {}: {}", repo.fullName(), ex.getMessage());
			return Optional.empty();
		}
	}

	private static String sanitize(String raw, Repository repo) {
		// The rendered README uses repo-relative paths for images and links.
		String blobBase = repo.url() + "/blob/HEAD/";
		String rawBase = "https://raw.githubusercontent.com/" + repo.fullName() + "/HEAD/";

		Document doc = Jsoup.parse(raw, blobBase);
		doc.select("img[src]").forEach(img -> {
			String src = img.attr("src");
			if (src.startsWith("/") && !src.startsWith("//")) {
				img.attr("src", "https://github.com" + src);
			}
			else if (!src.matches("(?i)^(https?:|data:|//).*")) {
				img.attr("src", rawBase + src);
			}
		});
		doc.select("a[href]").forEach(a -> {
			if (a.attr("href").startsWith("#")) {
				return;
			}
			a.attr("href", a.absUrl("href"));
			a.attr("target", "_blank");
			a.attr("rel", "noopener");
		});

		return Jsoup.clean(doc.body().html(), SAFELIST);
	}
}
