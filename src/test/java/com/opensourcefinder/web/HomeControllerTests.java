package com.opensourcefinder.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;
import java.util.Optional;

import com.opensourcefinder.github.GitHubException;
import com.opensourcefinder.model.Issue;
import com.opensourcefinder.model.Repository;
import com.opensourcefinder.service.ReadmeService;
import com.opensourcefinder.service.RepositoryService;
import com.opensourcefinder.service.TechnologyCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Both services are mocked, so these tests never call the GitHub API.
 */
@WebMvcTest(HomeController.class)
class HomeControllerTests {

	static final Repository FLASK = new Repository("pallets", "flask", "The Python micro framework.",
			"https://github.com/pallets/flask", List.of("Python", "Flask"), 70000);

	static final Repository RUSTLINGS = new Repository("rust-lang", "rustlings", "Small exercises.",
			"https://github.com/rust-lang/rustlings", List.of("Rust"), 61000);

	static final Issue ISSUE = new Issue(42, "Add type hints", "<p>Some <code>helpers</code> lack hints.</p>",
			"https://github.com/pallets/flask/issues/42", List.of("good first issue", "typing"), 3, "octocat");

	static final GitHubException RATE_LIMITED = new GitHubException("The GitHub API rate limit was reached.", null);

	@Autowired
	MockMvc mvc;

	@MockitoBean
	RepositoryService repositories;

	@MockitoBean
	ReadmeService readmes;

	@BeforeEach
	void knownRepositories() {
		given(repositories.find(anyString(), anyString())).willReturn(Optional.empty());
		given(repositories.find("pallets", "flask")).willReturn(Optional.of(FLASK));
		given(repositories.find("rust-lang", "rustlings")).willReturn(Optional.of(RUSTLINGS));
		given(repositories.findPopular(anyList())).willReturn(List.of(FLASK, RUSTLINGS));
	}

	@Nested
	class Index {

		@Test
		void rendersPopularRepositoriesWithoutFilter() throws Exception {
			mvc.perform(get("/"))
					.andExpect(status().isOk())
					.andExpect(view().name("index"))
					.andExpect(model().attribute("repositories", hasSize(2)))
					.andExpect(model().attribute("filter", new FilterState(List.of())))
					.andExpect(model().attribute("technologyOptions", TechnologyCatalog.names()))
					.andExpect(model().attributeDoesNotExist("selectedRepo", "error"))
					.andExpect(content().string(containsString("rustlings")))
					.andExpect(content().string(containsString("Nothing selected yet")))
					.andExpect(content().string(not(containsString("class=\"chip\""))));

			verify(repositories).findPopular(List.of());
		}

		@Test
		void passesNormalizedTechnologiesToTheService() throws Exception {
			mvc.perform(get("/").param("tech", "go", "Golang", "spring-boot", " ", ""))
					.andExpect(model().attribute("filter", new FilterState(List.of("Go", "Spring Boot"))));

			verify(repositories).findPopular(List.of("Go", "Spring Boot"));
		}

		@Test
		void keepsUnknownTechnologiesAsTyped() throws Exception {
			mvc.perform(get("/").param("tech", "Zig"))
					.andExpect(model().attribute("filter", new FilterState(List.of("Zig"))));

			verify(repositories).findPopular(List.of("Zig"));
		}

		@Test
		void highlightsTagsOfSelectedTechnologies() throws Exception {
			mvc.perform(get("/").param("tech", "rust"))
					.andExpect(content().string(containsString("<span class=\"tag is-match\">Rust</span>")))
					.andExpect(content().string(containsString("<span class=\"tag\">Python</span>")));
		}

		@Test
		void rendersChipsThatLinkToTheFilterWithoutThatTechnology() throws Exception {
			mvc.perform(get("/").param("tech", "Go", "Rust"))
					.andExpect(content().string(containsString("href=\"/?tech=Rust\" aria-label=\"Remove Go\"")))
					.andExpect(content().string(containsString("href=\"/?tech=Go\" aria-label=\"Remove Rust\"")))
					.andExpect(content().string(containsString("Clear all")));
		}

		@Test
		void linksCardsToTheirDetailView() throws Exception {
			mvc.perform(get("/").param("tech", "Python"))
					.andExpect(content().string(containsString("href=\"/?tech=Python&amp;repo=pallets/flask\"")))
					.andExpect(content().string(containsString("hx-get=\"/repos/pallets/flask?tech=Python\"")));
		}

		@Test
		void showsEmptyStateWhenNothingMatches() throws Exception {
			given(repositories.findPopular(anyList())).willReturn(List.of());

			mvc.perform(get("/").param("tech", "Elixir"))
					.andExpect(model().attribute("repositories", hasSize(0)))
					.andExpect(content().string(containsString("No repositories match")));
		}

