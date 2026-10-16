import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { apiErrorMessage } from '../api';
import { useAuth } from '../auth';
import { ErrorBanner } from '../components/ui';

export default function SignupTenant() {
  const navigate = useNavigate();
  const { signupTenant } = useAuth();
  const [tenantName, setTenantName] = useState('');
  const [adminName, setAdminName] = useState('');
  const [adminEmail, setAdminEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await signupTenant({ tenantName, adminName, adminEmail, password });
      navigate('/admin');
    } catch (err) {
      setError(apiErrorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="page page-center">
      <div className="card card-narrow">
        <h1 className="title">Create your clinic</h1>
        <p className="muted">
          Set up a new clinic workspace and your administrator account.
        </p>
        <ErrorBanner message={error} />
        <form onSubmit={onSubmit} className="form">
          <label className="field">
            <span>Clinic name</span>
            <input
              value={tenantName}
              onChange={(e) => setTenantName(e.target.value)}
              placeholder="Sunrise Dental"
              required
            />
          </label>
          <label className="field">
            <span>Your full name</span>
            <input
              value={adminName}
              onChange={(e) => setAdminName(e.target.value)}
              placeholder="Jane Doe"
              required
            />
          </label>
          <label className="field">
            <span>Admin email</span>
            <input
              type="email"
              value={adminEmail}
              onChange={(e) => setAdminEmail(e.target.value)}
              placeholder="you@clinic.com"
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
              minLength={8}
            />
          </label>
          <button className="btn btn-primary btn-block" disabled={busy}>
            {busy ? 'Creating…' : 'Create clinic'}
          </button>
        </form>
        <p className="muted center" style={{ marginTop: '1rem' }}>
          <Link to="/" className="link">
            ← Back to clinic picker
          </Link>
        </p>
      </div>
    </div>
  );
}
