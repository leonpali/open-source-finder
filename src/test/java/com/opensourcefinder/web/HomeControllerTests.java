package com.opensourcefinder.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
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

import com.opensourcefinder.service.ReadmeService;
import com.opensourcefinder.service.RepositoryService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Uses the real {@link RepositoryService} (its sample data is fixed) and mocks {@link ReadmeService}, which would
 * otherwise call the GitHub API.
 */
@WebMvcTest(HomeController.class)
@Import(RepositoryService.class)
class HomeControllerTests {

	static final int TOTAL_REPOSITORIES = 12;

	@Autowired
	MockMvc mvc;

	@Autowired
	RepositoryService repositories;

	@MockitoBean
	ReadmeService readmes;

	@Nested
	class Index {

		@Test
		void rendersAllRepositoriesWithoutFilter() throws Exception {
			mvc.perform(get("/"))
					.andExpect(status().isOk())
					.andExpect(view().name("index"))
					.andExpect(model().attribute("repositories", hasSize(TOTAL_REPOSITORIES)))
					.andExpect(model().attribute("filter", new FilterState(List.of())))
					.andExpect(model().attributeDoesNotExist("selectedRepo"))
					.andExpect(content().string(containsString("spring-boot")))
					.andExpect(content().string(containsString("Nothing selected yet")))
					.andExpect(content().string(not(containsString("class=\"chip\""))));
		}

		@Test
		void filtersByTechnologyIgnoringCase() throws Exception {
			mvc.perform(get("/").param("tech", "go"))
					.andExpect(status().isOk())
					.andExpect(model().attribute("filter", new FilterState(List.of("Go"))))
					.andExpect(model().attribute("repositories", hasSize(3)))
					.andExpect(content().string(containsString("3 of 12")))
					.andExpect(content().string(containsString("kubectl")))
					.andExpect(content().string(not(containsString("rustlings"))));
		}

		@Test
		void showsRepositoriesMatchingAnySelectedTechnology() throws Exception {
			mvc.perform(get("/").param("tech", "Go", "Rust"))
					.andExpect(model().attribute("repositories", hasSize(5)))
					.andExpect(content().string(containsString("rustlings")))
					.andExpect(content().string(containsString("compose")));
		}

		@Test
		void dropsDuplicateAndBlankTechnologies() throws Exception {
			mvc.perform(get("/").param("tech", "Java", "java", " ", ""))
					.andExpect(model().attribute("filter", new FilterState(List.of("Java"))));
		}

		@Test
		void rendersChipsThatLinkToTheFilterWithoutThatTechnology() throws Exception {
			mvc.perform(get("/").param("tech", "Go", "Rust"))
					.andExpect(content().string(containsString("href=\"/?tech=Rust\" aria-label=\"Remove Go\"")))
					.andExpect(content().string(containsString("href=\"/?tech=Go\" aria-label=\"Remove Rust\"")))
					.andExpect(content().string(containsString("Clear all")));
		}

		@Test
		void showsEmptyStateWhenNothingMatches() throws Exception {
			mvc.perform(get("/").param("tech", "Elixir"))
					.andExpect(model().attribute("repositories", hasSize(0)))
					.andExpect(content().string(containsString("No repositories match")))
					.andExpect(content().string(containsString("0 of 12")));
		}

		@Test
		void redirectsPlainRequestWithQueryToCanonicalUrl() throws Exception {
			mvc.perform(get("/").param("tech", "Go").param("q", "pyt"))
					.andExpect(status().is3xxRedirection())
					.andExpect(redirectedUrl("/?tech=Go&tech=Python"));
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
			mvc.perform(get("/").param("q", " Elixir ").header("HX-Request", "true"))
					.andExpect(header().string("HX-Push-Url", "/?tech=Elixir"));
		}

		@Test
		void doesNotPushUrlWithoutQuery() throws Exception {
			mvc.perform(get("/").param("tech", "Go").header("HX-Request", "true"))
					.andExpect(header().doesNotExist("HX-Push-Url"));
		}

		@Test
		void opensDetailDialogForRepoParameter() throws Exception {
			mvc.perform(get("/").param("repo", "pallets/flask"))
					.andExpect(model().attribute("selectedRepo", repositories.find("pallets", "flask").orElseThrow()))
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
			mvc.perform(get("/technologies").param("q", "Elixir"))
					.andExpect(model().attribute("technologyOptions", hasSize(0)))
					.andExpect(content().string(containsString("Press Enter to add")))
					.andExpect(content().string(containsString("Elixir")));
		}

		@Test
		void listsEverythingForEmptyQuery() throws Exception {
			mvc.perform(get("/technologies"))
					.andExpect(model().attribute("technologyOptions", hasSize(18)))
					.andExpect(content().string(not(containsString("Press Enter to add"))));
		}
	}

	@Nested
	class Detail {

		@Test
		void returnsDialogFragmentForRepository() throws Exception {
			mvc.perform(get("/repos/rust-lang/rustlings").param("tech", "Rust"))
					.andExpect(status().isOk())
					.andExpect(view().name("index :: detail"))
					.andExpect(content().string(not(containsString("<html"))))
					.andExpect(content().string(containsString("data-repo=\"rust-lang/rustlings\"")))
					.andExpect(content().string(containsString("Add hint for the iterators exercise")))
					.andExpect(content().string(containsString("href=\"https://github.com/rust-lang/rustlings\"")))
					.andExpect(content().string(containsString("hx-get=\"/repos/rust-lang/rustlings/readme\"")));
		}

		@Test
		void closeUrlKeepsTheCurrentFilter() throws Exception {
			mvc.perform(get("/repos/rust-lang/rustlings").param("tech", "Rust", "Go"))
					.andExpect(content().string(containsString("data-close-url=\"/?tech=Rust&amp;tech=Go\"")));
		}

		@Test
		void findsRepositoryIgnoringCase() throws Exception {
			mvc.perform(get("/repos/Pallets/Flask"))
					.andExpect(status().isOk())
					.andExpect(content().string(containsString("data-repo=\"pallets/flask\"")));
		}

		@Test
		void returns404ForUnknownRepository() throws Exception {
			mvc.perform(get("/repos/nobody/nothing"))
					.andExpect(status().isNotFound());
		}
	}

	@Nested
	class Readme {

		@Test
		void rendersReadmeHtmlFromService() throws Exception {
			given(readmes.findReadmeHtml(any())).willReturn(Optional.of("<h1>Flask</h1><p>Hello</p>"));

			mvc.perform(get("/repos/pallets/flask/readme"))
					.andExpect(status().isOk())
					.andExpect(view().name("index :: readme"))
					.andExpect(content().string(containsString("<h1>Flask</h1><p>Hello</p>")))
					.andExpect(content().string(not(containsString("could not be loaded"))));
		}

		@Test
		void linksToGitHubWhenReadmeIsUnavailable() throws Exception {
			given(readmes.findReadmeHtml(any())).willReturn(Optional.empty());

			mvc.perform(get("/repos/pallets/flask/readme"))
					.andExpect(status().isOk())
					.andExpect(content().string(containsString("README could not be loaded")))
					.andExpect(content().string(containsString("href=\"https://github.com/pallets/flask#readme\"")));
		}

		@Test
		void returns404ForUnknownRepositoryWithoutCallingGitHub() throws Exception {
			mvc.perform(get("/repos/nobody/nothing/readme"))
					.andExpect(status().isNotFound());

			verify(readmes, never()).findReadmeHtml(any());
		}
	}
}
