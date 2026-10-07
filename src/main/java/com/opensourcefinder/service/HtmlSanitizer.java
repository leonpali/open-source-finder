package com.opensourcefinder.service;

import com.opensourcefinder.model.Repository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

/**
 * Makes HTML rendered by GitHub (READMEs, issue bodies) safe to embed in the page, and points repo-relative links and
 * images back at GitHub.
 */
public final class HtmlSanitizer {

	private static final Safelist SAFELIST = Safelist.relaxed()
			.addAttributes("a", "target", "rel")
			.preserveRelativeLinks(true);

	private HtmlSanitizer() {
	}

	public static String sanitize(String html, Repository repo) {
		String blobBase = repo.url() + "/blob/HEAD/";
		String rawBase = "https://raw.githubusercontent.com/" + repo.fullName() + "/HEAD/";

		Document doc = Jsoup.parse(html, blobBase);
		doc.select("img[src]").forEach(img -> {
			String src = img.attr("src");
			if (src.startsWith("/") && !src.startsWith("//")) {
				img.attr("src", "https://github.com" + src);
			}
			else if (!src.matches("(?i)^(https?:|data:|//).*")) {
				img.attr("src", rawBase + src);
			}
		});
		doc.select("a[href]").forEach(a -> {
			if (a.attr("href").startsWith("#")) {
				return;
			}
			a.attr("href", a.absUrl("href"));
			a.attr("target", "_blank");
			a.attr("rel", "noopener");
		});

		return Jsoup.clean(doc.body().html(), SAFELIST);
	}
}