		@Test
		void showsErrorBannerWhenGitHubFails() throws Exception {
			given(repositories.findPopular(anyList())).willThrow(RATE_LIMITED);

			mvc.perform(get("/"))
					.andExpect(status().isOk())
					.andExpect(model().attribute("repositories", hasSize(0)))
					.andExpect(model().attribute("error", RATE_LIMITED.getMessage()))
					.andExpect(content().string(containsString("Couldn't load repositories from GitHub")))
					.andExpect(content().string(not(containsString("No repositories match"))));
		}

		@Test
		void redirectsPlainRequestWithQueryToCanonicalUrl() throws Exception {
			mvc.perform(get("/").param("tech", "Go").param("q", "pyt"))
					.andExpect(status().is3xxRedirection())
					.andExpect(redirectedUrl("/?tech=Go&tech=Python"));

			verify(repositories, never()).findPopular(anyList());
		}

		@Test
		void tellsHtmxWhichUrlToPushForQuery() throws Exception {
			mvc.perform(get("/").param("tech", "Go").param("q", "pyt").header("HX-Request", "true"))
					.andExpect(status().isOk())
					.andExpect(header().string("HX-Push-Url", "/?tech=Go&tech=Python"))
					.andExpect(model().attribute("filter", new FilterState(List.of("Go", "Python"))));
		}

		@Test
		void keepsUnknownQueryAsTyped() throws Exception {
			mvc.perform(get("/").param("q", " Zig ").header("HX-Request", "true"))
					.andExpect(header().string("HX-Push-Url", "/?tech=Zig"));
		}

		@Test
		void doesNotPushUrlWithoutQuery() throws Exception {
			mvc.perform(get("/").param("tech", "Go").header("HX-Request", "true"))
					.andExpect(header().doesNotExist("HX-Push-Url"));
		}

		@Test
		void opensDetailDialogForRepoParameter() throws Exception {
			mvc.perform(get("/").param("repo", "pallets/flask"))
					.andExpect(model().attribute("selectedRepo", FLASK))
					.andExpect(content().string(containsString("open=\"open\"")))
					.andExpect(content().string(containsString("data-repo=\"pallets/flask\"")));
		}

		@Test
		void ignoresUnknownOrMalformedRepoParameter() throws Exception {
			mvc.perform(get("/").param("repo", "nobody/nothing"))
					.andExpect(status().isOk())
					.andExpect(model().attributeDoesNotExist("selectedRepo"));
			mvc.perform(get("/").param("repo", "flask"))
					.andExpect(status().isOk())
					.andExpect(model().attributeDoesNotExist("selectedRepo"));
		}

		@Test
		void stillRendersListWhenRepoLookupFails() throws Exception {
			given(repositories.find("pallets", "flask")).willThrow(RATE_LIMITED);

			mvc.perform(get("/").param("repo", "pallets/flask"))
					.andExpect(status().isOk())
					.andExpect(model().attribute("repositories", hasSize(2)))
					.andExpect(model().attributeDoesNotExist("selectedRepo"));
		}
	}

	@Nested
	class Technologies {

		@Test
		void returnsOnlyThePickerFragmentWithMatchingOptions() throws Exception {
			mvc.perform(get("/technologies").param("q", "script"))
					.andExpect(status().isOk())
					.andExpect(view().name("index :: techState"))
					.andExpect(model().attribute("technologyOptions", List.of("JavaScript", "TypeScript")))
					.andExpect(content().string(containsString("id=\"tech-state\"")))
					.andExpect(content().string(not(containsString("<html"))));
		}

		@Test
		void marksSelectedOptionsAndKeepsThemAsHiddenInputs() throws Exception {
			mvc.perform(get("/technologies").param("q", "type").param("tech", "typescript"))
					.andExpect(content().string(containsString("<input type=\"hidden\" name=\"tech\" value=\"TypeScript\">")))
					.andExpect(content().string(containsString("tech-option is-selected")));
		}

		@Test
		void offersToAddUnknownTechnology() throws Exception {
			mvc.perform(get("/technologies").param("q", "Zig"))
					.andExpect(model().attribute("technologyOptions", hasSize(0)))
					.andExpect(content().string(containsString("Press Enter to add")))
					.andExpect(content().string(containsString("Zig")));
		}

		@Test
		void listsTheWholeCatalogForEmptyQuery() throws Exception {
			mvc.perform(get("/technologies"))
					.andExpect(model().attribute("technologyOptions", TechnologyCatalog.names()))
					.andExpect(content().string(not(containsString("Press Enter to add"))));
		}
	}

	@Nested
	class Detail {

