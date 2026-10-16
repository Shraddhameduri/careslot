import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { apiErrorMessage, getTenantBySlug } from '../api';
import { useAuth } from '../auth';
import { ErrorBanner } from '../components/ui';

export default function TenantPicker() {
  const navigate = useNavigate();
  const { setTenantRef, tenant } = useAuth();
  const [slug, setSlug] = useState(tenant?.slug ?? 'sunrise-dental');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    const clean = slug.trim().toLowerCase();
    if (!clean) {
      setError('Enter a clinic slug.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const t = await getTenantBySlug(clean);
      setTenantRef({ id: t.id, slug: t.slug, name: t.name });
      navigate('/book');
    } catch (err) {
      setError(apiErrorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="page page-center">
      <div className="card card-narrow">
        <h1 className="title">Find your clinic</h1>
        <p className="muted">
          Enter the clinic's web address name (slug) to book an appointment.
        </p>
        <ErrorBanner message={error} />
        <form onSubmit={onSubmit} className="form">
          <label className="field">
            <span>Clinic slug</span>
            <input
              value={slug}
              onChange={(e) => setSlug(e.target.value)}
              placeholder="sunrise-dental"
              autoComplete="off"
            />
          </label>
          <button className="btn btn-primary btn-block" disabled={busy}>
            {busy ? 'Checking…' : 'Continue'}
          </button>
        </form>
        <p className="muted center" style={{ marginTop: '1rem' }}>
          Running a clinic?{' '}
          <Link to="/signup-tenant" className="link">
            Create a new clinic
          </Link>
        </p>
      </div>
    </div>
  );
}
