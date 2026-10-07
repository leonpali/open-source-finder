package com.opensourcefinder.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import com.opensourcefinder.service.TechnologyCatalog.Technology;
import org.junit.jupiter.api.Test;

class TechnologyCatalogTests {

	@Test
	void comparesNamesIgnoringCaseAndPunctuation() {
		assertThat(TechnologyCatalog.same("Spring Boot", "spring-boot")).isTrue();
		assertThat(TechnologyCatalog.same("Node.js", "nodejs")).isTrue();
		assertThat(TechnologyCatalog.same("C", "C++")).isFalse();
		assertThat(TechnologyCatalog.same("C#", "C++")).isFalse();
	}

	@Test
	void looksUpByNameQualifierOrAlias() {
		assertThat(TechnologyCatalog.lookup("golang")).map(Technology::name).contains("Go");
		assertThat(TechnologyCatalog.lookup("cpp")).map(Technology::name).contains("C++");
		assertThat(TechnologyCatalog.lookup("csharp")).map(Technology::name).contains("C#");
		assertThat(TechnologyCatalog.lookup("reactjs")).map(Technology::qualifier).contains("topic:react");
		assertThat(TechnologyCatalog.lookup("Zig")).isEmpty();
	}

	@Test
	void searchListsPrefixMatchesFirst() {
		assertThat(TechnologyCatalog.search("ja")).containsExactly("Java", "JavaScript", "Django");
		assertThat(TechnologyCatalog.search("script")).containsExactly("JavaScript", "TypeScript");
		assertThat(TechnologyCatalog.search("r")).startsWith("Rails", "React", "Ruby", "Rust");
		assertThat(TechnologyCatalog.search("")).isEqualTo(TechnologyCatalog.names());
	}

	@Test
	void namesAreSortedAndUnique() {
		List<String> names = TechnologyCatalog.names();
		assertThat(names).doesNotHaveDuplicates();
		assertThat(names).isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
	}

	@Test
	void normalizesToCatalogSpellingWithoutDuplicates() {
		assertThat(TechnologyCatalog.normalize(Arrays.asList("java", "JAVA", "golang", "Go", "", " ", null, "Zig ")))
				.containsExactly("Java", "Go", "Zig");
	}

	@Test
	void resolvesQueryToBestMatchOrItself() {
		assertThat(TechnologyCatalog.resolve("pyt")).isEqualTo("Python");
		assertThat(TechnologyCatalog.resolve(" Zig ")).isEqualTo("Zig");
	}
}
