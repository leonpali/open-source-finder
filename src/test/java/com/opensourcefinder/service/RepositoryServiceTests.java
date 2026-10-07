package com.opensourcefinder.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.opensourcefinder.github.GitHubClient;
import com.opensourcefinder.github.GitHubException;
import com.opensourcefinder.github.GitHubIssue;
import com.opensourcefinder.github.GitHubRepository;
import com.opensourcefinder.model.Issue;
import com.opensourcefinder.model.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RepositoryServiceTests {

	static final String BASE = "good-first-issues:>0 archived:false stars:>=1000";

	@Mock
	GitHubClient github;

	RepositoryService service;

	@BeforeEach
	void setUp() {
		service = new RepositoryService(github, 1000, 30);
		given(github.searchRepositories(anyString(), anyInt())).willReturn(List.of());
	}

	@Test
	void searchesPopularRepositoriesWithGoodFirstIssues() {
		given(github.searchRepositories(BASE, 30)).willReturn(List.of(repo("pallets", "flask", 70000, "Python")));

		List<Repository> repos = service.findPopular(List.of());

		assertThat(repos).extracting(Repository::fullName).containsExactly("pallets/flask");
		assertThat(repos.get(0).url()).isEqualTo("https://github.com/pallets/flask");
		assertThat(repos.get(0).stars()).isEqualTo(70000);
	}

	@Test
	void searchesKnownTechnologiesByTheirQualifier() {
		service.findPopular(List.of("Java", "React"));

		verify(github).searchRepositories(BASE + " language:java", 30);
		verify(github).searchRepositories(BASE + " topic:react", 30);
	}

	@Test
	void triesUnknownTechnologyAsLanguageThenAsTopic() {
		given(github.searchRepositories(BASE + " topic:web-assembly", 30))
				.willReturn(List.of(repo("a", "wasm", 5000, "Rust")));

		List<Repository> repos = service.findPopular(List.of("Web Assembly"));

		verify(github).searchRepositories(BASE + " language:\"Web Assembly\"", 30);
		assertThat(repos).extracting(Repository::name).containsExactly("wasm");
	}

	@Test
	void doesNotFallBackToTopicWhenLanguageMatches() {
		given(github.searchRepositories(BASE + " language:\"Zig\"", 30))
				.willReturn(List.of(repo("ziglang", "zig", 40000, "Zig")));

		service.findPopular(List.of("Zig"));

		verify(github, never()).searchRepositories(BASE + " topic:zig", 30);
	}

	@Test
	void mergesResultsOfSeveralTechnologiesByStars() {
		given(github.searchRepositories(BASE + " language:go", 30))
				.willReturn(List.of(repo("golang", "go", 128000, "Go"), repo("docker", "compose", 35000, "Go")));
		given(github.searchRepositories(BASE + " topic:docker", 30))
				.willReturn(List.of(repo("docker", "compose", 35000, "Go"), repo("moby", "moby", 70000, "Go")));

		List<Repository> repos = service.findPopular(List.of("Go", "Docker"));

		assertThat(repos).extracting(Repository::name).containsExactly("go", "moby", "compose");
	}

	@Test
	void cachesSearches() {
		service.findPopular(List.of("Java"));
		service.findPopular(List.of("Java"));

		verify(github, times(1)).searchRepositories(BASE + " language:java", 30);
	}

	@Test
	void doesNotCacheFailures() {
		given(github.searchRepositories(BASE, 30))
				.willThrow(new GitHubException("rate limit", null))
				.willReturn(List.of(repo("pallets", "flask", 70000, "Python")));

		assertThatThrownBy(() -> service.findPopular(List.of())).isInstanceOf(GitHubException.class);
		assertThat(service.findPopular(List.of())).hasSize(1);
	}

	@Test
	void findsRepositoriesFromEarlierSearchesWithoutAnotherRequest() {
		given(github.searchRepositories(BASE, 30)).willReturn(List.of(repo("pallets", "flask", 70000, "Python")));
		service.findPopular(List.of());

		assertThat(service.find("Pallets", "Flask")).isPresent();
		verify(github, never()).getRepository(anyString(), anyString());
	}

	@Test
	void looksUpOtherRepositoriesOnGitHub() {
		given(github.getRepository("pallets", "flask"))
				.willReturn(Optional.of(repo("pallets", "flask", 70000, "Python")));

		assertThat(service.find("pallets", "flask")).get().extracting(Repository::name).isEqualTo("flask");
		assertThat(service.find("nobody", "nothing")).isEmpty();
	}

	@Test
	void buildsTechnologiesFromLanguageAndTopics() {
		var repo = new GitHubRepository("x", new GitHubRepository.Owner("o"), null, "https://github.com/o/x", 1,
				"JavaScript", List.of("hacktoberfest", "frontend", "reactjs", "golang", "good-first-issue",
						"javascript", "ui", "design-system", "web"));

		assertThat(RepositoryService.technologies(repo))
				.containsExactly("JavaScript", "React", "Go", "frontend", "ui");
	}

	@Test
	void handlesRepositoriesWithoutLanguageOrTopics() {
		var repo = new GitHubRepository("x", new GitHubRepository.Owner("o"), null, "https://github.com/o/x", 1,
				null, null);

		assertThat(RepositoryService.technologies(repo)).isEmpty();
	}

	@Test
	void picksNewestUnassignedIssueAndSkipsPullRequests() {
		Repository flask = RepositoryService.toRepository(repo("pallets", "flask", 70000, "Python"));
		given(github.openIssuesWithLabel("pallets", "flask", "good first issue", 20)).willReturn(List.of(
				issue(9, false, true),
				issue(8, true, false),
				issue(7, false, false)));

		Issue issue = service.findGoodFirstIssue(flask).orElseThrow();

		assertThat(issue.number()).isEqualTo(7);
		assertThat(issue.author()).isEqualTo("octocat");
		assertThat(issue.labels()).containsExactly("good first issue");
		assertThat(issue.bodyHtml()).isEqualTo("<p>Body <a href=\"https://github.com/pallets/flask/blob/HEAD/CONTRIBUTING.md\""
				+ " target=\"_blank\" rel=\"noopener\">guide</a></p>");
	}

	@Test
	void skipsIssuesNotWrittenInEnglish() {
		Repository flask = RepositoryService.toRepository(repo("pallets", "flask", 70000, "Python"));
		given(github.openIssuesWithLabel("pallets", "flask", "good first issue", 20)).willReturn(List.of(
				issue(9, "修复登录页面的显示问题", "<p>English body</p>"),
				issue(8, "Fix login page layout", "<p>登录页面在移动端显示错位，请修复。</p>"),
				issue(7, "Fix login page layout", "<p>The login page is misaligned on mobile.</p>")));

		assertThat(service.findGoodFirstIssue(flask)).get().extracting(Issue::number).isEqualTo(7);
	}

	@Test
	void returnsEmptyWhenNoIssueIsInEnglish() {
		Repository flask = RepositoryService.toRepository(repo("pallets", "flask", 70000, "Python"));
		given(github.openIssuesWithLabel("pallets", "flask", "good first issue", 20))
				.willReturn(List.of(issue(9, "修复登录页面的显示问题", "<p>请修复。</p>")));

		assertThat(service.findGoodFirstIssue(flask)).isEmpty();
	}

	@Test
	void dropsRepositoriesDescribedInAnotherLanguage() {
		given(github.searchRepositories(BASE, 30)).willReturn(List.of(
				repo("pallets", "flask", 70000, "Python"),
				new GitHubRepository("vue-admin", new GitHubRepository.Owner("someone"), "一个基于 Vue 的后台管理系统模板",
						"https://github.com/someone/vue-admin", 60000, "Vue", List.of()),
				new GitHubRepository("no-description", new GitHubRepository.Owner("someone"), null,
						"https://github.com/someone/no-description", 50000, "Go", List.of())));

		assertThat(service.findPopular(List.of())).extracting(Repository::name)
				.containsExactly("flask", "no-description");
	}

	@Test
	void stillFindsNonEnglishRepositoryByName() {
		given(github.getRepository("someone", "vue-admin")).willReturn(Optional.of(new GitHubRepository("vue-admin",
				new GitHubRepository.Owner("someone"), "一个基于 Vue 的后台管理系统模板", "https://github.com/someone/vue-admin",
				60000, "Vue", List.of())));

		assertThat(service.find("someone", "vue-admin")).isPresent();
	}

	@Test
	void fallsBackToAssignedIssue() {
		Repository flask = RepositoryService.toRepository(repo("pallets", "flask", 70000, "Python"));
		given(github.openIssuesWithLabel("pallets", "flask", "good first issue", 20))
				.willReturn(List.of(issue(9, false, true)));

		assertThat(service.findGoodFirstIssue(flask)).get().extracting(Issue::number).isEqualTo(9);
	}

	@Test
	void returnsEmptyWhenOnlyPullRequestsHaveTheLabel() {
		Repository flask = RepositoryService.toRepository(repo("pallets", "flask", 70000, "Python"));
		given(github.openIssuesWithLabel("pallets", "flask", "good first issue", 20))
				.willReturn(List.of(issue(8, true, false)));

		assertThat(service.findGoodFirstIssue(flask)).isEmpty();
	}

	static GitHubRepository repo(String owner, String name, int stars, String language) {
		return new GitHubRepository(name, new GitHubRepository.Owner(owner), name + " description",
				"https://github.com/" + owner + "/" + name, stars, language, List.of());
	}

	static GitHubIssue issue(int number, String title, String bodyHtml) {
		return new GitHubIssue(number, title, bodyHtml, "https://github.com/pallets/flask/issues/" + number,
				List.of(new GitHubIssue.Label("good first issue")), 0, new GitHubIssue.User("octocat"), List.of(), null);
	}

	static GitHubIssue issue(int number, boolean pullRequest, boolean assigned) {
		return new GitHubIssue(number, "Issue " + number,
				"<p>Body <a href=\"CONTRIBUTING.md\">guide</a><script>alert(1)</script></p>",
				"https://github.com/pallets/flask/issues/" + number,
				List.of(new GitHubIssue.Label("good first issue")), 1, new GitHubIssue.User("octocat"),
				assigned ? List.of(new GitHubIssue.User("someone")) : List.of(),
				pullRequest ? Map.of("url", "x") : null);
	}
}
