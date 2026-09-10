const icons = {
  bag: <><path d="M6.75 8.25h10.5l.75 11.25H6L6.75 8.25Z"/><path d="M9 8.25V6a3 3 0 0 1 6 0v2.25"/></>,
  search: <><circle cx="11" cy="11" r="5.75"/><path d="m15.5 15.5 4 4"/></>,
  home: <><path d="m3.75 10.5 8.25-6.75 8.25 6.75"/><path d="M5.5 9.75v9h13v-9"/><path d="M9.5 18.75v-5.25h5v5.25"/></>,
  cart: <><path d="M3.75 5.25h2l1.35 8.1a2 2 0 0 0 1.97 1.65h7.95a2 2 0 0 0 1.95-1.55l1.03-4.7H6.3"/><circle cx="9.25" cy="18.5" r="1"/><circle cx="17.25" cy="18.5" r="1"/></>,
  box: <><path d="M4.25 7.5 12 3.5l7.75 4v9L12 20.5l-7.75-4v-9Z"/><path d="m4.25 7.5 7.75 4 7.75-4M12 11.5v9"/></>,
  user: <><circle cx="12" cy="8" r="3.25"/><path d="M5.75 19.5c.8-3.2 3.1-5 6.25-5s5.45 1.8 6.25 5"/></>,
  refund: <><path d="M5 7.5h10.25a4 4 0 0 1 0 8H8"/><path d="m8 4.5-3 3 3 3"/><path d="M8.25 12.25h4.5"/><path d="M10.5 10v4.5"/></>,
  pin: <><path d="M12 21s6-5.25 6-11a6 6 0 1 0-12 0c0 5.75 6 11 6 11Z"/><circle cx="12" cy="10" r="2"/></>,
  key: <><circle cx="8.5" cy="12" r="3.25"/><path d="M11.5 12H20M16.5 12v2.25M19 12v2.25"/></>,
  logout: <><path d="M10.5 5H6.75A1.75 1.75 0 0 0 5 6.75v10.5A1.75 1.75 0 0 0 6.75 19h3.75"/><path d="M14.25 8 18 12l-3.75 4M18 12H9.5"/></>,
  chevron: <path d="m9 6 6 6-6 6"/>,
  lock: <><rect x="6" y="10" width="12" height="9" rx="2"/><path d="M8.5 10V7.75a3.5 3.5 0 0 1 7 0V10"/></>
}

export default function UiIcon({ name, size = 22, className = '' }) {
  return (
    <svg
      className={className}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {icons[name] || icons.bag}
    </svg>
  )
}
