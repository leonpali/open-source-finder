package com.opensourcefinder.github;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Thin wrapper around the GitHub REST API. Errors are translated to {@link GitHubException}; a 404 on a single
 * resource becomes an empty {@link Optional}.
 */
@Component
public class GitHubClient {

	static final String JSON = "application/vnd.github+json";
	static final String HTML = "application/vnd.github.html+json";

	private static final ParameterizedTypeReference<List<GitHubIssue>> ISSUE_LIST = new ParameterizedTypeReference<>() {
	};

	private final RestClient rest;

	@Autowired
	public GitHubClient(@Value("${github.api-url:https://api.github.com}") String apiUrl,
			@Value("${github.token:}") String token) {
		this(RestClient.builder().requestFactory(requestFactory()), apiUrl, token);
	}

	GitHubClient(RestClient.Builder builder, String apiUrl, String token) {
		this.rest = builder
				.baseUrl(apiUrl)
				.defaultHeader(HttpHeaders.ACCEPT, JSON)
				.defaultHeader("X-GitHub-Api-Version", "2022-11-28")
				.defaultHeaders(headers -> {
					if (!token.isBlank()) {
						headers.setBearerAuth(token);
					}
				})
				.build();
	}

	private static JdkClientHttpRequestFactory requestFactory() {
		// Renamed or transferred repositories answer with a redirect.
		var http = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NORMAL)
				.connectTimeout(Duration.ofSeconds(5))
				.build();
		var factory = new JdkClientHttpRequestFactory(http);
		factory.setReadTimeout(Duration.ofSeconds(10));
		return factory;
	}

	/**
	 * Repository search, most stars first.
	 *
	 * @param query GitHub search syntax, e.g. {@code good-first-issues:>0 language:java}
	 */
	public List<GitHubRepository> searchRepositories(String query, int perPage) {
		var result = call(() -> rest.get()
				.uri("/search/repositories?q={q}&sort=stars&order=desc&per_page={perPage}", query, perPage)
				.retrieve()
				.body(RepositorySearchResult.class));
		return result == null || result.items() == null ? List.of() : result.items();
	}

	public Optional<GitHubRepository> getRepository(String owner, String name) {
		return optional(() -> rest.get()
				.uri("/repos/{owner}/{name}", owner, name)
				.retrieve()
				.body(GitHubRepository.class));
	}

	/** Open issues with the given label, newest first, with the body rendered as HTML. Includes pull requests. */
	public List<GitHubIssue> openIssuesWithLabel(String owner, String name, String label, int perPage) {
		List<GitHubIssue> issues = call(() -> rest.get()
				.uri("/repos/{owner}/{name}/issues?labels={label}&state=open&sort=created&direction=desc&per_page={perPage}",
						owner, name, label, perPage)
				.header(HttpHeaders.ACCEPT, HTML)
				.retrieve()
				.body(ISSUE_LIST));
		return issues == null ? List.of() : issues;
	}

	/** The README rendered as HTML (unsanitized). */
	public Optional<String> readmeHtml(String owner, String name) {
		return optional(() -> rest.get()
				.uri("/repos/{owner}/{name}/readme", owner, name)
				.header(HttpHeaders.ACCEPT, HTML)
				.retrieve()
				.body(String.class));
	}

	private <T> Optional<T> optional(Supplier<T> request) {
		try {
			return Optional.ofNullable(call(request));
		}
		catch (GitHubException ex) {
			if (ex.getCause() instanceof RestClientResponseException response
					&& response.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
				return Optional.empty();
			}
			throw ex;
		}
	}

	private static <T> T call(Supplier<T> request) {
		try {
			return request.get();
		}
		catch (RestClientResponseException ex) {
			throw new GitHubException(describe(ex), ex);
		}
		catch (RestClientException ex) {
			throw new GitHubException("Could not reach the GitHub API.", ex);
		}
	}

	private static String describe(RestClientResponseException ex) {
		int status = ex.getStatusCode().value();
		HttpHeaders headers = ex.getResponseHeaders();
		boolean outOfRequests = headers != null && "0".equals(headers.getFirst("X-RateLimit-Remaining"));
		if (status == 429 || (status == 403 && (outOfRequests || ex.getResponseBodyAsString().contains("rate limit")))) {
			return "The GitHub API rate limit was reached. Try again in a few minutes, or set GITHUB_TOKEN for a higher limit.";
		}
		return "The GitHub API answered with an error (HTTP " + status + ").";
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record RepositorySearchResult(List<GitHubRepository> items) {
	}
}
