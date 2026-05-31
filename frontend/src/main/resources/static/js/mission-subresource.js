/*
 * Mission sub-resource client helper.
 *
 * Provides a small, framework-agnostic toolbox for the mission detail page
 * to talk to the fine-grained sub-section endpoints introduced under
 * /api/v1/missions/{id}/... (Option A: fine-grained optimistic locking
 * per sub-aggregate; parent Mission.version is NOT bumped on sub-writes).
 *
 * Goals (mirrors AGENTS.md):
 *  - Multi-user collaboration: concurrent users editing different sub-panels
 *    of the same mission do not invalidate each other's in-flight forms.
 *  - Lost-update safety: every write carries the sub-aggregate's own version.
 *  - DOM version sync: after a successful write, ALL data-version attributes
 *    in the same DOM context (tr/buttons/modals) are updated atomically,
 *    otherwise follow-up actions would trigger spurious 409 conflicts.
 *  - RFC7807 aware: on 409 conflicts a KRT-styled toast is shown (never
 *    native alert/confirm) and the user is offered to reload.
 *
 * All user-visible strings are read from the window.MISSION_SUBRES_I18N
 * dictionary which is populated by mission-detail.html from Thymeleaf
 * messages (no hardcoded user-visible text here).
 */
(function () {
    'use strict';

    function i18n(key, fallback) {
        const dict = window.MISSION_SUBRES_I18N || {};
        return (dict[key] != null && dict[key] !== '') ? dict[key] : (fallback || key);
    }

    function csrfHeaders() {
        const headers = {
            'Accept': 'application/json',
            'Content-Type': 'application/json'
        };
        const token = document.querySelector('meta[name="_csrf"]')?.content;
        const header = document.querySelector('meta[name="_csrf_header"]')?.content;
        if (token && header && header !== 'undefined' && token !== 'undefined') {
            headers[header] = token;
        }
        return headers;
    }

    /**
     * Update every data-version attribute in the given container so that
     * subsequent AJAX actions on the same sub-aggregate send the fresh
     * version. Falls back to window.location.reload() if the container
     * cannot be resolved.
     *
     * @param {string|Element} containerSelector CSS selector or Element
     * @param {number|string}  newVersion        new sub-aggregate version
     */
    function syncVersion(containerSelector, newVersion) {
        if (newVersion == null) {
            return;
        }
        const container = (typeof containerSelector === 'string')
            ? document.querySelector(containerSelector)
            : containerSelector;
        if (!container) {
            return;
        }
        container.setAttribute('data-version', String(newVersion));
        container.querySelectorAll('[data-version]').forEach((el) => {
            el.setAttribute('data-version', String(newVersion));
        });
    }

    /**
     * Render a KRT-styled error toast for a RFC7807 Problem response.
     *
     * On a 409 we look at the RFC 7807 `code` extension property emitted by the backend
     * (see backend/.../GlobalExceptionHandler.java) to pick the right UX:
     *
     *  - `OPTIMISTIC_LOCK` / `PESSIMISTIC_LOCK`: stale data — offer a reload prompt via the
     *    KRT confirm modal so the user can pull in the fresh values.
     *  - Anything else (`DUPLICATE_ENTITY`, `BUSINESS_CONFLICT`, `ENTITY_IN_USE`,
     *    `DATA_INTEGRITY_VIOLATION`, …): the request is rejected because of a domain rule
     *    such as "user already in mission" or "crew slot taken". The user's input is NOT
     *    stale — they need to see *why* the save was refused, not be asked to throw their
     *    input away by reloading. Just show the localized detail in a toast.
     *
     * @param {Response}       response     fetch Response
     * @param {string}         sectionKey   e.g. 'participant', 'unit', ...
     * @param {object|string}  problem      parsed problem+json or text
     */
    async function handleProblem(response, sectionKey, problem) {
        const sectionLabel = i18n('mission.conflict.section.' + sectionKey, sectionKey);
        if (response.status === 409) {
            const problemCode = (problem && typeof problem === 'object' && problem.code)
                ? String(problem.code)
                : null;
            const isStaleData = (problemCode === 'OPTIMISTIC_LOCK'
                || problemCode === 'PESSIMISTIC_LOCK');
            if (isStaleData) {
                const title = i18n('mission.conflict.toast.title', 'Konflikt');
                const detail = (problem && problem.detail)
                    ? problem.detail
                    : i18n('mission.conflict.toast.detail', 'Bitte Seite neu laden.');
                const msg = sectionLabel + ': ' + detail;
                if (typeof window.showFrontendErrorToast === 'function') {
                    window.showFrontendErrorToast(msg);
                }
                if (typeof window.showKrtConfirm === 'function') {
                    const ok = await window.showKrtConfirm(
                        title,
                        i18n('mission.conflict.action.reload.question',
                             'Aktuelle Werte laden? Eingaben in anderen Bereichen bleiben erhalten (via Neuladen gehen sie verloren).'),
                        i18n('mission.conflict.action.reload', 'Aktuelle Werte laden'),
                        i18n('mission.conflict.action.dismiss', 'Schliessen')
                    );
                    if (ok) {
                        window.location.reload();
                    }
                }
                return;
            }
            // Domain conflict (duplicate, in-use, business-state). Surface the backend's
            // localized detail directly — never the reload prompt, because the user's input
            // is fine, the operation itself is what the server refused.
            const detail = (problem && problem.detail)
                ? problem.detail
                : i18n('mission.save.section.error', 'Speichern fehlgeschlagen.');
            if (typeof window.showFrontendErrorToast === 'function') {
                window.showFrontendErrorToast(sectionLabel + ': ' + detail);
            }
            return;
        }
        const generic = (problem && problem.detail)
            ? problem.detail
            : i18n('mission.save.section.error', 'Speichern fehlgeschlagen.');
        if (typeof window.showFrontendErrorToast === 'function') {
            window.showFrontendErrorToast(sectionLabel + ': ' + generic);
        }
    }

    /**
     * Send a write (PATCH/POST/PUT/DELETE) to a mission sub-resource.
     *
     * @param {object} opts
     * @param {string} opts.method             HTTP method (default PATCH)
     * @param {string} opts.url                target URL
     * @param {object} [opts.payload]          JSON payload (optional)
     * @param {string} opts.sectionKey         i18n section key (participant/unit/...)
     * @param {string|Element} [opts.containerSelector] DOM container for syncVersion
     * @param {function(object):void} [opts.onSuccess] success callback
     * @returns {Promise<{ok:boolean,status:number,body:any}>}
     */
    async function patchSubResource(opts) {
        const method = opts.method || 'PATCH';
        const init = {
            method: method,
            headers: csrfHeaders()
        };
        if (opts.payload !== undefined && method !== 'GET' && method !== 'DELETE') {
            init.body = JSON.stringify(opts.payload);
        }
        let response;
        try {
            response = await fetch(opts.url, init);
        } catch (e) {
            if (typeof window.showFrontendErrorToast === 'function') {
                window.showFrontendErrorToast(
                    i18n('mission.save.section.error', 'Speichern fehlgeschlagen.')
                    + ' (' + (e && e.message ? e.message : 'network') + ')'
                );
            }
            return { ok: false, status: 0, body: null };
        }

        let body;
        const contentType = response.headers.get('Content-Type') || '';
        try {
            if (contentType.indexOf('application/json') >= 0
                || contentType.indexOf('application/problem+json') >= 0) {
                body = await response.json();
            } else {
                body = await response.text();
            }
        } catch (_ignored) {
            body = null;
        }

        if (!response.ok) {
            await handleProblem(response, opts.sectionKey, body);
            return { ok: false, status: response.status, body: body };
        }

        if (opts.containerSelector && body && body.version != null) {
            syncVersion(opts.containerSelector, body.version);
        }
        if (typeof window.showFrontendSuccessToast === 'function') {
            const sectionLabel = i18n('mission.save.section.' + opts.sectionKey, opts.sectionKey);
            window.showFrontendSuccessToast(sectionLabel + ': ' + i18n('mission.save.section.ok', 'Gespeichert.'));
        }
        if (typeof opts.onSuccess === 'function') {
            try { opts.onSuccess(body); } catch (_e) { /* callback errors must not break UX */ }
        }
        return { ok: true, status: response.status, body: body };
    }

    window.MissionSubresource = {
        patchSubResource: patchSubResource,
        syncVersion: syncVersion,
        handleProblem: handleProblem
    };
})();