		@Test
		void returnsDialogFragmentThatLoadsReadmeAndIssue() throws Exception {
			mvc.perform(get("/repos/rust-lang/rustlings").param("tech", "Rust"))
					.andExpect(status().isOk())
					.andExpect(view().name("index :: detail"))
					.andExpect(content().string(not(containsString("<html"))))
					.andExpect(content().string(containsString("data-repo=\"rust-lang/rustlings\"")))
					.andExpect(content().string(containsString("href=\"https://github.com/rust-lang/rustlings\"")))
					.andExpect(content().string(containsString("hx-get=\"/repos/rust-lang/rustlings/readme\"")))
					.andExpect(content().string(containsString("hx-get=\"/repos/rust-lang/rustlings/issue\"")));
		}

		@Test
		void closeUrlKeepsTheCurrentFilter() throws Exception {
			mvc.perform(get("/repos/rust-lang/rustlings").param("tech", "Rust", "Go"))
					.andExpect(content().string(containsString("data-close-url=\"/?tech=Rust&amp;tech=Go\"")));
		}

		@Test
		void returns404ForUnknownRepository() throws Exception {
			mvc.perform(get("/repos/nobody/nothing"))
					.andExpect(status().isNotFound());
		}

		@Test
		void showsErrorInDialogWhenGitHubFails() throws Exception {
			given(repositories.find("pallets", "flask")).willThrow(RATE_LIMITED);

			mvc.perform(get("/repos/pallets/flask"))
					.andExpect(status().isOk())
					.andExpect(content().string(containsString("data-repo=\"pallets/flask\"")))
					.andExpect(content().string(containsString(RATE_LIMITED.getMessage())));
		}
	}

	@Nested
	class Readme {

		@Test
		void rendersReadmeHtmlFromService() throws Exception {
			given(readmes.findReadmeHtml(FLASK)).willReturn(Optional.of("<h1>Flask</h1><p>Hello</p>"));

			mvc.perform(get("/repos/pallets/flask/readme"))
					.andExpect(status().isOk())
					.andExpect(view().name("index :: readme"))
					.andExpect(content().string(containsString("<h1>Flask</h1><p>Hello</p>")))
					.andExpect(content().string(not(containsString("could not be loaded"))));
		}

		@Test
		void linksToGitHubWhenReadmeIsUnavailable() throws Exception {
			given(readmes.findReadmeHtml(FLASK)).willReturn(Optional.empty());

			mvc.perform(get("/repos/pallets/flask/readme"))
					.andExpect(status().isOk())
					.andExpect(content().string(containsString("README could not be loaded")))
					.andExpect(content().string(containsString("href=\"https://github.com/pallets/flask#readme\"")));
		}

		@Test
		void fallsBackForUnknownRepositoryWithoutFetchingReadme() throws Exception {
			mvc.perform(get("/repos/nobody/nothing/readme"))
					.andExpect(status().isOk())
					.andExpect(content().string(containsString("README could not be loaded")));

			verify(readmes, never()).findReadmeHtml(any());
		}

		@Test
		void fallsBackWhenRepoLookupFails() throws Exception {
			given(repositories.find("pallets", "flask")).willThrow(RATE_LIMITED);

			mvc.perform(get("/repos/pallets/flask/readme"))
					.andExpect(status().isOk())
					.andExpect(content().string(containsString("README could not be loaded")));
		}
	}

	@Nested
	class GoodFirstIssue {

		@Test
		void rendersIssueFromService() throws Exception {
			given(repositories.findGoodFirstIssue(FLASK)).willReturn(Optional.of(ISSUE));

			mvc.perform(get("/repos/pallets/flask/issue"))
					.andExpect(status().isOk())
					.andExpect(view().name("index :: issue"))
					.andExpect(content().string(not(containsString("<html"))))
					.andExpect(content().string(containsString("Add type hints")))
					.andExpect(content().string(containsString("#42")))
					.andExpect(content().string(containsString("<strong>octocat</strong>")))
					.andExpect(content().string(containsString("3 comments")))
					.andExpect(content().string(containsString("<span class=\"tag\">typing</span>")))
					.andExpect(content().string(containsString("<p>Some <code>helpers</code> lack hints.</p>")))
					.andExpect(content().string(containsString("href=\"https://github.com/pallets/flask/issues/42\"")));
		}

		@Test
		void saysSoWhenThereIsNoGoodFirstIssue() throws Exception {
			given(repositories.findGoodFirstIssue(FLASK)).willReturn(Optional.empty());

			mvc.perform(get("/repos/pallets/flask/issue"))
					.andExpect(status().isOk())
					.andExpect(content().string(containsString("No open good first issue in English found")))
					.andExpect(content().string(containsString("href=\"https://github.com/pallets/flask/issues\"")));
		}

		@Test
		void showsGitHubErrorMessage() throws Exception {
			given(repositories.findGoodFirstIssue(FLASK)).willThrow(RATE_LIMITED);

			mvc.perform(get("/repos/pallets/flask/issue"))
					.andExpect(status().isOk())
					.andExpect(content().string(containsString(RATE_LIMITED.getMessage())));
		}
	}
}
