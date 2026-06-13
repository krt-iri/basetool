/*
 * krt-fetch.js — the single client-side seam for frontend write requests and
 * AJAX fragment swaps (epic #571, spec REQ-FE-001..005).
 *
 * Generalizes the former mission-subresource.js (window.MissionSubresource)
 * into a mission-agnostic toolbox loaded globally from fragments/head.html, so
 * every page shares ONE implementation of:
 *
 *  - CSRF header construction (window.krtCsrf) — reads the freshest
 *    meta[name="_csrf"] / meta[name="_csrf_header"] tags, the single source of
 *    truth. Replaces the ~30 hand-rolled readers in 6 syntactic variants.
 *  - retry-on-403 — a bare 403 means the CSRF token was rejected (stale tab,
 *    post-re-login session-id rotation, maximumSessions eviction). krtFetch
 *    transparently refetches the token from GET /csrf, updates the meta tags,
 *    and retries the write exactly once before surfacing the error.
 *  - JSON / application/problem+json parsing and RFC7807 409 branching
 *    (OPTIMISTIC_LOCK / PESSIMISTIC_LOCK -> reload-confirm; domain conflict
 *    codes -> toast only), carried over verbatim from mission-subresource.js.
 *  - syncVersion — the canonical @Version propagator: on success the fresh
 *    version is written to the container AND every descendant [data-version]
 *    so the user's next action does not 409 (see the concurrency rules in
 *    CLAUDE.md).
 *  - swap — server-rendered HTML fragment swaps for lists / filters /
 *    pagination, including delegated interception of in-container pagination
 *    anchors so paging stays in-place (fixes the known full-reload regression).
 *
 * No user-visible string is hardcoded here: callers pass already-localized
 * labels/messages (the MissionSubresource alias below sources them from
 * window.MISSION_SUBRES_I18N exactly as before). The few inline fallbacks only
 * ever surface if a caller forgets to pass a message AND its i18n dictionary is
 * missing — a developer error, not a user-facing path.
 */
