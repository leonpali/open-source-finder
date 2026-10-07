package com.opensourcefinder.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

import com.opensourcefinder.model.Issue;
import com.opensourcefinder.model.Repository;

import org.springframework.stereotype.Service;

@Service
public class RepositoryService {

	// Placeholder data until the GitHub integration exists.
	private static final List<Repository> SAMPLE_REPOSITORIES = List.of(
			repo("spring-projects", "spring-boot", "Spring Boot helps you to create Spring-powered, production-grade applications and services with absolute minimum fuss.",
					List.of("Java", "Spring", "Gradle"), 79000,
					"Improve documentation for configuration property binding",
					"The reference docs for `@ConfigurationProperties` could use a clearer example of binding to immutable records.\n\nA short section showing constructor binding with records would help newcomers."),
			repo("thymeleaf", "thymeleaf", "Thymeleaf is a modern server-side Java template engine for both web and standalone environments.",
					List.of("Java", "HTML", "Maven"), 2900,
					"Add test coverage for fragment expressions with parameters",
					"Fragment expressions that take parameters are under-tested. Add unit tests covering default values and nested fragments."),
			repo("facebook", "react", "The library for web and native user interfaces.",
					List.of("JavaScript", "TypeScript", "React"), 240000,
					"Clarify warning message for missing keys in lists",
					"The warning shown when list children are missing a `key` prop could point users to the relevant docs page."),
			repo("rust-lang", "rustlings", "Small exercises to get you used to reading and writing Rust code!",
					List.of("Rust"), 61000,
					"Add hint for the iterators exercise",
					"Several users get stuck on the iterators exercise. A more descriptive hint would make it easier to progress."),
			repo("pallets", "flask", "The Python micro framework for building web applications.",
					List.of("Python", "HTML"), 70000,
					"Type hints missing on a few helper functions",
					"Some helper functions are missing return type annotations. Add them and make sure mypy still passes."),
			repo("golang", "go", "The Go programming language.",
					List.of("Go", "Assembly"), 128000,
					"cmd/go: improve error message for invalid module path",
					"When a module path is invalid the error message is terse. It should explain which part of the path is the problem."),
			repo("vuejs", "core", "Vue.js is a progressive, incrementally-adoptable JavaScript framework for building UI on the web.",
					List.of("TypeScript", "Vue", "JavaScript"), 50000,
					"Docs: example for defineModel with modifiers",
					"Add an example to the API docs showing how to use `defineModel` together with custom modifiers."),
			repo("kubernetes", "kubectl", "Issue tracker and mirror of kubectl code.",
					List.of("Go", "Kubernetes", "Docker"), 3000,
					"Add shell completion for a missing flag",
					"The `--selector` flag does not offer shell completion for label keys. Implement completion support."),
			repo("microsoft", "vscode", "Visual Studio Code.",
					List.of("TypeScript", "Electron", "CSS"), 175000,
					"Accessibility: missing aria-label on a toolbar button",
					"A toolbar button in the source control view is missing an `aria-label`, so screen readers announce it as \"button\"."),
			repo("tailwindlabs", "tailwindcss", "A utility-first CSS framework for rapid UI development.",
					List.of("CSS", "TypeScript", "Rust"), 88000,
					"Improve error message for unknown theme keys",
					"Referencing an unknown key in `theme()` produces an unhelpful error. Include the key name and suggestions."),
			repo("django", "django", "The Web framework for perfectionists with deadlines.",
					List.of("Python", "PostgreSQL", "HTML"), 84000,
					"Add missing translation strings in admin",
					"A few strings in the admin changelist view are not wrapped for translation. Wrap them with gettext."),
			repo("docker", "compose", "Define and run multi-container applications with Docker.",
					List.of("Go", "Docker"), 35000,
					"Improve validation error for invalid port mappings",
					"Invalid port mappings in compose files produce a generic error. Point to the offending service and line."));

	public List<Repository> findAll() {
		return SAMPLE_REPOSITORIES;
	}

	/**
	 * Repositories using at least one of the given technologies, or all repositories when none are given.
	 */
	public List<Repository> findByTechnologies(Collection<String> technologies) {
		if (technologies.isEmpty()) {
			return SAMPLE_REPOSITORIES;
		}
		return SAMPLE_REPOSITORIES.stream()
				.filter(repo -> repo.technologies().stream()
						.anyMatch(tech -> technologies.stream().anyMatch(tech::equalsIgnoreCase)))
				.toList();
	}

	public Optional<Repository> find(String owner, String name) {
		return SAMPLE_REPOSITORIES.stream()
				.filter(repo -> repo.owner().equalsIgnoreCase(owner) && repo.name().equalsIgnoreCase(name))
				.findFirst();
	}

	public SortedSet<String> availableTechnologies() {
		var technologies = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
		SAMPLE_REPOSITORIES.forEach(repo -> technologies.addAll(repo.technologies()));
		return technologies;
	}

	public List<String> searchTechnologies(String query) {
		String q = query == null ? "" : query.strip().toLowerCase();
		return availableTechnologies().stream()
				.filter(tech -> tech.toLowerCase().contains(q))
				.toList();
	}

	/**
	 * Maps user input to the known spelling of a technology ("java" -> "Java"), dropping blanks and duplicates.
	 * Unknown technologies are kept as typed.
	 */
	public List<String> normalizeTechnologies(Collection<String> input) {
		var known = availableTechnologies();
		var result = new ArrayList<String>();
		for (String raw : input) {
			String tech = raw.strip();
			if (tech.isEmpty() || result.stream().anyMatch(tech::equalsIgnoreCase)) {
				continue;
			}
			result.add(known.contains(tech) ? known.tailSet(tech).first() : tech);
		}
		return result;
	}

	/**
	 * Resolves a search query typed into the picker: the first known technology containing it, else the query itself.
	 */
	public String resolveTechnology(String query) {
		return searchTechnologies(query).stream().findFirst().orElse(query.strip());
	}

	private static Repository repo(String owner, String name, String description, List<String> technologies,
			int stars, String issueTitle, String issueBody) {
		String url = "https://github.com/" + owner + "/" + name;
		// Links to the repo's issue list until real issue numbers come from the GitHub API.
		var issue = new Issue(0, issueTitle, issueBody, url + "/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22",
				List.of("good first issue"), 0);
		return new Repository(owner, name, description, url, technologies, stars, issue);
	}
}
