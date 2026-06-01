/* Profit Basetool — screens. Depends on components.jsx, data.jsx, icons.jsx. */
const { useState: useS } = React;

const fmt = (n) => n == null ? null : n.toLocaleString("de-DE");

/* ---------------------------------------------------------------- LOGIN --- */
function LoginScreen({ onLogin }) {
  return (
    <div className="login-stage">
      <HudBox className="login-card">
        <div className="login-logo">
          <img src="../../assets/krt.webp" alt="DAS KARTELL" />
          <div className="name">Profit Basetool</div>
          <div className="sub">IRIDIUM · Squadron Access</div>
        </div>
        <form onSubmit={(e) => { e.preventDefault(); onLogin(); }}>
          <div className="field">
            <label>Username</label>
            <input type="text" defaultValue="cmdr.valk" autoComplete="username" />
          </div>
          <div className="field">
            <label>Password</label>
            <input type="password" defaultValue="••••••••" autoComplete="current-password" />
          </div>
          <Btn variant={null} type="submit">Sign in via Keycloak</Btn>
        </form>
        <div className="login-foot">
          Access is reserved for members &amp; approved guests.<br />
          <a href="#" onClick={(e) => { e.preventDefault(); onLogin(); }}>Create an order as guest →</a>
        </div>
      </HudBox>
    </div>
  );
}

/* ------------------------------------------------------------ DASHBOARD --- */
function Dashboard({ onOpenMission }) {
  const m = NEXT_MISSION;
  return (
    <React.Fragment>
      <div className="greeting">
        <h1>Welcome, Commander Valk</h1>
        <p>Central platform for mission planning and fleet management.</p>
      </div>
      <div className="dash-grid">
        <HudBox>
          <h2 style={{ marginTop: 0 }}>Next Mission</h2>
          <div className="info-grid">
            <strong>Name:</strong><span>{m.name}</span>
            <strong>Status:</strong><span><StatusPill status={m.status} /></span>
            <strong>Description:</strong><span>{m.description}</span>
            <strong>Meeting (TS):</strong><span>{m.meetingTime}</span>
            <strong>Server Join:</strong><span>{m.startTime}</span>
            <strong>Participants:</strong><span>{m.participants}</span>
          </div>
          <div style={{ marginTop: "1.25rem" }}>
            <Btn onClick={() => onOpenMission()}>Open Mission</Btn>
          </div>
        </HudBox>
        <HudBox>
          <h2 style={{ marginTop: 0 }}>Squadron Status</h2>
          <div className="stat-row">
            <div className="stat hud-box" style={{ padding: "1rem" }}><div className="n">18</div><div className="k">Active Pilots</div></div>
            <div className="stat hud-box" style={{ padding: "1rem" }}><div className="n">42</div><div className="k">Ships in Hangar</div></div>
          </div>
          <div className="stat-row">
            <div className="stat hud-box" style={{ padding: "1rem" }}><div className="n">7</div><div className="k">Open Job Orders</div></div>
            <div className="stat hud-box" style={{ padding: "1rem" }}><div className="n" style={{ color: "var(--color-success)" }}>2.4M</div><div className="k">Profit (30d, aUEC)</div></div>
          </div>
        </HudBox>
      </div>
      <div style={{ marginTop: "1rem" }}>
        <div className="alert alert-warning" style={{ marginBottom: 0 }}>
          <Icon name="warning" /> &nbsp;UEX price feed last synced 3h ago — refinery margins may be stale.
        </div>
      </div>
    </React.Fragment>
  );
}