(function () {
    'use strict';

    /**
     * Returns value when it is a non-empty string, otherwise the fallback. Used
     * so a missing/blank localized message degrades to a neutral default instead
     * of rendering "undefined".
     */
    function text(value, fallback) {
        return value != null && value !== '' ? value : fallback;
    }

    // ---------------------------------------------------------------- krtCsrf

    function metaContent(name) {
        const el = document.querySelector('meta[name="' + name + '"]');
        const content = el ? el.getAttribute('content') : null;
        return content && content !== 'undefined' ? content : null;
    }

    function setMetaContent(name, value) {
        let el = document.querySelector('meta[name="' + name + '"]');
        if (!el) {
            el = document.createElement('meta');
            el.setAttribute('name', name);
            document.head.appendChild(el);
        }
        el.setAttribute('content', value);
    }

    function csrfToken() {
        return metaContent('_csrf');
    }

    function csrfHeaderName() {
        return metaContent('_csrf_header');
    }

    /**
     * Builds the request headers for a JSON write: Accept + Content-Type +
     * X-Requested-With, plus the CSRF header read fresh from the meta tags. Any
     * base headers passed in are merged first (the CSRF header always wins).
     */
    function csrfHeaders(base) {
        const headers = Object.assign(
            {
                Accept: 'application/json',
                'Content-Type': 'application/json',
                'X-Requested-With': 'XMLHttpRequest',
            },
            base || {},
        );
        const token = csrfToken();
        const header = csrfHeaderName();
        if (token && header) {
            headers[header] = token;
        }
        return headers;
    }

    // De-duplicates concurrent refreshes: parallel writes that all 403 share one
    // in-flight GET /csrf instead of stampeding the endpoint.
    let refreshInFlight = null;

    /**
     * Refetches the CSRF token from GET /csrf, writes it back into the meta tags
     * (the single source of truth every subsequent krtCsrf.headers() call reads),
     * and resolves to { headerName, token } — or null if the refresh failed (e.g.
     * the session is gone and the endpoint 403s/redirects).
     */
    function refreshCsrf() {
        if (refreshInFlight) {
            return refreshInFlight;
        }
        refreshInFlight = fetch('/csrf', {
            headers: { Accept: 'application/json', 'X-Requested-With': 'XMLHttpRequest' },
        })
            .then(function (res) {
                return res.ok ? res.json() : null;
            })
            .then(function (data) {
                if (data && data.token && data.headerName) {
                    setMetaContent('_csrf', data.token);
                    setMetaContent('_csrf_header', data.headerName);
                    return data;
                }
                return null;
            })
            .catch(function () {
                return null;
            })
            .finally(function () {
                refreshInFlight = null;
            });
        return refreshInFlight;
    }

    window.krtCsrf = {
        token: csrfToken,
        headerName: csrfHeaderName,
        headers: csrfHeaders,
        refresh: refreshCsrf,
    };

    // --------------------------------------------------------------- helpers

    /**
     * Writes newVersion to the container and to every descendant carrying a
     * [data-version] attribute so the next AJAX action on the same aggregate
     * sends the fresh version. No-op when newVersion is null or the container
     * cannot be resolved.
     */
    function syncVersion(containerSelector, newVersion) {
        if (newVersion == null) {
            return;
        }
        const container =
            typeof containerSelector === 'string'
                ? document.querySelector(containerSelector)
                : containerSelector;
        if (!container) {
            return;
        }
        container.setAttribute('data-version', String(newVersion));
        container.querySelectorAll('[data-version]').forEach(function (el) {
            el.setAttribute('data-version', String(newVersion));
        });
    }

    function errorToast(message) {
        if (typeof window.showFrontendErrorToast === 'function') {
            window.showFrontendErrorToast(message);
        }
    }

    function successToast(message) {
        if (typeof window.showFrontendSuccessToast === 'function') {
            window.showFrontendSuccessToast(message);
        }
    }

    /**
     * Renders the KRT-styled feedback for a non-ok response.
     *
     * On 409 the RFC 7807 `code` extension decides the UX:
     *  - OPTIMISTIC_LOCK / PESSIMISTIC_LOCK -> the user's view is stale; show the
     *    error toast and offer a reload via showKrtConfirm.
     *  - any other code (DUPLICATE_ENTITY, BUSINESS_CONFLICT, ...) -> a domain
     *    rule refused the operation; the input is fine, so just show why (no
     *    reload prompt).
     *
     * opts carries the already-localized strings: conflictSectionLabel (error
     * prefix), errorMessage (generic fallback), and conflict.{title,reloadLabel,
     * dismissLabel,reloadQuestion,reloadDetailFallback}.
     */
    async function handleProblem(response, problem, opts) {
        const options = opts || {};
        const prefix = options.conflictSectionLabel ? options.conflictSectionLabel + ': ' : '';
        const genericError = text(options.errorMessage, 'Speichern fehlgeschlagen.');
        if (response.status === 409) {
            const code =
                problem && typeof problem === 'object' && problem.code
                    ? String(problem.code)
                    : null;
            const stale = code === 'OPTIMISTIC_LOCK' || code === 'PESSIMISTIC_LOCK';
            if (stale) {
                const conflict = options.conflict || {};
                const detail =
                    problem && problem.detail
                        ? problem.detail
                        : text(conflict.reloadDetailFallback, 'Bitte Seite neu laden.');
                errorToast(prefix + detail);
                if (typeof window.showKrtConfirm === 'function') {
                    const ok = await window.showKrtConfirm(
                        text(conflict.title, 'Konflikt'),
                        text(conflict.reloadQuestion, 'Aktuelle Werte laden?'),
                        text(conflict.reloadLabel, 'Aktuelle Werte laden'),
                        text(conflict.dismissLabel, 'Schliessen'),
                    );
                    if (ok) {
                        window.location.reload();
                    }
                }
                return;
            }
            const domainDetail = problem && problem.detail ? problem.detail : genericError;
            errorToast(prefix + domainDetail);
            return;
        }
        const generic = problem && problem.detail ? problem.detail : genericError;
        errorToast(prefix + generic);
    }

    async function parseBody(response) {
        const contentType = response.headers.get('Content-Type') || '';
        try {
            if (
                contentType.indexOf('application/json') >= 0 ||
                contentType.indexOf('application/problem+json') >= 0
            ) {
                return await response.json();
            }
            return await response.text();
        } catch (_ignored) {
            return null;
        }
    }

    /**
     * Sends a write (PATCH/POST/PUT/DELETE) as JSON and handles the response.
     *
     * opts:
     *  - method               HTTP method (default PATCH)
     *  - url                  target URL
     *  - payload              JSON payload (omitted for GET/DELETE)
     *  - containerSelector    DOM container/selector for syncVersion on success
     *  - sectionLabel         already-localized success-toast prefix (optional)
     *  - successMessage       already-localized success text (default "Gespeichert.")
     *  - toast                set false to suppress the success toast
     *  - conflictSectionLabel already-localized error/conflict prefix (optional)
     *  - errorMessage         already-localized generic error text
     *  - conflict             localized conflict strings (see handleProblem)
     *  - onSuccess            callback(body) run after a 2xx
     *
     * Returns { ok, status, body }. On a bare 403 the CSRF token is refreshed
     * from GET /csrf and the request retried exactly once before failing.
     */
    async function write(opts) {
        const method = opts.method || 'PATCH';

        function buildInit() {
            const init = { method: method, headers: csrfHeaders() };
            if (opts.payload !== undefined && method !== 'GET' && method !== 'DELETE') {
                init.body = JSON.stringify(opts.payload);
            }
            return init;
        }

        let response;
        try {
            response = await fetch(opts.url, buildInit());
            // A bare 403 is the CSRF filter rejecting a stale token (it answers
            // before GlobalExceptionHandler, so the body is not problem+json).
            // Refresh the token once and retry; a genuine authorization failure
            // either redirects (302) or 403s again after the refetch and surfaces.
            if (response.status === 403) {
                const refreshed = await refreshCsrf();
                if (refreshed) {
                    response = await fetch(opts.url, buildInit());
                }
            }
        } catch (networkError) {
            errorToast(
                text(opts.errorMessage, 'Speichern fehlgeschlagen.') +
                    ' (' +
                    (networkError && networkError.message ? networkError.message : 'network') +
                    ')',
            );
            return { ok: false, status: 0, body: null };
        }

        const body = await parseBody(response);

        if (!response.ok) {
            await handleProblem(response, body, opts);
            return { ok: false, status: response.status, body: body };
        }

        if (opts.containerSelector && body && body.version != null) {
            syncVersion(opts.containerSelector, body.version);
        }
        if (opts.toast !== false) {
            const label = opts.sectionLabel ? opts.sectionLabel + ': ' : '';
            successToast(label + text(opts.successMessage, 'Gespeichert.'));
        }
        if (typeof opts.onSuccess === 'function') {
            try {
                opts.onSuccess(body);
            } catch (_callbackError) {
                /* a success callback must never break the UX */
            }
        }
        return { ok: true, status: response.status, body: body };
    }

    // ------------------------------------------------------------ fragment swap

    /**
     * Ensures the fragment query parameter (default fragment=results) is present
     * on url so the controller returns the results fragment rather than the full
     * page. Resolves relative URLs against the current origin and returns a
     * same-origin path+query string.
     */
    function withFragmentParam(url, paramName, paramValue) {
        const resolved = new URL(url, window.location.origin);
        resolved.searchParams.set(paramName, paramValue);
        return resolved.pathname + '?' + resolved.searchParams.toString();
    }

    /**
     * Strips the internal fragment query parameter and returns the user-facing
     * same-origin path+query — the URL shown in the address bar after a swap, so a
     * refresh or a copied deep-link re-renders the same filtered / paged state
     * server-side.
     */
    function withoutFragmentParam(url, paramName) {
        const resolved = new URL(url, window.location.origin);
        resolved.searchParams.delete(paramName);
        const query = resolved.searchParams.toString();
        return resolved.pathname + (query ? '?' + query : '');
    }

    /**
     * Loads a server-rendered HTML fragment and swaps it into a container.
     *
     * opts:
     *  - url             source URL (fragment param is added if missing)
     *  - container       target element or selector
     *  - indicator       optional loading element/selector toggled during the fetch
     *  - fragmentParam   query param name (default "fragment")
     *  - fragmentValue   query param value (default "results")
     *  - history         when true, the address-bar URL is kept in sync via
     *                    history.replaceState (minus the internal fragment param)
     *                    so a refresh / deep-link re-renders the same state. We use
     *                    replaceState, not pushState, so a debounced filter does not
     *                    flood the back-stack with intermediate keystrokes.
     *  - preserveScroll  unless false, the window scroll position is restored after
     *                    the swap so paging/filtering does not jump the page.
     *
     * After the swap, a single delegated click listener is installed on the
     * container so in-container pagination/sort anchors (a.page-btn[href] and
     * any opted-in a[data-swap][href]) re-swap in place instead of navigating —
     * fixing the regression where pagination inside an AJAX results container
     * triggered a full page reload.
     */
    function swap(opts) {
        const container =
            typeof opts.container === 'string'
                ? document.querySelector(opts.container)
                : opts.container;
        if (!container) {
            return Promise.resolve(false);
        }
        const indicator =
            typeof opts.indicator === 'string'
                ? document.querySelector(opts.indicator)
                : opts.indicator;
        const paramName = opts.fragmentParam || 'fragment';
        const paramValue = opts.fragmentValue || 'results';
        const url = withFragmentParam(opts.url, paramName, paramValue);
        const scrollY = window.scrollY;
        if (indicator) {
            indicator.style.display = 'block';
        }
        return fetch(url, { headers: { 'X-Requested-With': 'XMLHttpRequest' } })
            .then(function (res) {
                return res.text();
            })
            .then(function (html) {
                container.innerHTML = html;
                if (indicator) {
                    indicator.style.display = 'none';
                }
                bindSwapAnchorInterception(container, opts);
                // Let page/global enhancers re-process the freshly swapped subtree
                // (e.g. the .utc-time localiser in sidebar.html). A one-shot
                // DOMContentLoaded enhancer would otherwise miss swapped-in content.
                document.dispatchEvent(
                    new CustomEvent('krt:swapped', { detail: { container: container } }),
                );
                if (opts.history) {
                    window.history.replaceState(
                        window.history.state,
                        '',
                        withoutFragmentParam(opts.url, paramName),
                    );
                }
                if (opts.preserveScroll !== false) {
                    window.scrollTo(0, scrollY);
                }
                return true;
            })
            .catch(function () {
                if (indicator) {
                    indicator.style.display = 'none';
                }
                return false;
            });
    }

    function bindSwapAnchorInterception(container, opts) {
        if (container._krtSwapBound) {
            return;
        }
        container._krtSwapBound = true;
        container.addEventListener('click', function (event) {
            const anchor = event.target.closest('a.page-btn[href], a[data-swap][href]');
            if (!anchor || !container.contains(anchor)) {
                return;
            }
            // A disabled page-btn renders without an href, but guard anyway so a
            // CSS-only ".disabled" never triggers a wasted swap.
            if (anchor.classList.contains('disabled')) {
                event.preventDefault();
                return;
            }
            event.preventDefault();
            swap(Object.assign({}, opts, { url: anchor.getAttribute('href') }));
        });
    }

    window.krtFetch = {
        write: write,
        swap: swap,
        syncVersion: syncVersion,
        handleProblem: handleProblem,
        csrf: window.krtCsrf,
    };

    // ----------------------------------------------- MissionSubresource alias

    // Thin backward-compatibility shim so mission-detail.html keeps working
    // unchanged (it is migrated to call krtFetch directly in the Missions child,
    // #574). All mission-specific user-visible strings are sourced from
    // window.MISSION_SUBRES_I18N here, preserving the exact prior behaviour.
    function missionI18n(key, fallback) {
        const dict = window.MISSION_SUBRES_I18N || {};
        return text(dict[key], fallback);
    }

    function missionConflict() {
        return {
            title: missionI18n('mission.conflict.toast.title', 'Konflikt'),
            reloadLabel: missionI18n('mission.conflict.action.reload', 'Aktuelle Werte laden'),
            dismissLabel: missionI18n('mission.conflict.action.dismiss', 'Schliessen'),
            reloadQuestion: missionI18n(
                'mission.conflict.action.reload.question',
                'Aktuelle Werte laden? Eingaben in anderen Bereichen bleiben erhalten (via Neuladen' +
                    ' gehen sie verloren).',
            ),
            reloadDetailFallback: missionI18n(
                'mission.conflict.toast.detail',
                'Bitte Seite neu laden.',
            ),
        };
    }

    window.MissionSubresource = {
        patchSubResource: function (opts) {
            const key = opts.sectionKey;
            return write(
                Object.assign({}, opts, {
                    sectionLabel: missionI18n('mission.save.section.' + key, key),
                    conflictSectionLabel: missionI18n('mission.conflict.section.' + key, key),
                    successMessage: missionI18n('mission.save.section.ok', 'Gespeichert.'),
                    errorMessage: missionI18n(
                        'mission.save.section.error',
                        'Speichern fehlgeschlagen.',
                    ),
                    conflict: missionConflict(),
                }),
            );
        },
        syncVersion: syncVersion,
        handleProblem: function (response, sectionKey, problem) {
            return handleProblem(response, problem, {
                conflictSectionLabel: missionI18n(
                    'mission.conflict.section.' + sectionKey,
                    sectionKey,
                ),
                errorMessage: missionI18n(
                    'mission.save.section.error',
                    'Speichern fehlgeschlagen.',
                ),
                conflict: missionConflict(),
            });
        },
    };
})();
