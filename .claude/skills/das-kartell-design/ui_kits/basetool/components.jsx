/* Profit Basetool — shared chrome components. Depends on Icon (icons.jsx). */
const { useState, useEffect, useCallback } = React;

const LOGO_MARK = "../../assets/krt.webp";

function HudBox({ children, className, style }) {
  return <div className={"hud-box" + (className ? " " + className : "")} style={style}>{children}</div>;
}

function Btn({ variant, children, onClick, type, disabled, icon, style }) {
  const cls = "btn" + (variant ? " btn-" + variant : "");
  return (
    <button className={cls} onClick={onClick} type={type || "button"} disabled={disabled} style={style}>
      {icon ? <Icon name={icon} /> : null}{children}
    </button>
  );
}

function Badge({ variant, children }) {
  const cls = "squadron-badge" + (variant ? " squadron-badge-" + variant : "");
  return <span className={cls}>{children}</span>;
}

function StatusPill({ status }) {
  const map = {
    PLANNED: "status-planned", ACTIVE: "status-active",
    COMPLETED: "status-completed", CANCELLED: "status-cancelled", CANCELED: "status-cancelled",
  };
  return <span className={"status-pill " + (map[status] || "status-completed")}>{status}</span>;
}

function Header({ onHamburger, admin, activeSquadron }) {
  return (
    <React.Fragment>
      <header className={"app-header" + (admin ? " admin" : "")}>
        <button className="hamburger" onClick={onHamburger} aria-label="Menu">
          <span></span><span></span><span></span>
        </button>
        <a className="brand" href="#" onClick={(e) => e.preventDefault()}>
          <img src={LOGO_MARK} alt="DAS KARTELL" />
          <span className="logo-text">Profit Basetool</span>
        </a>
        {admin ? <span className="admin-chip">Admin</span> : null}
      </header>
      <div className="ctx-chip">
        <span className="lbl">Staffel</span>
        <span className="val">{activeSquadron || "IRI"}</span>
      </div>
    </React.Fragment>
  );
}

const NAV_MAIN = [
  { id: "home", label: "Home" },
  { id: "missions", label: "Missions" },
  { id: "hangar", label: "Hangar" },
  { id: "materials", label: "Price Overview" },
];
const NAV_ADMIN = [
  { id: "members", label: "Member Management" },
  { id: "uex", label: "UEX Data" },
  { id: "settings", label: "System Settings" },
];

function Sidebar({ open, onClose, current, onNavigate, onLogout }) {
  const go = (id) => { onNavigate(id); onClose(); };
  return (
    <React.Fragment>
      <div className={"sidebar" + (open ? " open" : "")}>
        <div className="sidebar-content">
          <button className="close-sidebar" onClick={onClose} aria-label="Close">&times;</button>
          <div className="sidebar-links">
            {NAV_MAIN.map((n) => (
              <button key={n.id} className={"navlink" + (current === n.id ? " active" : "")} onClick={() => go(n.id)}>{n.label}</button>
            ))}
            <div className="sidebar-group">
              <div className="grp-title">Administration</div>
              <div className="sidebar-sublinks">
                {NAV_ADMIN.map((n) => (
                  <button key={n.id} className={"navlink" + (current === n.id ? " active" : "")} onClick={() => go(n.id)}>{n.label}</button>
                ))}
              </div>
            </div>
            <div className="sidebar-group">
              <button className="navlink" onClick={onLogout}>Logout</button>
            </div>
          </div>
        </div>
      </div>
      <div className={"sidebar-overlay" + (open ? " visible" : "")} onClick={onClose}></div>
    </React.Fragment>
  );
}

function Footer() {
  return (
    <footer className="app-footer">
      <div className="links">
        <a href="#" onClick={(e) => e.preventDefault()}>Impressum</a>
        <a href="#" onClick={(e) => e.preventDefault()}>Datenschutz</a>
        <a href="#" onClick={(e) => e.preventDefault()}>Nutzungsbedingungen</a>
      </div>
      <span className="ver">DAS KARTELL · Profit Basetool · v1.4.3</span>
    </footer>
  );
}

/* Toast system ------------------------------------------------------------- */
function Toast({ t }) {
  return (
    <div className={"notification-toast toast-enter" + (t.error ? " error-toast" : "")}>
      <h4>{t.title}</h4>
      <p>{t.body}</p>
    </div>
  );
}
function ToastViewport({ toasts }) {
  return <div className="toast-vp">{toasts.map((t) => <Toast key={t.id} t={t} />)}</div>;
}
function useToasts() {
  const [toasts, setToasts] = useState([]);
  const push = useCallback((title, body, error) => {
    const id = Math.random().toString(36).slice(2);
    setToasts((p) => [...p, { id, title, body, error }]);
    setTimeout(() => setToasts((p) => p.filter((x) => x.id !== id)), 3200);
  }, []);
  return { toasts, push };
}

Object.assign(window, {
  HudBox, Btn, Badge, StatusPill, Header, Sidebar, Footer,
  Toast, ToastViewport, useToasts, NAV_MAIN, NAV_ADMIN,
});
