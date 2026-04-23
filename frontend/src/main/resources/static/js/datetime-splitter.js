/**
 * Datetime-Splitter
 *
 * Warum: Das Backend speichert alle Zeitpunkte ausschliesslich in UTC als ISO-8601
 * (java.time.Instant -> "YYYY-MM-DDTHH:mm:ss(.SSS)Z"). Die Anzeige bzw. Eingabe
 * muss in der lokalen Zeitzone des Browsers erfolgen (DST-konform), damit der
 * Benutzer seine Lokalzeit sieht und eingibt. Dieses Skript ist die EINZIGE
 * Stelle, die in Formularen die Umrechnung UTC <-> Browser-Lokalzeit fuer
 * date-/time-Eingabefelder vornimmt.
 *
 * Hidden-Value-Vertrag:
 *   - Beim Laden akzeptiert: ISO-Instant mit 'Z' oder Offset, ISO-LocalDateTime
 *     (YYYY-MM-DDTHH:mm[:ss]) oder nur Datum (YYYY-MM-DD). Alles andere wird
 *     als Lokalzeit interpretiert (Fallback, rueckwaertskompatibel).
 *   - Beim Schreiben: immer UTC-ISO-Instant mit Sekunden und 'Z'
 *     (z.B. "2026-04-19T14:30:00Z").
 */
document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.datetime-split-group').forEach(group => {
        const hidden = group.querySelector('input[type="hidden"]');
        const datePart = group.querySelector('.date-part');
        const timePart = group.querySelector('.time-part');

        if (!hidden || !datePart || !timePart) return;

        const errorDiv = document.createElement('div');
        errorDiv.style.color = 'var(--color-dept-combat)';
        errorDiv.style.fontSize = '0.8rem';
        errorDiv.style.marginTop = '0.2rem';
        group.appendChild(errorDiv);

        const pad = n => String(n).padStart(2, '0');

        // Erkennt, ob der Wert bereits Zonen-Information (Z oder +/-HH:MM) traegt.
        const hasZoneInfo = (val) => /Z$|[+-]\d{2}:?\d{2}$/.test(val);

        const isoDateRegex = /^\d{4}-\d{2}-\d{2}$/;
        const isoLocalRegex = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?$/;

        // Lokale date-/time-Inputs aus dem Hidden-Wert initial befuellen.
        if (hidden.value) {
            const v = hidden.value.trim();
            if (isoDateRegex.test(v)) {
                // Nur Datum, keine Uhrzeit
                datePart.value = v;
                timePart.value = '';
            } else if (hasZoneInfo(v)) {
                // UTC/Offset -> in Browser-Lokalzeit umrechnen
                const d = new Date(v);
                if (!isNaN(d)) {
                    datePart.value = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
                    timePart.value = `${pad(d.getHours())}:${pad(d.getMinutes())}`;
                }
            } else if (isoLocalRegex.test(v)) {
                // Rueckwaertskompatibel: bereits Lokalzeit-String
                const parts = v.split('T');
                datePart.value = parts[0];
                timePart.value = parts[1].substring(0, 5);
            } else {
                // Letzter Fallback: versuchen als Date zu parsen und lokal darzustellen
                const d = new Date(v);
                if (!isNaN(d)) {
                    datePart.value = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
                    timePart.value = `${pad(d.getHours())}:${pad(d.getMinutes())}`;
                }
            }
        }

        const updateHidden = () => {
            errorDiv.textContent = '';
            if (datePart.value && timePart.value) {
                // Lokale Eingabe -> Date-Objekt -> toISOString() liefert UTC mit 'Z'.
                const [y, m, d] = datePart.value.split('-').map(Number);
                const [hh, mm] = timePart.value.split(':').map(Number);
                const local = new Date(y, (m - 1), d, hh, mm, 0, 0);
                if (!isNaN(local)) {
                    hidden.value = local.toISOString();
                } else {
                    hidden.value = '';
                }
            } else if (datePart.value) {
                // Nur Datum -> als reines Datum (ohne Zone) uebermitteln
                hidden.value = datePart.value;
            } else {
                hidden.value = '';
            }

            if (hidden.value) {
                const currentDate = new Date();
                const selectedDate = new Date(hidden.value);

                if (group.getAttribute('data-validate-not-past') === 'true') {
                    if (selectedDate < currentDate) {
                        errorDiv.textContent = group.getAttribute('data-error-past') || 'Date cannot be in the past.';
                    }
                }

                if (group.hasAttribute('data-validate-after')) {
                    const targetId = group.getAttribute('data-validate-after');
                    const targetHidden = document.getElementById(targetId);
                    if (targetHidden && targetHidden.value) {
                        const targetDate = new Date(targetHidden.value);
                        if (selectedDate <= targetDate) {
                            errorDiv.textContent = group.getAttribute('data-error-after') || 'End time must be after start time.';
                        }
                    }
                }
            }

            hidden.dispatchEvent(new Event('change'));
        };

        datePart.addEventListener('change', updateHidden);
        timePart.addEventListener('change', updateHidden);
        datePart.addEventListener('input', updateHidden);
        timePart.addEventListener('input', updateHidden);
    });
});
