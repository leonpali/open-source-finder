// UI behaviour only. Filtering, data and the README come from the server via htmx (see index.html).
(() => {
	'use strict';

	const picker = document.getElementById('tech-picker');
	const search = document.getElementById('tech-search');
	const dialog = document.getElementById('detail');

	// ---------- Technology picker ----------

	picker.addEventListener('toggle', () => {
		if (picker.open) search.focus();
	});

	document.addEventListener('click', (e) => {
		if (picker.open && !picker.contains(e.target)) picker.open = false;
	});

	document.addEventListener('keydown', (e) => {
		if (e.key === 'Escape' && picker.open) {
			picker.open = false;
			picker.querySelector('summary').focus();
		}
	});

	// After Enter adds a technology, clear the search so the full option list shows again.
	document.body.addEventListener('htmx:afterRequest', (e) => {
		if (e.detail.elt.id === 'tech-form' && e.detail.successful) search.value = '';
	});

	// ---------- Detail dialog ----------

	// Set while we close the dialog ourselves, so closing doesn't rewrite the URL.
	let syncing = false;

	const content = () => document.getElementById('detail-content');

	function showDetail() {
		syncing = true;
		if (dialog.open) dialog.close(); // may be open non-modally from the server or a history snapshot
		dialog.showModal();
		syncing = false;
		dialog.scrollTop = 0;
	}

	function hideDetail() {
		syncing = true;
		if (dialog.open) dialog.close();
		syncing = false;
	}

	// Runs on page load and whenever htmx inserts content: open the dialog once its content has arrived.
	htmx.onLoad((elt) => {
		if (elt === document.body || elt.id === 'detail-content') {
			if (content().dataset.repo) showDetail();
		}
	});

	// Closing removes ?repo= from the URL, keeping the current filter.
	dialog.addEventListener('close', () => {
		if (!syncing) history.replaceState(history.state, '', content().dataset.closeUrl);
	});

	dialog.addEventListener('click', (e) => {
		if (e.target === dialog) dialog.close(); // backdrop click
	});

	// Back/forward: htmx restores a snapshot of the page, which may contain stale dialog state.
	document.body.addEventListener('htmx:historyRestore', () => {
		const repo = new URLSearchParams(location.search).get('repo');
		if (repo && content().dataset.repo === repo) showDetail();
		else hideDetail();
	});
})();
