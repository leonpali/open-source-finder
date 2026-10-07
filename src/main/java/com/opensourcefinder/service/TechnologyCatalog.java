package com.opensourcefinder.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The technologies offered in the picker and how each maps to a GitHub search qualifier. Names are compared by
 * {@link #key(String)}, so "Spring Boot", "spring-boot" and "springboot" are the same technology.
 */
public final class TechnologyCatalog {

	/**
	 * @param qualifier GitHub repository search qualifier, e.g. {@code language:java} or {@code topic:react}
	 * @param aliases other spellings, e.g. GitHub topic names
	 */
	public record Technology(String name, String qualifier, List<String> aliases) {

		boolean matches(String other) {
			String k = key(other);
			return key(name).equals(k) || key(qualifier.substring(qualifier.indexOf(':') + 1)).equals(k)
					|| aliases.stream().anyMatch(alias -> key(alias).equals(k));
		}
	}

	private static final List<Technology> TECHNOLOGIES = List.of(
			language("C", "c"),
			language("C#", "csharp"),
			language("C++", "cpp"),
			language("CSS", "css"),
			language("Dart", "dart"),
			language("Elixir", "elixir"),
			language("Go", "go", "golang"),
			language("Haskell", "haskell"),
			language("HTML", "html"),
			language("Java", "java"),
			language("JavaScript", "javascript", "js"),
			language("Kotlin", "kotlin"),
			language("Lua", "lua"),
			language("PHP", "php"),
			language("Python", "python"),
			language("Ruby", "ruby"),
			language("Rust", "rust"),
			language("Scala", "scala"),
			language("Shell", "shell", "bash"),
			language("Swift", "swift"),
			language("TypeScript", "typescript", "ts"),
			topic("Angular", "angular"),
			topic("Django", "django"),
			topic("Docker", "docker"),
			topic("Flask", "flask"),
			topic("Flutter", "flutter"),
			topic("Kubernetes", "kubernetes", "k8s"),
			topic("Laravel", "laravel"),
			topic("Machine Learning", "machine-learning", "ml"),
			topic("Next.js", "nextjs"),
			topic("Node.js", "nodejs", "node"),
			topic("Rails", "rails", "ruby-on-rails"),
			topic("React", "react", "reactjs"),
			topic("Spring Boot", "spring-boot"),
			topic("Svelte", "svelte"),
			topic("Vue", "vue", "vuejs"));

	private TechnologyCatalog() {
	}

	/** Comparison key: lower case without spaces, dots, dashes and underscores. */
	public static String key(String name) {
		return name.toLowerCase(Locale.ROOT).replaceAll("[\\s._-]", "");
	}

	public static boolean same(String a, String b) {
		return key(a).equals(key(b));
	}

	public static Optional<Technology> lookup(String name) {
		return TECHNOLOGIES.stream().filter(tech -> tech.matches(name)).findFirst();
	}

	public static List<String> names() {
		return TECHNOLOGIES.stream()
				.map(Technology::name)
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.toList();
	}

	/** Catalog names containing the query (ignoring case and punctuation), alphabetically. */
	public static List<String> search(String query) {
		String q = key(query == null ? "" : query);
		return names().stream()
				.filter(name -> key(name).contains(q))
				.sorted(Comparator.comparing((String name) -> !key(name).startsWith(q))
						.thenComparing(String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	/** The catalog spelling of a known technology, otherwise the input as given. */
	public static String displayName(String name) {
		return lookup(name).map(Technology::name).orElse(name.strip());
	}

	/** Display names with blanks and duplicates removed, in input order. */
	public static List<String> normalize(Collection<String> names) {
		var result = new ArrayList<String>();
		for (String raw : names) {
			if (raw == null || key(raw).isEmpty()) {
				continue;
			}
			String name = displayName(raw);
			if (result.stream().noneMatch(existing -> same(existing, name))) {
				result.add(name);
			}
		}
		return result;
	}

	/** What Enter in the picker adds: the best catalog match for the query, else the query itself. */
	public static String resolve(String query) {
		return search(query).stream().findFirst().orElse(query.strip());
	}

	private static Technology language(String name, String language, String... aliases) {
		return new Technology(name, "language:" + language, List.of(aliases));
	}

	private static Technology topic(String name, String topic, String... aliases) {
		return new Technology(name, "topic:" + topic, List.of(aliases));
	}
}
