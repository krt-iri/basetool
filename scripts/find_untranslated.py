"""Find EN entries that still have German content.

Compares messages_de.properties and messages_en.properties: any common key
whose EN value is identical to the DE value AND contains German linguistic
markers (umlauts via \\uXXXX escapes, German function/content words) is
flagged as likely untranslated.
"""

import re


def load(path):
    d = {}
    with open(path, encoding='utf-8') as f:
        for line in f:
            line = line.rstrip('\r\n')
            if line and not line.startswith('#') and '=' in line:
                k, _, v = line.partition('=')
                d[k] = v
    return d


def main():
    root = 'frontend/src/main/resources'
    de = load(f'{root}/messages_de.properties')
    en = load(f'{root}/messages_en.properties')

    umlaut_re = re.compile(r'\\u00[A-Fa-f0-9]{2}')
    german_words = re.compile(
        r"\b(und|oder|der|die|das|ein|eine|einer|einen|von|den|dem|mit|f.r|"
        r"ist|sind|wird|werden|nicht|kein|keine|aber|sondern|als|auch|nach|"
        r"bei|seit|aus|zur|zum|gegen|ohne|w.hrend|durch|um|im|an|auf|wegen|"
        r"trotz|alle|aller|alles|nur|noch|schon|sehr|mehr|sich|wenn|dann|"
        r"weil|dass|wie|wo|warum|hier|dort|jetzt|heute|morgen|gestern|"
        r"hinzuf.gen|l.schen|speichern|laden|erstellen|bearbeiten|entfernen|"
        r"abbrechen|best.tigen|fehler|erfolg|erfolgreich|gel.scht|bitte|"
        r"nachricht|datum|uhrzeit|datei|seite|liste|menge|gesamt|aktiv|"
        r"inaktiv|aktivieren|deaktivieren|abgeschlossen|offen|erstellt|"
        r"geschlossen|gesperrt|warten|wartet|gespeichert|verkn.pft|"
        r"hochgeladen|ausgew.hlt|markiert|zugewiesen|bearbeitet|gesendet|"
        r"abbruch|verworfen|wirklich|leider|sicher|vor|seit|aktuell|"
        r"derzeit|momentan|verboten|berechtigt|unberechtigt|m.glich|"
        r"unm.glich|grund|begr.ndung|notwendig|optional|verbleibend|"
        r"einsatz|auftrag|antrag|nutzer|benutzer|name|datei|materialien?|"
        r"schiffe|schiff|kunde|kunden|lieferant|raffinerie|hangar|terminal|"
        r"verwalten|ansicht|liste|details|legal|impressum|datenschutz|"
        r"login|anmelden|abmelden|abgemeldet|angemeldet|registrieren|"
        r"passwort)\b",
        re.IGNORECASE,
    )

    likely_de = []
    identical = []
    for k in sorted(en.keys() & de.keys()):
        en_v = en[k]
        de_v = de[k]
        if en_v == de_v:
            identical.append((k, en_v))
            if umlaut_re.search(en_v) or german_words.search(en_v):
                likely_de.append((k, en_v))

    print(f"Identical EN/DE entries: {len(identical)}")
    print(f"Of those, likely-German (heuristic): {len(likely_de)}")
    print()
    print("=== Likely-German EN entries (key=value) ===")
    for k, v in likely_de:
        print(f"{k}={v}")


if __name__ == '__main__':
    main()
