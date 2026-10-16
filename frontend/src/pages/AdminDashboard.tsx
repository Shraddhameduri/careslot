import { useEffect, useState, type FormEvent } from 'react';
import {
  apiErrorMessage,
  createAdminProvider,
  createAdminService,
  deleteAdminService,
  getAdminAppointments,
  getAdminProviders,
  getAdminServices,
  updateAdminService,
} from '../api';
import { ErrorBanner, Empty, Loading, SuccessBanner, formatDate, formatPrice, formatTime, toISODate } from '../components/ui';
import type { Appointment, Provider, Service } from '../types';

type Tab = 'services' | 'providers' | 'appointments';

const emptyService = { name: '', description: '', durationMinutes: 30, priceCents: 0 };

export default function AdminDashboard() {
  const [tab, setTab] = useState<Tab>('services');
  const [error, setError] = useState<string | null>(null);
  const [ok, setOk] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // services
  const [services, setServices] = useState<Service[]>([]);
  const [loadingServices, setLoadingServices] = useState(true);
  const [form, setForm] = useState(emptyService);
  const [editingId, setEditingId] = useState<number | null>(null);

  // providers
  const [providers, setProviders] = useState<Provider[]>([]);
  const [loadingProviders, setLoadingProviders] = useState(true);
  const [pForm, setPForm] = useState({ name: '', specialty: '', email: '', password: '' });

  // appointments
  const [date, setDate] = useState(toISODate(new Date()));
  const [appts, setAppts] = useState<Appointment[]>([]);
  const [loadingAppts, setLoadingAppts] = useState(true);

  const loadServices = async () => {
    setLoadingServices(true);
    try {
      setServices(await getAdminServices());
    } catch (err) {
      setError(apiErrorMessage(err));
    } finally {
      setLoadingServices(false);
    }
  };
  const loadProviders = async () => {
    setLoadingProviders(true);
    try {
      setProviders(await getAdminProviders());
    } catch (err) {
      setError(apiErrorMessage(err));
    } finally {
      setLoadingProviders(false);
    }
  };
  const loadAppts = async (d: string) => {
    setLoadingAppts(true);
    try {
      setAppts(await getAdminAppointments(d));
    } catch (err) {
      setError(apiErrorMessage(err));
    } finally {
      setLoadingAppts(false);
    }
  };

  useEffect(() => {
    loadServices();
    loadProviders();
  }, []);
  useEffect(() => {
    loadAppts(date);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [date]);

  // ---- service handlers ----
  const startEdit = (s: Service) => {
    setEditingId(s.id);
    setForm({
      name: s.name,
      description: s.description,
      durationMinutes: s.durationMinutes,
      priceCents: s.priceCents,
    });
  };
  const cancelEdit = () => {
    setEditingId(null);
    setForm(emptyService);
  };

  const submitService = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setOk(null);
    try {
      if (editingId !== null) {
        await updateAdminService(editingId, form);
        setOk('Service updated.');
      } else {
        await createAdminService(form);
        setOk('Service created.');
      }
      cancelEdit();
      loadServices();
    } catch (err) {
      setError(apiErrorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  const removeService = async (id: number) => {
    if (!window.confirm('Delete this service?')) return;
    setError(null);
    try {
      await deleteAdminService(id);
      setOk('Service deleted.');
      loadServices();
    } catch (err) {
      setError(apiErrorMessage(err));
    }
  };

  // ---- provider handlers ----
  const submitProvider = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setOk(null);
    try {
      await createAdminProvider(pForm);
      setOk('Provider created (with login account).');
      setPForm({ name: '', specialty: '', email: '', password: '' });
      loadProviders();
    } catch (err) {
      setError(apiErrorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="page">
      <h1 className="title">Clinic administration</h1>
      <ErrorBanner message={error} />
      <SuccessBanner message={ok} />

      <div className="tabs">
        {(['services', 'providers', 'appointments'] as Tab[]).map((t) => (
          <button
            key={t}
            className={`tab${tab === t ? ' active' : ''}`}
            onClick={() => setTab(t)}
          >
            {t[0].toUpperCase() + t.slice(1)}
          </button>
        ))}
      </div>

      {tab === 'services' && (
        <div className="card">
          <h2 className="section-title">{editingId !== null ? 'Edit service' : 'Add service'}</h2>
          <form onSubmit={submitService} className="form form-grid">
            <label className="field">
              <span>Name</span>
              <input
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                required
              />
            </label>
            <label className="field">
              <span>Duration (minutes)</span>
              <input
                type="number"
                min={5}
                value={form.durationMinutes}
                onChange={(e) => setForm({ ...form, durationMinutes: Number(e.target.value) })}
                required
              />
            </label>
            <label className="field">
              <span>Price (cents)</span>
              <input
                type="number"
                min={0}
                value={form.priceCents}
                onChange={(e) => setForm({ ...form, priceCents: Number(e.target.value) })}
                required
              />
            </label>
            <label className="field field-wide">
              <span>Description</span>
              <textarea
                value={form.description}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
                rows={2}
              />
            </label>
            <div className="field-wide row">
              <button className="btn btn-primary" disabled={busy}>
                {busy ? 'Saving…' : editingId !== null ? 'Save changes' : 'Add service'}
              </button>
              {editingId !== null && (
                <button type="button" className="btn btn-ghost" onClick={cancelEdit}>
                  Cancel
                </button>
              )}
            </div>
          </form>

          <h2 className="section-title">Services</h2>
          {loadingServices ? (
            <Loading />
          ) : services.length === 0 ? (
            <Empty text="No services yet." />
          ) : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Duration</th>
                    <th>Price</th>
                    <th>Description</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {services.map((s) => (
                    <tr key={s.id}>
                      <td>{s.name}</td>
                      <td>{s.durationMinutes} min</td>
                      <td>{formatPrice(s.priceCents)}</td>
                      <td className="muted small">{s.description}</td>
                      <td className="row">
                        <button className="btn btn-ghost btn-sm" onClick={() => startEdit(s)}>
                          Edit
                        </button>
                        <button className="btn btn-ghost btn-sm danger" onClick={() => removeService(s.id)}>
                          Delete
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {tab === 'providers' && (
        <div className="card">
          <h2 className="section-title">Add provider</h2>
          <form onSubmit={submitProvider} className="form form-grid">
            <label className="field">
              <span>Name</span>
              <input
                value={pForm.name}
                onChange={(e) => setPForm({ ...pForm, name: e.target.value })}
                required
              />
            </label>
            <label className="field">
              <span>Specialty</span>
              <input
                value={pForm.specialty}
                onChange={(e) => setPForm({ ...pForm, specialty: e.target.value })}
              />
            </label>
            <label className="field">
              <span>Login email</span>
              <input
                type="email"
                value={pForm.email}
                onChange={(e) => setPForm({ ...pForm, email: e.target.value })}
                required
              />
            </label>
            <label className="field">
              <span>Password</span>
              <input
                type="password"
                value={pForm.password}
                onChange={(e) => setPForm({ ...pForm, password: e.target.value })}
                required
                minLength={8}
              />
            </label>
            <div className="field-wide">
              <button className="btn btn-primary" disabled={busy}>
                {busy ? 'Creating…' : 'Add provider'}
              </button>
            </div>
          </form>

          <h2 className="section-title">Providers</h2>
          {loadingProviders ? (
            <Loading />
          ) : providers.length === 0 ? (
            <Empty text="No providers yet." />
          ) : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Specialty</th>
                  </tr>
                </thead>
                <tbody>
                  {providers.map((p) => (
                    <tr key={p.id}>
                      <td>{p.name}</td>
                      <td>{p.specialty}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {tab === 'appointments' && (
        <div className="card">
          <h2 className="section-title">All appointments</h2>
          <label className="field field-inline">
            <span>Date</span>
            <input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
          </label>
          {loadingAppts ? (
            <Loading />
          ) : appts.length === 0 ? (
            <Empty text="No appointments on this day." />
          ) : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>Time</th>
                    <th>Status</th>
                    <th>Provider</th>
                    <th>Payment ref</th>
                  </tr>
                </thead>
                <tbody>
                  {appts.map((a) => (
                    <tr key={a.id}>
                      <td>
                        {formatDate(a.startAt)} {formatTime(a.startAt)}–{formatTime(a.endAt)}
                      </td>
                      <td>
                        <span className={`badge badge-${a.status.toLowerCase()}`}>{a.status}</span>
                      </td>
                      <td>#{a.providerId}</td>
                      <td className="muted small">{a.paymentRef ?? '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
