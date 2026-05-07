(function () {
    'use strict';

    const form = document.getElementById('missions-filter-form');
    const resultsContainer = document.getElementById('missions-results');
    const loadingIndicator = document.getElementById('missions-loading-indicator');
    const resetBtn = document.getElementById('missions-filter-reset');

    if (!form || !resultsContainer) return;

    let debounceTimer = null;

    function buildQueryString() {
        const data = new FormData(form);
        const params = new URLSearchParams();
        for (const [key, value] of data.entries()) {
            if (value !== '') params.append(key, value);
        }
        params.set('fragment', 'results');
        return params.toString();
    }

    function loadResults() {
        if (loadingIndicator) loadingIndicator.style.display = 'block';
        fetch('/missions?' + buildQueryString(), {
            headers: { 'X-Requested-With': 'XMLHttpRequest' }
        })
            .then(function (res) { return res.text(); })
            .then(function (html) {
                resultsContainer.innerHTML = html;
                if (loadingIndicator) loadingIndicator.style.display = 'none';
            })
            .catch(function () {
                if (loadingIndicator) loadingIndicator.style.display = 'none';
            });
    }

    function onFilterChange() {
        clearTimeout(debounceTimer);
        debounceTimer = setTimeout(loadResults, 300);
    }

    form.querySelectorAll('input, select').forEach(function (el) {
        el.addEventListener('input', onFilterChange);
        el.addEventListener('change', onFilterChange);
    });

    if (resetBtn) {
        resetBtn.addEventListener('click', function () {
            form.querySelectorAll('input[type="text"], input[type="hidden"], input[type="date"], input[type="time"]').forEach(function (el) {
                el.value = '';
            });
            form.querySelectorAll('input[type="checkbox"]').forEach(function (el) {
                el.checked = false;
            });
            loadResults();
        });
    }
})();
