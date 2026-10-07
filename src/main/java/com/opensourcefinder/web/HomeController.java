package com.opensourcefinder.web;

import java.util.ArrayList;
import java.util.List;

import com.opensourcefinder.model.Repository;
import com.opensourcefinder.service.ReadmeService;
import com.opensourcefinder.service.RepositoryService;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/**
 * Serves the single page and the fragments htmx swaps into it. Every fragment is part of {@code index.html}, so a
 * full page load and an htmx update render the exact same markup.
 */
@Controller
public class HomeController {

	private final RepositoryService repositories;
	private final ReadmeService readmes;

	public HomeController(RepositoryService repositories, ReadmeService readmes) {
		this.repositories = repositories;
		this.readmes = readmes;
	}

	/**
	 * The full page. htmx requests also hit this endpoint and pick the parts they need out of the response.
	 *
	 * @param q text typed into the technology picker and submitted with Enter; it is resolved and added to the filter
	 * @param repo {@code owner/name} of a repository whose detail view should be open
	 */
	@GetMapping("/")
	public String index(@RequestParam(name = "tech", defaultValue = "") List<String> tech,
			@RequestParam(required = false) String q,
			@RequestParam(required = false) String repo,
			@RequestHeader(name = "HX-Request", required = false) String htmxRequest,
			HttpServletResponse response, Model model) {

		var selected = new ArrayList<>(tech);
		if (q != null && !q.isBlank()) {
			selected.add(repositories.resolveTechnology(q));
		}
		var filter = filter(selected);

		// Keep "q" out of the address bar: redirect plain requests, tell htmx which URL to push.
		if (q != null) {
			if (htmxRequest == null) {
				return "redirect:" + filter.url();
			}
			response.setHeader("HX-Push-Url", filter.url());
		}

		List<Repository> matches = repositories.findByTechnologies(filter.selected());
		model.addAttribute("filter", filter);
		model.addAttribute("repositories", matches);
		model.addAttribute("totalCount", repositories.findAll().size());
		model.addAttribute("technologyOptions", repositories.availableTechnologies());

		if (repo != null && repo.contains("/")) {
			String[] parts = repo.split("/", 2);
			repositories.find(parts[0], parts[1]).ifPresent(r -> model.addAttribute("selectedRepo", r));
		}
		return "index";
	}

	/** Technology picker options matching what has been typed so far. */
	@GetMapping("/technologies")
	public String technologies(@RequestParam(name = "tech", defaultValue = "") List<String> tech,
			@RequestParam(defaultValue = "") String q, Model model) {
		model.addAttribute("filter", filter(tech));
		model.addAttribute("technologyOptions", repositories.searchTechnologies(q));
		model.addAttribute("query", q.strip());
		return "index :: techState";
	}

	/** Contents of the detail dialog. The README is loaded separately so the dialog opens immediately. */
	@GetMapping("/repos/{owner}/{name}")
	public String detail(@PathVariable String owner, @PathVariable String name,
			@RequestParam(name = "tech", defaultValue = "") List<String> tech, Model model) {
		model.addAttribute("filter", filter(tech));
		model.addAttribute("selectedRepo", findOr404(owner, name));
		return "index :: detail";
	}

	@GetMapping("/repos/{owner}/{name}/readme")
	public String readme(@PathVariable String owner, @PathVariable String name, Model model) {
		Repository repo = findOr404(owner, name);
		model.addAttribute("selectedRepo", repo);
		readmes.findReadmeHtml(repo).ifPresent(html -> model.addAttribute("readmeHtml", html));
		return "index :: readme";
	}

	private FilterState filter(List<String> technologies) {
		return new FilterState(repositories.normalizeTechnologies(technologies));
	}

	private Repository findOr404(String owner, String name) {
		return repositories.find(owner, name)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown repository"));
	}
}
