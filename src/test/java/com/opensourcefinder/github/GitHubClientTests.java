package com.opensourcefinder.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GitHubClientTests {

	static final String API = "https://api.github.com";

	MockRestServiceServer server;

	GitHubClient client;

	@BeforeEach
	void setUp() {
		var builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		client = new GitHubClient(builder, API, "secret-token");
	}

	@Test
	void searchesRepositoriesByStarsWithEncodedQuery() {
		server.expect(requestTo(API + "/search/repositories?q=good-first-issues%3A%3E0%20language%3Ajava"
						+ "&sort=stars&order=desc&per_page=30"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer secret-token"))
				.andExpect(header(HttpHeaders.ACCEPT, GitHubClient.JSON))
				.andRespond(withSuccess("""
						{"total_count": 1, "items": [{
							"name": "spring-boot", "owner": {"login": "spring-projects", "id": 1},
							"description": "Spring Boot", "html_url": "https://github.com/spring-projects/spring-boot",
							"stargazers_count": 79000, "language": "Java", "topics": ["java", "spring-boot"],
							"forks_count": 41000
						}]}
						""", MediaType.APPLICATION_JSON));

		List<GitHubRepository> repos = client.searchRepositories("good-first-issues:>0 language:java", 30);

		assertThat(repos).containsExactly(new GitHubRepository("spring-boot",
				new GitHubRepository.Owner("spring-projects"), "Spring Boot",
				"https://github.com/spring-projects/spring-boot", 79000, "Java", List.of("java", "spring-boot")));
		server.verify();
	}

	@Test
	void omitsAuthorizationWithoutToken() {
		var builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		client = new GitHubClient(builder, API, "");

		server.expect(requestTo(API + "/repos/pallets/flask"))
				.andExpect(request -> assertThat(request.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)).isFalse())
				.andRespond(withSuccess("""
						{"name": "flask", "owner": {"login": "pallets"}, "html_url": "https://github.com/pallets/flask",
						 "stargazers_count": 70000}
						""", MediaType.APPLICATION_JSON));

		assertThat(client.getRepository("pallets", "flask")).get()
				.extracting(GitHubRepository::name).isEqualTo("flask");
	}

	@Test
	void returnsEmptyForUnknownRepository() {
		server.expect(requestTo(API + "/repos/nobody/nothing")).andRespond(withResourceNotFound());

		assertThat(client.getRepository("nobody", "nothing")).isEmpty();
	}

	@Test
	void fetchesIssuesWithLabelAsHtml() {
		server.expect(requestTo(API + "/repos/pallets/flask/issues?labels=good%20first%20issue&state=open"
						+ "&sort=created&direction=desc&per_page=20"))
				.andExpect(header(HttpHeaders.ACCEPT, GitHubClient.HTML))
				.andRespond(withSuccess("""
						[{"number": 7, "title": "A PR", "html_url": "u7", "labels": [], "comments": 0,
						  "user": {"login": "a"}, "assignees": [], "pull_request": {"url": "x"}},
						 {"number": 5, "title": "An issue", "body_html": "<p>Hi</p>", "html_url": "u5",
						  "labels": [{"name": "good first issue", "color": "7057ff"}], "comments": 2,
						  "user": {"login": "b"}, "assignees": [{"login": "c"}]}]
						""", MediaType.APPLICATION_JSON));

		List<GitHubIssue> issues = client.openIssuesWithLabel("pallets", "flask", "good first issue", 20);

		assertThat(issues).hasSize(2);
		assertThat(issues.get(0).isPullRequest()).isTrue();
		GitHubIssue issue = issues.get(1);
		assertThat(issue.isPullRequest()).isFalse();
		assertThat(issue.isAssigned()).isTrue();
		assertThat(issue.bodyHtml()).isEqualTo("<p>Hi</p>");
		assertThat(issue.labels()).containsExactly(new GitHubIssue.Label("good first issue"));
		assertThat(issue.user().login()).isEqualTo("b");
	}

	@Test
	void fetchesReadmeAsHtml() {
		server.expect(requestTo(API + "/repos/pallets/flask/readme"))
				.andExpect(header(HttpHeaders.ACCEPT, GitHubClient.HTML))
				.andRespond(withSuccess("<h1>Flask</h1>", MediaType.TEXT_HTML));

		assertThat(client.readmeHtml("pallets", "flask")).contains("<h1>Flask</h1>");
	}

	@Test
	void returnsEmptyWhenThereIsNoReadme() {
		server.expect(requestTo(API + "/repos/pallets/flask/readme")).andRespond(withResourceNotFound());

		assertThat(client.readmeHtml("pallets", "flask")).isEmpty();
	}

	@Test
	void explainsRateLimit() {
		var headers = new HttpHeaders();
		headers.add("X-RateLimit-Remaining", "0");
		server.expect(requestTo(API + "/repos/pallets/flask"))
				.andRespond(withStatus(HttpStatus.FORBIDDEN).headers(headers)
						.body("{\"message\": \"API rate limit exceeded\"}"));

		assertThatThrownBy(() -> client.getRepository("pallets", "flask"))
				.isInstanceOf(GitHubException.class)
				.hasMessageContaining("rate limit")
				.hasMessageContaining("GITHUB_TOKEN");
	}

	@Test
	void reportsOtherErrorsWithStatus() {
		server.expect(requestTo(API + "/search/repositories?q=x&sort=stars&order=desc&per_page=30"))
				.andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT));

		assertThatThrownBy(() -> client.searchRepositories("x", 30))
				.isInstanceOf(GitHubException.class)
				.hasMessageContaining("HTTP 422");
	}
}
