import { useAuth } from '../auth';
import { useNavigate, Link } from 'react-router-dom';

export function formatPrice(priceCents: number): string {
  return `$${(priceCents / 100).toFixed(2)}`;
}

export function formatTime(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
}

export function formatDate(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleDateString([], { weekday: 'short', month: 'short', day: 'numeric' });
}

export function toISODate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

/** Next N days starting today, as YYYY-MM-DD strings. */
export function nextDays(n: number): string[] {
  const out: string[] = [];
  const today = new Date();
  for (let i = 0; i < n; i++) {
    const d = new Date(today);
    d.setDate(today.getDate() + i);
    out.push(toISODate(d));
  }
  return out;
}

export const DAY_NAMES = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
] as const;

export function Header() {
  const { user, tenant, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <header className="topbar">
      <Link to="/" className="brand">
        <span className="brand-mark">◈</span> CareSlot
      </Link>
      {tenant && <span className="tenant-chip">{tenant.name}</span>}
      <nav className="topnav">
        {user?.role === 'CUSTOMER' && <Link to="/book">Book</Link>}
        {user?.role === 'PROVIDER' && <Link to="/provider">Schedule</Link>}
        {user?.role === 'TENANT_ADMIN' && <Link to="/admin">Admin</Link>}
        {user ? (
          <>
            <span className="user-label">{user.fullName}</span>
            <button className="btn btn-ghost" onClick={handleLogout}>
              Sign out
            </button>
          </>
        ) : (
          <Link to="/login" className="btn btn-ghost">
            Sign in
          </Link>
        )}
      </nav>
    </header>
  );
}

export function ErrorBanner({ message }: { message: string | null }) {
  if (!message) return null;
  return <div className="alert alert-error">{message}</div>;
}

export function SuccessBanner({ message }: { message: string | null }) {
  if (!message) return null;
  return <div className="alert alert-success">{message}</div>;
}

export function Loading({ label = 'Loading…' }: { label?: string }) {
  return <div className="loading">{label}</div>;
}

export function Empty({ text }: { text: string }) {
  return <div className="empty">{text}</div>;
}
