import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { apiErrorMessage } from '../api';
import { useAuth } from '../auth';
import { ErrorBanner } from '../components/ui';
import type { Role } from '../types';

const roleHome: Record<Role, string> = {
  TENANT_ADMIN: '/admin',
  PROVIDER: '/provider',
  CUSTOMER: '/book',
};

export default function Login() {
  const navigate = useNavigate();
  const { login, register, tenant } = useAuth();
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!tenant) {
      setError('Pick a clinic first (go back and enter a clinic slug).');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const res =
        mode === 'login'
          ? await login(email, password)
          : await register(email, password, fullName);
      navigate(roleHome[res.user.role]);
    } catch (err) {
      setError(apiErrorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="page page-center">
      <div className="card card-narrow">
        <h1 className="title">{mode === 'login' ? 'Sign in' : 'Create account'}</h1>
        <p className="muted">
          {tenant ? (
            <>
              Clinic: <strong>{tenant.name}</strong> ({tenant.slug})
            </>
          ) : (
            <>
              No clinic selected.{' '}
              <Link to="/" className="link">
                Pick one first
              </Link>
              .
            </>
          )}
        </p>
        <ErrorBanner message={error} />
        <form onSubmit={onSubmit} className="form">
          {mode === 'register' && (
            <label className="field">
              <span>Full name</span>
              <input
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                placeholder="Jane Doe"
                required
              />
            </label>
          )}
          <label className="field">
            <span>Email</span>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
              required
            />
          </label>
          <label className="field">
            <span>Password</span>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </label>
          <button className="btn btn-primary btn-block" disabled={busy || !tenant}>
            {busy ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}
          </button>
        </form>
        <p className="muted center" style={{ marginTop: '1rem' }}>
          {mode === 'login' ? (
            <>
              New here?{' '}
              <button className="linklike" onClick={() => setMode('register')}>
                Create a customer account
              </button>
            </>
          ) : (
            <>
              Already have an account?{' '}
              <button className="linklike" onClick={() => setMode('login')}>
                Sign in
              </button>
            </>
          )}
        </p>

        <div className="demo-box">
          <strong>Demo seed data</strong> — clinic <code>sunrise-dental</code>:
          <ul>
            <li>
              Admin: <code>admin@careslot.demo</code> / <code>Password123!</code>
            </li>
            <li>
              Provider: <code>provider@careslot.demo</code> / <code>Password123!</code>
            </li>
            <li>
              Customer: <code>customer@careslot.demo</code> / <code>Password123!</code>
            </li>
          </ul>
        </div>
      </div>
    </div>
  );
}