/* ------------------------------------------------------------- MISSIONS --- */
function MissionsScreen({ push, onOpen }) {
  const [q, setQ] = useS("");
  const [showPast, setShowPast] = useS(true);
  let rows = MISSIONS.filter((m) => m.name.toLowerCase().includes(q.toLowerCase()));
  if (!showPast) rows = rows.filter((m) => m.status === "PLANNED" || m.status === "ACTIVE");
  return (
    <React.Fragment>
      <div className="page-head">
        <h1 className="section-title" style={{ border: "none", marginBottom: 0 }}>Mission Management</h1>
        <Btn icon="plus" onClick={() => push("Action", "New mission form opened.")}>New Mission</Btn>
      </div>
      <div className="toolbar" style={{ marginTop: "1rem" }}>
        <div className="search">
          <Icon name="search" />
          <input type="search" placeholder="Enter mission name…" value={q} onChange={(e) => setQ(e.target.value)} />
        </div>
        <label style={{ display: "flex", gap: "0.5rem", alignItems: "center", color: "var(--color-gray-1)", fontSize: "0.9rem" }}>
          <input type="checkbox" checked={showPast} onChange={(e) => setShowPast(e.target.checked)} /> Show past missions
        </label>
      </div>
      <table>
        <thead><tr><th>Mission</th><th>Department</th><th>Status</th><th>Server Join</th><th>Owner</th><th>Part.</th></tr></thead>
        <tbody>
          {rows.map((m) => (
            <tr key={m.id} style={{ cursor: onOpen ? "pointer" : "default" }} onClick={() => onOpen && onOpen(m.id)}>
              <td>{m.name}</td>
              <td><span className="dept-tag" style={{ color: "var(--color-dept-" + m.dept + ")" }}>{m.deptLabel}</span></td>
              <td><StatusPill status={m.status} /></td>
              <td>{m.start}</td>
              <td>{m.owner}</td>
              <td className="num-cell">{m.participants}</td>
            </tr>
          ))}
          {rows.length === 0 ? <tr><td colSpan="6" style={{ textAlign: "center", fontStyle: "italic", color: "var(--color-gray-2)" }}>No missions found.</td></tr> : null}
        </tbody>
      </table>
    </React.Fragment>
  );
}

