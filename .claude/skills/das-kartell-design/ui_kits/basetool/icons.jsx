/* KRT icon sprite + <Icon> — in-house 24px line set, currentColor. */
const KRT_SPRITE = (
  <svg className="krt-icon-sprite" aria-hidden="true" style={{position:'absolute',width:0,height:0}}>
    <symbol id="krt-icon-close" viewBox="0 0 24 24"><path d="M6 6l12 12M18 6L6 18" stroke="currentColor" strokeWidth="2" strokeLinecap="round" fill="none"/></symbol>
    <symbol id="krt-icon-chevron-down" viewBox="0 0 24 24"><path d="M6 9l6 6 6-6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" fill="none"/></symbol>
    <symbol id="krt-icon-chevron-right" viewBox="0 0 24 24"><path d="M9 6l6 6-6 6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" fill="none"/></symbol>
    <symbol id="krt-icon-warning" viewBox="0 0 24 24"><path d="M12 3l10 18H2L12 3z" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" fill="none"/><path d="M12 10v5M12 18v0.01" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/></symbol>
    <symbol id="krt-icon-success" viewBox="0 0 24 24"><path d="M5 12l5 5 9-11" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" fill="none"/></symbol>
    <symbol id="krt-icon-info" viewBox="0 0 24 24"><circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="2" fill="none"/><path d="M12 11v6M12 7v0.01" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/></symbol>
    <symbol id="krt-icon-plus" viewBox="0 0 24 24"><path d="M12 5v14M5 12h14" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/></symbol>
    <symbol id="krt-icon-search" viewBox="0 0 24 24"><circle cx="11" cy="11" r="6" stroke="currentColor" strokeWidth="2" fill="none"/><path d="M20 20l-4-4" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/></symbol>
    <symbol id="krt-icon-filter" viewBox="0 0 24 24"><path d="M4 5h16l-6 8v6l-4-2v-4z" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" fill="none"/></symbol>
    <symbol id="krt-icon-edit" viewBox="0 0 24 24"><path d="M14 4l6 6-11 11H3v-6z" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" fill="none"/></symbol>
    <symbol id="krt-icon-trash" viewBox="0 0 24 24"><path d="M5 7h14M9 7V4h6v3M7 7l1 14h8l1-14" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" fill="none"/></symbol>
    <symbol id="krt-icon-ship" viewBox="0 0 24 24"><path d="M3 12l18-6-7 6 7 6-18-6z" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" fill="none"/></symbol>
    <symbol id="krt-icon-mission" viewBox="0 0 24 24"><circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="2" fill="none"/><path d="M12 3v4M12 17v4M3 12h4M17 12h4" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/></symbol>
    <symbol id="krt-icon-box" viewBox="0 0 24 24"><path d="M3 7l9-4 9 4v10l-9 4-9-4z M3 7l9 4 9-4 M12 11v10" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" fill="none"/></symbol>
  </svg>
);

function Icon({ name, size, className }) {
  const cls = "krt-icon" + (size === "lg" ? " krt-icon-lg" : size === "xl" ? " krt-icon-xl" : "") + (className ? " " + className : "");
  return <svg className={cls} aria-hidden="true"><use href={"#krt-icon-" + name} /></svg>;
}

Object.assign(window, { KRT_SPRITE, Icon });
