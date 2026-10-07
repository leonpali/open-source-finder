package com.opensourcefinder.service;

import java.lang.Character.UnicodeScript;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * A cheap heuristic for "is this text written in English?". GitHub's search can't filter by the language of
 * descriptions or issues, so results are checked after they arrive.
 * <p>
 * Text that is too short to judge counts as English, so terse but valid issues aren't dropped. Bilingual text (e.g. an
 * English description followed by a Chinese translation) counts as English as long as at least half of it is.
 */
public final class EnglishText {

	/** Share of the words that must be Latin script. */
	static final double MIN_LATIN_WORD_SHARE = 0.5;

	/** Chinese and Japanese are written without spaces; this many characters count as one word. */
	static final double CJK_CHARS_PER_WORD = 1.5;

	/** Below this many Latin words, only the script is checked. */
	static final int MIN_WORDS_FOR_STOP_WORD_CHECK = 20;

	/** English prose is typically 30–50% stop words; terse technical writing still clears this easily. */
	static final double MIN_STOP_WORD_SHARE = 0.08;

	private static final Set<String> STOP_WORDS = Set.of(
			"a", "an", "and", "are", "as", "at", "be", "but", "by", "can", "do", "does", "for", "from", "has", "have",
			"how", "i", "if", "in", "is", "it", "its", "not", "of", "on", "or", "should", "so", "that", "the", "there",
			"this", "to", "was", "we", "when", "which", "will", "with", "would", "you");

	private static final Set<UnicodeScript> CJK = Set.of(UnicodeScript.HAN, UnicodeScript.HIRAGANA,
			UnicodeScript.KATAKANA);

	private EnglishText() {
	}

	public static boolean isLikelyEnglish(String text) {
		if (text == null || text.codePoints().filter(Character::isLetter).count() < 3) {
			return true;
		}

		var latinWords = new ArrayList<String>();
		double otherWords = 0;
		var word = new StringBuilder();
		UnicodeScript wordScript = null;

		for (int cp : text.codePoints().toArray()) {
			UnicodeScript script = Character.isLetter(cp) ? UnicodeScript.of(cp) : null;
			if (script != null && CJK.contains(script)) {
				otherWords += 1 / CJK_CHARS_PER_WORD;
				script = null; // also ends a word in progress
			}
			if (script != wordScript && !word.isEmpty()) {
				if (wordScript == UnicodeScript.LATIN) {
					latinWords.add(word.toString());
				}
				else {
					otherWords++;
				}
				word.setLength(0);
			}
			wordScript = script;
			if (script != null) {
				word.appendCodePoint(cp);
			}
		}
		if (!word.isEmpty()) {
			if (wordScript == UnicodeScript.LATIN) {
				latinWords.add(word.toString());
			}
			else {
				otherWords++;
			}
		}

		if (latinWords.size() / (latinWords.size() + otherWords) < MIN_LATIN_WORD_SHARE) {
			return false;
		}
		return latinWords.size() < MIN_WORDS_FOR_STOP_WORD_CHECK || stopWordShare(latinWords) >= MIN_STOP_WORD_SHARE;
	}

	/** Checks the prose of rendered HTML; code blocks are ignored since code is "English" in any language. */
	public static boolean isLikelyEnglishHtml(String html) {
		if (html == null) {
			return true;
		}
		Document doc = Jsoup.parse(html);
		doc.select("pre, code").remove();
		return isLikelyEnglish(doc.text());
	}

	private static double stopWordShare(List<String> words) {
		long stopWords = words.stream().filter(w -> STOP_WORDS.contains(w.toLowerCase(Locale.ROOT))).count();
		return (double) stopWords / words.size();
	}
}
