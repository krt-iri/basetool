/* exported krtSearchableSelect */
/*
 * KRT searchable select (combobox).
 *
 * Progressively enhances a native <select> into a type-to-filter dropdown that
 * keeps the DAS KARTELL HUD look. The original <select> is the data source and
 * the no-JS fallback; after enhancement a hidden <input> carries the value, so
 * the surrounding page keeps working unchanged:
 *
 *   - the hidden input inherits the select's `name` (form submission) and its
 *     `data-role` (existing change-delegation + dependent loaders read it);
 *   - selecting an option dispatches a bubbling `change` on the hidden input,
 *     exactly as a native <select> would;
 *   - `required` is mirrored onto the visible textbox and a custom validity
 *     message is set while the typed text matches no option, so the browser's
 *     own constraint-validation bubble keeps gating submit at the right field.
 *
 * Follows the WAI-ARIA editable-combobox-with-list-autocomplete pattern
 * (role=combobox textbox + role=listbox popup, aria-activedescendant, keyboard
 * navigation). Reuses the design tokens / option styling defined in styles.css
 * (.krt-combobox*).
 */
(function () {
    'use strict';

    // Monotonic counter for collision-free ARIA ids across every combobox on a page.
    let comboboxSeq = 0;

    /**
     * Locates the <label> describing a control: first via an explicit
     * `for="<id>"`, then by falling back to a label inside the same .form-group
     * (the item-line markup uses an unbound previous-sibling label). Ensures the
     * label carries an id so it can be referenced via aria-labelledby.
     *
     * @param {HTMLElement} select the control whose label is wanted
     * @param {string} uid the instance id used to mint a label id when missing
     * @returns {HTMLElement|null} the label element (with an id), or null
     */
    function findLabel(select, uid) {
        let label = null;
        if (select.id) {
            label = document.querySelector('label[for="' + select.id + '"]');
        }
        if (!label) {
            const group = select.closest('.form-group');
            if (group) {
                label = group.querySelector('label');
            }
        }
        if (label && !label.id) {
            label.id = uid + '-label';
        }
        return label && label.id ? label : null;
    }

    /**
     * Appends an option label to a list element, wrapping the first
     * case-insensitive occurrence of the query in a <mark> for emphasis. Built
     * from text nodes so the (backend-supplied) label is never parsed as HTML.
     *
     * @param {HTMLElement} el the list item to fill
     * @param {string} label the full option label
     * @param {string} query the lower-cased filter text (may be empty)
     */
    function appendHighlighted(el, label, query) {
        const at = query ? label.toLowerCase().indexOf(query) : -1;
        if (at < 0) {
            el.appendChild(document.createTextNode(label));
            return;
        }
        el.appendChild(document.createTextNode(label.slice(0, at)));
        const mark = document.createElement('mark');
        mark.textContent = label.slice(at, at + query.length);
        el.appendChild(mark);
        el.appendChild(document.createTextNode(label.slice(at + query.length)));
    }

    /**
     * Enhances a native <select> in place into a searchable combobox. Safe to
     * call once per control; a no-op on a non-select or an already-enhanced one.
     *
     * @param {HTMLSelectElement} select the select to upgrade
     * @param {Object} [config] optional text/behaviour overrides; each text key also has a
     *     `data-combobox-*` attribute fallback on the select:
     *     `placeholder`, `noResultsText`, `hintText` (shown when the result list
     *     is capped), `invalidText` (custom validity for unmatched text),
     *     `loadingText` (shown while a remote fetch is in flight), `maxResults`
     *     (render cap, default 50) and `remoteSource` — an optional
     *     `(query) => Promise<Array<{value,label}>>` that, when supplied, makes the
     *     combobox fetch its options from the backend on demand (debounced) instead
     *     of filtering a preloaded static list (no data-attribute fallback).
     */
    function krtSearchableSelect(select, config) {
        if (!select || select.tagName !== 'SELECT') {
            return;
        }
        if (select.dataset.krtComboboxDone === 'true') {
            return;
        }
        select.dataset.krtComboboxDone = 'true';

        const opts = config || {};
        const data = select.dataset;
        const texts = {
            noResults: opts.noResultsText || data.comboboxNoResults || 'No matches',
            hint: opts.hintText || data.comboboxHint || '',
            invalid: opts.invalidText || data.comboboxInvalid || '',
            loading: opts.loadingText || data.comboboxLoading || '',
        };
        // Optional remote (backend-backed) option source: a function (query) -> Promise<[{value,
        // label}]>. When supplied the combobox fetches its options on demand instead of filtering a
        // preloaded static list, so it scales to catalogues far larger than one page can hold.
        const remoteSource = typeof opts.remoteSource === 'function' ? opts.remoteSource : null;
        const maxResults = Math.max(
            1,
            parseInt(opts.maxResults || data.comboboxMax || '50', 10) || 50,
        );

        // Harvest the option set; the empty-value option (if any) seeds the placeholder. In remote
        // mode only a seeded preselected option is harvested (edit mode); the rest arrives per fetch.
        let items = [];
        let placeholder = opts.placeholder || data.comboboxPlaceholder || '';
        Array.prototype.forEach.call(select.options, function (option) {
            if (option.value === '') {
                if (!placeholder) {
                    placeholder = option.textContent.trim();
                }
                return;
            }
            items.push({ value: option.value, label: option.textContent.trim() });
        });

        const uid = 'krt-cb-' + ++comboboxSeq;
        const listboxId = uid + '-list';

        const wrapper = document.createElement('div');
        wrapper.className = 'krt-combobox';

        const hidden = document.createElement('input');
        hidden.type = 'hidden';
        if (select.name) {
            hidden.name = select.name;
        }
        if (data.role) {
            hidden.dataset.role = data.role;
        }

        const input = document.createElement('input');
        input.type = 'text';
        input.id = uid + '-input';
        input.className = 'krt-combobox__input';
        input.setAttribute('role', 'combobox');
        input.setAttribute('aria-autocomplete', 'list');
        input.setAttribute('aria-expanded', 'false');
        input.setAttribute('aria-controls', listboxId);
        input.setAttribute('autocomplete', 'off');
        input.setAttribute('autocapitalize', 'none');
        input.setAttribute('spellcheck', 'false');
        if (placeholder) {
            input.placeholder = placeholder;
        }
        if (select.required) {
            input.required = true;
        }
        if (select.disabled) {
            input.disabled = true;
            hidden.disabled = true;
        }
        if (data.testid) {
            input.dataset.testid = data.testid;
        }
        const labelEl = findLabel(select, uid);
        if (labelEl) {
            input.setAttribute('aria-labelledby', labelEl.id);
        }

        const listbox = document.createElement('ul');
        listbox.id = listboxId;
        listbox.className = 'krt-combobox__listbox';
        listbox.setAttribute('role', 'listbox');
        listbox.hidden = true;

        wrapper.appendChild(hidden);
        wrapper.appendChild(input);
        wrapper.appendChild(listbox);

        // Seed the display from a preselected value (edit mode / adopted sub-assembly).
        let committedLabel = '';
        const preselected = items.find(function (it) {
            return it.value === select.value;
        });
        if (preselected) {
            committedLabel = preselected.label;
            hidden.value = preselected.value;
            input.value = committedLabel;
        }

        select.parentNode.replaceChild(wrapper, select);

        // ---- per-instance state + behaviour ---------------------------------
        let rendered = [];
        let activeIndex = -1;
        let remoteSeq = 0;
        let remoteTimer = null;

        function isOpen() {
            return listbox.hidden === false;
        }

        function noticeRow(message) {
            const li = document.createElement('li');
            li.className = 'krt-combobox__notice';
            li.setAttribute('aria-disabled', 'true');
            li.textContent = message;
            return li;
        }

        function setActive(index) {
            if (activeIndex >= 0 && rendered[activeIndex]) {
                rendered[activeIndex].el.classList.remove('krt-combobox__option--active');
            }
            activeIndex = index;
            if (index < 0 || !rendered[index]) {
                input.removeAttribute('aria-activedescendant');
                return;
            }
            const el = rendered[index].el;
            el.classList.add('krt-combobox__option--active');
            input.setAttribute('aria-activedescendant', el.id);
            const top = el.offsetTop;
            const bottom = top + el.offsetHeight;
            if (top < listbox.scrollTop) {
                listbox.scrollTop = top;
            } else if (bottom > listbox.scrollTop + listbox.clientHeight) {
                listbox.scrollTop = bottom - listbox.clientHeight;
            }
        }

        function renderOptions(query) {
            const q = (query || '').trim().toLowerCase();
            listbox.textContent = '';
            rendered = [];
            activeIndex = -1;

            // Remote mode: the backend already filtered to the query, so render the fetched set
            // as-is (highlighting still keys off the typed term); local mode filters in place.
            const matches = remoteSource
                ? items.slice()
                : q
                  ? items.filter(function (it) {
                        return it.label.toLowerCase().indexOf(q) !== -1;
                    })
                  : items.slice();
            const truncated = matches.length > maxResults;

            matches.slice(0, maxResults).forEach(function (it, idx) {
                const li = document.createElement('li');
                li.id = listboxId + '-opt-' + idx;
                li.className = 'krt-combobox__option';
                li.setAttribute('role', 'option');
                li.setAttribute('aria-selected', it.value === hidden.value ? 'true' : 'false');
                appendHighlighted(li, it.label, q);
                // mousedown keeps focus on the textbox so blur does not pre-empt the
                // pick; the commit runs on click so a programmatic .click() (tests)
                // still resolves on a visible target before the list closes.
                li.addEventListener('mousedown', function (event) {
                    event.preventDefault();
                });
                li.addEventListener('click', function () {
                    commit(it);
                });
                li.addEventListener('mousemove', function () {
                    if (activeIndex !== idx) {
                        setActive(idx);
                    }
                });
                listbox.appendChild(li);
                rendered.push({ value: it.value, label: it.label, el: li });
            });

            if (rendered.length === 0) {
                listbox.appendChild(noticeRow(texts.noResults));
            } else if (truncated && texts.hint) {
                listbox.appendChild(noticeRow(texts.hint));
            }
        }

        function highlightCommitted() {
            const selIdx = rendered.findIndex(function (r) {
                return r.value === hidden.value;
            });
            if (selIdx >= 0) {
                setActive(selIdx);
            }
        }

        function open(query) {
            renderOptions(query);
            listbox.hidden = false;
            input.setAttribute('aria-expanded', 'true');
            highlightCommitted();
        }

        // Renders a transient "loading" row while a remote fetch is in flight.
        function renderLoading() {
            listbox.textContent = '';
            rendered = [];
            activeIndex = -1;
            listbox.appendChild(noticeRow(texts.loading || texts.hint || ''));
        }

        // Remote mode: fetch the option set for `query` from the backend, then render it. A
        // monotonic token drops a slow earlier response so it cannot overwrite a newer query.
        function loadRemote(query) {
            const token = ++remoteSeq;
            Promise.resolve(remoteSource(query))
                .then(function (list) {
                    if (token !== remoteSeq || !isOpen()) {
                        return;
                    }
                    items = Array.isArray(list) ? list.slice() : [];
                    renderOptions(query);
                    highlightCommitted();
                })
                .catch(function () {
                    if (token !== remoteSeq || !isOpen()) {
                        return;
                    }
                    items = [];
                    renderOptions(query);
                });
        }

        // Opens the popup in remote mode: shows a loading row at once, then debounces the fetch.
        function openRemote(query, delay) {
            renderLoading();
            listbox.hidden = false;
            input.setAttribute('aria-expanded', 'true');
            window.clearTimeout(remoteTimer);
            remoteTimer = window.setTimeout(function () {
                loadRemote(query);
            }, delay || 0);
        }

        function close() {
            if (!isOpen()) {
                return;
            }
            listbox.hidden = true;
            input.setAttribute('aria-expanded', 'false');
            input.removeAttribute('aria-activedescendant');
            activeIndex = -1;
        }

        function commit(item) {
            const next = item ? item.value : '';
            const changed = hidden.value !== next;
            hidden.value = next;
            committedLabel = item ? item.label : '';
            input.value = committedLabel;
            input.setCustomValidity('');
            close();
            if (changed) {
                hidden.dispatchEvent(new Event('change', { bubbles: true }));
            }
        }

        // Keep the hidden value (and thus form validity) in step with free text:
        // an exact label match commits silently, anything else clears the value and
        // arms the custom-validity message so submit stays blocked until resolved.
        function reconcile() {
            const typed = input.value.trim().toLowerCase();
            let exact = null;
            for (let i = 0; i < items.length; i++) {
                if (items[i].label.toLowerCase() === typed) {
                    exact = items[i];
                    break;
                }
            }
            if (exact) {
                committedLabel = exact.label;
                if (hidden.value !== exact.value) {
                    hidden.value = exact.value;
                    hidden.dispatchEvent(new Event('change', { bubbles: true }));
                }
                input.setCustomValidity('');
                return;
            }
            if (hidden.value) {
                hidden.value = '';
                hidden.dispatchEvent(new Event('change', { bubbles: true }));
            }
            input.setCustomValidity(input.value.trim() ? texts.invalid : '');
        }

        input.addEventListener('focus', function () {
            input.select();
        });

        input.addEventListener('click', function () {
            if (isOpen()) {
                close();
            } else if (remoteSource) {
                openRemote('', 0);
            } else {
                open('');
            }
        });

        input.addEventListener('input', function () {
            if (remoteSource) {
                openRemote(input.value, 250);
            } else {
                open(input.value);
            }
            reconcile();
        });

        input.addEventListener('keydown', function (event) {
            switch (event.key) {
                case 'ArrowDown':
                    event.preventDefault();
                    if (!isOpen()) {
                        if (remoteSource) {
                            openRemote('', 0);
                        } else {
                            // Opening lands on the committed row (set by open()), else the first.
                            open('');
                            if (activeIndex < 0) {
                                setActive(0);
                            }
                        }
                    } else {
                        setActive(activeIndex + 1 >= rendered.length ? 0 : activeIndex + 1);
                    }
                    break;
                case 'ArrowUp':
                    event.preventDefault();
                    if (!isOpen()) {
                        if (remoteSource) {
                            openRemote('', 0);
                        } else {
                            open('');
                            if (activeIndex < 0) {
                                setActive(rendered.length - 1);
                            }
                        }
                    } else {
                        setActive(activeIndex - 1 < 0 ? rendered.length - 1 : activeIndex - 1);
                    }
                    break;
                case 'Enter':
                    if (isOpen() && activeIndex >= 0 && rendered[activeIndex]) {
                        event.preventDefault();
                        commit(rendered[activeIndex]);
                    } else if (isOpen() && rendered.length === 1) {
                        event.preventDefault();
                        commit(rendered[0]);
                    } else if (isOpen()) {
                        close();
                    }
                    break;
                case 'Escape':
                    if (isOpen()) {
                        event.preventDefault();
                        close();
                        input.value = committedLabel;
                        input.setCustomValidity('');
                    }
                    break;
                case 'Home':
                    if (isOpen() && rendered.length) {
                        event.preventDefault();
                        setActive(0);
                    }
                    break;
                case 'End':
                    if (isOpen() && rendered.length) {
                        event.preventDefault();
                        setActive(rendered.length - 1);
                    }
                    break;
                default:
                    break;
            }
        });

        // Leaving the field (tab / click away) closes the popup and discards any
        // stray free text, snapping the box back to the committed label. Deferred
        // so an option click resolves first; skipped if focus stayed on the input.
        input.addEventListener('blur', function () {
            window.setTimeout(function () {
                if (document.activeElement === input) {
                    return;
                }
                close();
                input.value = committedLabel;
                input.setCustomValidity('');
            }, 150);
        });
    }

    window.krtSearchableSelect = krtSearchableSelect;
})();