/* --------------------------------------------------------------- HANGAR --- */
function HangarScreen({ push }) {
  const [ships, setShips] = useS(SHIPS);
  const toggle = (id) => setShips((p) => p.map((s) => s.id === id ? { ...s, fitted: !s.fitted } : s));
  const del = (id) => { setShips((p) => p.filter((s) => s.id !== id)); push("Action successful", "Ship successfully deleted."); };
  return (
    <React.Fragment>
      <div className="page-head">
        <h1 className="section-title" style={{ border: "none", marginBottom: 0 }}>Hangar</h1>
        <Btn icon="plus" onClick={() => push("Action", "Add-ship form opened.")}>Add Ship</Btn>
      </div>
      <table style={{ marginTop: "1rem" }}>
        <thead><tr><th>Name</th><th>Ship Type</th><th>Maker</th><th>Owner</th><th>Insurance</th><th>Location</th><th>Fitted</th><th>Action</th></tr></thead>
        <tbody>
          {ships.map((s) => (
            <tr key={s.id}>
              <td style={{ fontWeight: 700 }}>{s.name}</td>
              <td>{s.type}</td>
              <td>{s.maker}</td>
              <td>{s.owner}</td>
              <td>{s.insurance === "LTI" ? <Badge>LTI</Badge> : s.insurance}</td>
              <td style={{ color: "var(--color-gray-1)" }}>{s.location}</td>
              <td><span className="dot" onClick={() => toggle(s.id)} role="button" title="Toggle fitted"
                    style={{ cursor: "pointer", background: s.fitted ? "var(--color-success)" : "var(--color-gray-2)" }}></span>
                  <span style={{ marginLeft: 8, fontSize: "0.8rem", color: s.fitted ? "var(--color-success)" : "var(--color-gray-2)" }}>{s.fitted ? "Ready" : "Unfitted"}</span></td>
              <td>
                <span className="row-action">
                  <button className="icon-btn" title="Edit" onClick={() => push("Action", "Editing " + s.name + ".")}><Icon name="edit" /></button>
                  <button className="icon-btn danger" title="Delete" onClick={() => del(s.id)}><Icon name="trash" /></button>
                </span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </React.Fragment>
  );
}

/* ------------------------------------------------------------ MATERIALS --- */
function MaterialsScreen() {
  const [collapsed, setCollapsed] = useS({});
  const toggle = (k) => setCollapsed((p) => ({ ...p, [k]: !p[k] }));
  return (
    <React.Fragment>
      <h1 className="section-title">Price Overview</h1>
      <HudBox>
        <p style={{ color: "var(--color-gray-2)", fontSize: "0.85rem", marginTop: 0 }}>
          Sell prices in <span className="price-sell">green (+)</span>, buy prices in <span className="price-buy">red (−)</span>, per terminal. Click a category to collapse.
        </p>
        <div style={{ overflowX: "auto" }}>
          <table className="matrix-table" style={{ marginTop: "0.5rem" }}>
            <thead>
              <tr><th>Commodity</th>{TERMINALS.map((t) => <th key={t.name} className="num-cell" title={t.planet}>{t.name}</th>)}</tr>
            </thead>
            <tbody>
              {MATERIALS.map((grp) => (
                <React.Fragment key={grp.kind}>
                  <tr className="kind-row" onClick={() => toggle(grp.kind)}>
                    <td colSpan={TERMINALS.length + 1}>{collapsed[grp.kind] ? "+" : "−"} &nbsp;{grp.kind}</td>
                  </tr>
                  {!collapsed[grp.kind] && grp.rows.map((r) => (
                    <tr key={r.name}>
                      <td>{r.volatile ? <span className="text-warning" title="Volatile" style={{ marginRight: 6 }}>⚠</span> : null}{r.name}</td>
                      {r.prices.map((p, i) => (
                        <td key={i} className="num-cell">
                          {p == null ? <span style={{ color: "var(--color-gray-3)" }}>–</span> : <span className="price-sell">+{fmt(p)}</span>}
                        </td>
                      ))}
                    </tr>
                  ))}
                </React.Fragment>
              ))}
            </tbody>
          </table>
        </div>
      </HudBox>
    </React.Fragment>
  );
}

/* --------------------------------------------------- ADMIN (light) -------- */
function MembersScreen() {
  const members = [
    { name: "Valk", roles: "Admin · Officer", sk: "—", status: "In Keycloak" },
    { name: "Mara", roles: "Logistician", sk: "Vipers", status: "In Keycloak" },
    { name: "Hex", roles: "Mission Manager", sk: "Vipers", status: "In Keycloak" },
    { name: "Dane", roles: "Squadron Member", sk: "—", status: "In Keycloak" },
  ];
  return (
    <React.Fragment>
      <h1 className="section-title">Member Management</h1>
      <p style={{ color: "var(--color-gray-2)", fontSize: "0.9rem" }}>Manage the members of your squadron.</p>
      <table>
        <thead><tr><th>Name</th><th>Staffel Roles</th><th>SK</th><th>Status</th><th>Action</th></tr></thead>
        <tbody>
          {members.map((m) => (
            <tr key={m.name}>
              <td style={{ fontWeight: 700 }}>{m.name}</td>
              <td>{m.roles}</td>
              <td>{m.sk === "—" ? <span style={{ color: "var(--color-gray-3)" }}>—</span> : <Badge variant="sk">{m.sk}</Badge>}</td>
              <td><span style={{ color: "var(--color-success)", fontSize: "0.85rem" }}>{m.status}</span></td>
              <td><button className="icon-btn" title="Edit"><Icon name="edit" /></button></td>
            </tr>
          ))}
        </tbody>
      </table>
    </React.Fragment>
  );
}

function PlaceholderScreen({ title, note }) {
  return (
    <React.Fragment>
      <h1 className="section-title">{title}</h1>
      <HudBox>
        <p style={{ color: "var(--color-gray-2)", margin: 0 }}>
          <Icon name="info" /> &nbsp;{note}
        </p>
      </HudBox>
    </React.Fragment>
  );
}

Object.assign(window, {
  LoginScreen, Dashboard, MissionsScreen, HangarScreen, MaterialsScreen,
  MembersScreen, PlaceholderScreen,
});
