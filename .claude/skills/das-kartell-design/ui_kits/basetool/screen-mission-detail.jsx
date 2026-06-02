/* Profit Basetool — Mission detail screen. Demonstrates the action hierarchy.
   Depends on components.jsx, data.jsx, icons.jsx. */
const { useState: useMD } = React;

function Panel({ id, title, count, defaultOpen, children }) {
  const [open, setOpen] = useMD(defaultOpen !== false);
  return (
    <div className={"mcol" + (open ? "" : " collapsed")}>
      <button type="button" className="panel-header" aria-expanded={open} onClick={() => setOpen((o) => !o)}>
        <h2>{title}{count != null ? <span className="panel-count">{count}</span> : null}</h2>
        <span className="toggle-icon" aria-hidden="true"></span>
      </button>
      <div className="col-body">{children}</div>
    </div>
  );
}

function MissionDetailScreen({ push, onBack }) {
  const [parts, setParts] = useMD([
    { id: 1, user: "cmdr.valk", org: "IRI", job: "Pilot", state: "in" },
    { id: 2, user: "mara.k", org: "IRI", job: "Gunner", state: "out" },
    { id: 3, user: "hex_07", org: null, job: "Medic", state: "pre" },
  ]);
  const checkIn = (id) => { setParts((p) => p.map((x) => x.id === id ? { ...x, state: "in" } : x)); push("Check-In", "Participant checked in."); };
  const del = (id) => { setParts((p) => p.filter((x) => x.id !== id)); push("Action successful", "Participant removed."); };

  return (
    <React.Fragment>
      <div className="greeting hud-box" style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: "1rem", flexWrap: "wrap" }}>
        <div style={{ display: "flex", alignItems: "center", gap: "0.75rem", flexWrap: "wrap" }}>
          <h1 style={{ margin: 0, fontSize: "1.6rem" }}>Einsatz: Operation Tiefschlag</h1>
          <span className="squadron-badge">IRI</span>
          <span className="status-pill status-planned" style={{ marginLeft: "0.25rem" }}>PLANNED</span>
        </div>
        <button className="btn btn-ghost" onClick={onBack}>Zurück</button>
      </div>

      <div className="mission-cols">
        {/* DETAILS — form: neutral labels, single CTA */}
        <Panel title="Details">
          <div className="hud-box">
            <div className="form-group">
              <label className="form-label-sm">Name</label>
              <input type="text" defaultValue="Operation Tiefschlag" />
            </div>
            <div className="form-group">
              <label className="form-label-sm">Beschreibung</label>
              <textarea rows="2" defaultValue="Quantanium-Run durch Pyro — Kampfeskorte + Raffinerie-Übergabe."></textarea>
            </div>
            <div style={{ display: "flex", gap: "1rem", flexWrap: "wrap" }}>
              <div className="form-group" style={{ flex: 1, minWidth: 140 }}>
                <label className="form-label-sm">Status</label>
                <select defaultValue="PLANNED"><option>PLANNED</option><option>ACTIVE</option><option>COMPLETED</option></select>
              </div>
              <div className="form-group" style={{ flex: 1, minWidth: 140 }}>
                <label className="form-label-sm">Geplanter Start</label>
                <input type="text" defaultValue="29.05.2026  19:00" />
              </div>
            </div>
          </div>
          <div className="hud-box detail-actions" style={{ justifyContent: "flex-end" }}>
            <button className="btn btn-quiet-danger" style={{ marginRight: "auto" }} onClick={() => push("Bestätigung", "Wirklich löschen?", true)}>Delete</button>
            <button className="btn btn-ghost" onClick={onBack}>Zurück</button>
            <button className="btn btn--cta" onClick={() => push("Gespeichert", "Einsatz erfolgreich gespeichert.")}>Speichern</button>
          </div>
        </Panel>

        {/* ORGANISATION — data values neutral-bright, Edit ghost */}
        <Panel title="Organisation">
          <div className="hud-box">
            <div className="kv-row"><span className="kv-key">Einsatzleiter</span><span className="data-value">cmdr.valk</span></div>
            <div className="kv-row"><span className="kv-key">Flottenfunk</span><span className="kv-right"><span className="data-value data-value--mono">123.450</span><button className="btn btn-ghost btn-sm2" onClick={() => push("Frequenz", "Bearbeiten…")}>Edit</button></span></div>
            <div className="kv-row"><span className="kv-key">Bodenfunk</span><span className="kv-right"><span className="data-value data-value--mono">88.200</span><button className="btn btn-ghost btn-sm2" onClick={() => push("Frequenz", "Bearbeiten…")}>Edit</button></span></div>
          </div>
        </Panel>

        {/* PARTICIPANTS — one CTA (Anmelden), green status, ghost/quiet rows */}
        <Panel title="Teilnehmer" count={parts.filter((p) => p.state === "in").length + "/" + parts.length}>
          <div className="hud-box">
            <div className="panel-toolbar">
              <button className="btn btn--cta" onClick={() => push("Anmeldung", "Teilnehmer-Formular geöffnet.")}>＋ Anmelden</button>
            </div>
            <table className="mission-table" style={{ marginTop: 0 }}>
              <thead><tr><th>Benutzer</th><th>Org</th><th>Aufgabe</th><th style={{ textAlign: "right" }}>Aktion</th></tr></thead>
              <tbody>
                {parts.map((p) => (
                  <tr key={p.id}>
                    <td style={{ fontWeight: 700 }}>{p.user}</td>
                    <td>{p.org ? <Badge>{p.org}</Badge> : <Badge variant="muted">—</Badge>}</td>
                    <td>{p.job}</td>
                    <td><div className="act">
                      {p.state === "pre" ? <button className="btn btn-success btn-sm2" onClick={() => checkIn(p.id)}>Check-In</button> : null}
                      {p.state === "in" ? <button className="btn btn-ghost btn-sm2" onClick={() => push("Check-Out", "Ausgecheckt.")}>Check-Out</button> : null}
                      <button className="btn btn-ghost btn-sm2" onClick={() => push("Bearbeiten", "Teilnehmer bearbeiten…")}>Edit</button>
                      <button className="btn btn-quiet-danger btn-sm2" onClick={() => del(p.id)}>Delete</button>
                    </div></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Panel>

        {/* UNITS — CTA add, per-unit emphasis (outline) + ghost + quiet */}
        <Panel title="Einheiten">
          <div className="hud-box">
            <div className="panel-toolbar">
              <button className="btn btn--cta" onClick={() => push("Einheit", "Einheit hinzufügen…")}>＋ Hinzufügen</button>
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
              {[{ n: "Schwarze Witwe", t: "Constellation Andromeda" }, { n: "Eisenfaust", t: "Hammerhead" }].map((u) => (
                <div className="unit-box" key={u.n}>
                  <div className="unit-head">
                    <span><span className="unit-name">{u.n}</span><span style={{ color: "var(--color-gray-1)" }}> — {u.t}</span></span>
                    <div className="detail-actions">
                      <button className="btn btn-outline btn-sm2" onClick={() => push("Crew", "Crew zuweisen…")}>Crew zuweisen</button>
                      <button className="btn btn-ghost btn-sm2" onClick={() => push("Bearbeiten", "Einheit bearbeiten…")}>Edit</button>
                      <button className="btn btn-quiet-danger btn-sm2" onClick={() => push("Einheit", "Einheit gelöscht.")}>Delete</button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </Panel>
      </div>
    </React.Fragment>
  );
}

Object.assign(window, { MissionDetailScreen });
