package com.opensourcefinder.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.opensourcefinder.github.GitHubClient;
import com.opensourcefinder.github.GitHubException;
import com.opensourcefinder.github.GitHubIssue;
import com.opensourcefinder.github.GitHubRepository;
import com.opensourcefinder.model.Issue;
import com.opensourcefinder.model.Repository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Popular repositories with open "good first issue" issues, from the GitHub API. Results are cached to stay within
 * GitHub's rate limits; failed requests throw {@link GitHubException} and are retried on the next call.
 */
@Service
public class RepositoryService {

	static final String GOOD_FIRST_ISSUE = "good first issue";

	static final int MAX_TECHNOLOGIES = 5;

	// Topics that say nothing about the technology used.
	private static final Pattern NOISE_TOPICS = Pattern.compile(
			"hacktoberfest.*|good-?first-?issues?|first-?timers?(-only)?|beginner-?friendly|up-for-grabs|help-?wanted"
					+ "|open-?source|awesome(-list)?|hacktoberfest\\d+");

	private final GitHubClient github;
	private final String baseQuery;
	private final int pageSize;

	private final Cache<String, List<Repository>> searches = Caffeine.newBuilder()
			.expireAfterWrite(Duration.ofMinutes(15))
			.maximumSize(200)
			.build();

	private final Cache<String, Optional<Repository>> repositories = Caffeine.newBuilder()
			.expireAfterWrite(Duration.ofHours(1))
			.maximumSize(2000)
			.build();

	private final Cache<String, Optional<Issue>> issues = Caffeine.newBuilder()
			.expireAfterWrite(Duration.ofMinutes(15))
			.maximumSize(500)
			.build();

	public RepositoryService(GitHubClient github, @Value("${github.min-stars:1000}") int minStars,
			@Value("${github.page-size:30}") int pageSize) {
		this.github = github;
		this.baseQuery = "good-first-issues:>0 archived:false stars:>=" + minStars;
		this.pageSize = pageSize;
	}

	/**
	 * The most-starred repositories with open good first issues that use at least one of the given technologies, or
	 * regardless of technology when none are given.
	 */
	public List<Repository> findPopular(List<String> technologies) {
		if (technologies.isEmpty()) {
			return search(baseQuery);
		}
		var byName = new LinkedHashMap<String, Repository>();
		for (String tech : technologies) {
			searchByTechnology(tech).forEach(repo -> byName.putIfAbsent(key(repo.owner(), repo.name()), repo));
		}
		return byName.values().stream()
				.sorted(Comparator.comparingInt(Repository::stars).reversed())
				.toList();
	}

	public Optional<Repository> find(String owner, String name) {
		return repositories.get(key(owner, name), k -> github.getRepository(owner, name).map(RepositoryService::toRepository));
	}

	/** The newest open good first issue, preferring ones nobody is assigned to. */
	public Optional<Issue> findGoodFirstIssue(Repository repo) {
		return issues.get(key(repo.owner(), repo.name()), k -> {
			List<GitHubIssue> candidates = github.openIssuesWithLabel(repo.owner(), repo.name(), GOOD_FIRST_ISSUE, 20)
					.stream()
					.filter(issue -> !issue.isPullRequest())
					.toList();
			return candidates.stream()
					.filter(issue -> !issue.isAssigned())
					.findFirst()
					.or(() -> candidates.stream().findFirst())
					.map(issue -> toIssue(issue, repo));
		});
	}

	private List<Repository> searchByTechnology(String tech) {
		var known = TechnologyCatalog.lookup(tech);
		if (known.isPresent()) {
			return search(baseQuery + " " + known.get().qualifier());
		}
		// Unknown technology: try it as a language, then as a topic.
		String cleaned = tech.replace("\"", "").strip();
		List<Repository> byLanguage = search(baseQuery + " language:\"" + cleaned + "\"");
		if (!byLanguage.isEmpty()) {
			return byLanguage;
		}
		return search(baseQuery + " topic:" + cleaned.toLowerCase(Locale.ROOT).replaceAll("\\s+", "-"));
	}

	private List<Repository> search(String query) {
		return searches.get(query, q -> {
			List<Repository> found = github.searchRepositories(q, pageSize).stream()
					.map(RepositoryService::toRepository)
					.toList();
			// Lets the detail view open without another request.
			found.forEach(repo -> repositories.put(key(repo.owner(), repo.name()), Optional.of(repo)));
			return found;
		});
	}

	static Repository toRepository(GitHubRepository repo) {
		return new Repository(repo.owner().login(), repo.name(), repo.description(), repo.htmlUrl(),
				technologies(repo), repo.stars());
	}

	/** Primary language, then topics from the catalog, then other topics; capped at {@link #MAX_TECHNOLOGIES}. */
	static List<String> technologies(GitHubRepository repo) {
		var known = new ArrayList<String>();
		var other = new ArrayList<String>();
		if (repo.language() != null) {
			known.add(TechnologyCatalog.displayName(repo.language()));
		}
		for (String topic : repo.topics() == null ? List.<String>of() : repo.topics()) {
			if (NOISE_TOPICS.matcher(topic).matches()) {
				continue;
			}
			TechnologyCatalog.lookup(topic).ifPresentOrElse(tech -> known.add(tech.name()), () -> other.add(topic));
		}
		known.addAll(other);
		return TechnologyCatalog.normalize(known).stream().limit(MAX_TECHNOLOGIES).toList();
	}

	private static Issue toIssue(GitHubIssue issue, Repository repo) {
		String body = issue.bodyHtml() == null ? "" : HtmlSanitizer.sanitize(issue.bodyHtml(), repo);
		List<String> labels = issue.labels() == null ? List.of()
				: issue.labels().stream().map(GitHubIssue.Label::name).toList();
		String author = issue.user() == null ? null : issue.user().login();
		return new Issue(issue.number(), issue.title(), body, issue.htmlUrl(), labels, issue.comments(), author);
	}

	private static String key(String owner, String name) {
		return (owner + "/" + name).toLowerCase(Locale.ROOT);
	}
}
