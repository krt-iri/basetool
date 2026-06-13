(function () {
    'use strict';

    const form = document.getElementById('bank-audit-filter-form');
    const resultsContainer = document.getElementById('bank-audit-results');
    const resetLink = document.getElementById('bank-audit-filter-reset');

    // krtFetch (fragments/head.html) owns the fragment swap + in-results pagination
    // interception + URL sync, so filtering and paging the audit log stay in place.
    if (!form || !resultsContainer || !window.krtFetch) return;

    let debounceTimer = null;

    function buildUrl() {
        // The split datetime widget keeps the hidden from/to inputs in sync, so a plain
        // FormData serialisation already carries the canonical filter values.
        const data = new FormData(form);
        const params = new URLSearchParams();
        for (const [key, value] of data.entries()) {
            if (value !== '') params.append(key, value);
        }
        const query = params.toString();
        return '/admin/bank-audit' + (query ? '?' + query : '');
    }

    function loadResults(url) {
        window.krtFetch.swap({
            url: url || buildUrl(),
            container: resultsContainer,
            history: true,
        });
    }

    function onFilterChange() {
        clearTimeout(debounceTimer);
        debounceTimer = setTimeout(function () {
            loadResults();
        }, 300);
    }

    form.addEventListener('submit', function (event) {
        event.preventDefault();
        clearTimeout(debounceTimer);
        loadResults();
    });

    form.querySelectorAll('input, select').forEach(function (el) {
        el.addEventListener('input', onFilterChange);
        el.addEventListener('change', onFilterChange);
    });

    if (resetLink) {
        resetLink.addEventListener('click', function (event) {
            event.preventDefault();
            form.querySelectorAll('input').forEach(function (el) {
                el.value = '';
            });
            form.querySelectorAll('select').forEach(function (el) {
                el.selectedIndex = 0;
            });
            loadResults('/admin/bank-audit');
        });
    }
})();
