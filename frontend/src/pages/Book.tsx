import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  apiErrorMessage,
  availabilityStreamUrl,
  cancelBooking,
  createBooking,
  getAvailability,
  getMyBookings,
  getProviders,
  getServices,
} from '../api';
import { useAuth } from '../auth';
import { ErrorBanner, Empty, Loading, SuccessBanner, formatDate, formatPrice, formatTime, nextDays } from '../components/ui';
import type { Appointment, Provider, Service, Slot } from '../types';

const DATE_OPTIONS = nextDays(14);

export default function Book() {
  const { tenant, user } = useAuth();

  const [services, setServices] = useState<Service[]>([]);
  const [providers, setProviders] = useState<Provider[]>([]);
  const [slots, setSlots] = useState<Slot[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingSlots, setLoadingSlots] = useState(false);

  const [serviceId, setServiceId] = useState<number | null>(null);
  const [providerId, setProviderId] = useState<number | null>(null);
  const [date, setDate] = useState<string>(DATE_OPTIONS[0]);
  const [slotId, setSlotId] = useState<number | null>(null);
  const [notes, setNotes] = useState('');
  const [booking, setBooking] = useState(false);
  const [booked, setBooked] = useState<Appointment | null>(null);

  const [mine, setMine] = useState<Appointment[]>([]);
  const [mineError, setMineError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const esRef = useRef<EventSource | null>(null);

  // ---- load services + providers once per tenant ----
  useEffect(() => {
    if (!tenant) {
      setLoading(false);
      return;
    }
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const [svcs, provs] = await Promise.all([
          getServices(tenant.slug),
          getProviders(tenant.slug),
        ]);
        setServices(svcs);
        setProviders(provs);
        setServiceId(svcs[0]?.id ?? null);
        setProviderId(provs[0]?.id ?? null);
      } catch (err) {
        setError(apiErrorMessage(err));
      } finally {
        setLoading(false);
      }
    })();
  }, [tenant]);

  // ---- load slots for provider/date ----
  const loadSlots = useCallback(async () => {
    if (!tenant || !providerId || !date) return;
    setLoadingSlots(true);
    try {
      const s = await getAvailability(tenant.slug, providerId, date);
      setSlots(s);
      if (slotId !== null && !s.some((x) => x.slotId === slotId && x.status === 'AVAILABLE')) {
        setSlotId(null);
      }
    } catch (err) {
      setError(apiErrorMessage(err));
    } finally {
      setLoadingSlots(false);
    }
  }, [tenant, providerId, date, slotId]);

  useEffect(() => {
    loadSlots();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [providerId, date]);

  // ---- live slot-status updates via SSE ----
  useEffect(() => {
    if (!tenant || !providerId || !date) return;
    esRef.current?.close();
    const es = new EventSource(availabilityStreamUrl(tenant.slug, providerId, date));
    es.onmessage = (ev) => {
      try {
        const msg = JSON.parse(ev.data) as { slotId: number; status: Slot['status'] };
        setSlots((prev) =>
          prev.map((s) => (s.slotId === msg.slotId ? { ...s, status: msg.status } : s))
        );
        if (msg.status !== 'AVAILABLE') {
          setSlotId((cur) => (cur === msg.slotId ? null : cur));
        }
      } catch {
        // ignore malformed events
      }
    };
    es.onerror = () => {
      // transient network hiccup: close; the next provider/date change re-opens
      es.close();
    };
    esRef.current = es;
    return () => {
      es.close();
      esRef.current = null;
    };
  }, [tenant, providerId, date]);

  // ---- my bookings ----
  const loadMine = useCallback(async () => {
    if (!user) return;
    try {
      setMine(await getMyBookings());
      setMineError(null);
    } catch (err) {
      setMineError(apiErrorMessage(err));
    }
  }, [user]);

  useEffect(() => {
    loadMine();
  }, [loadMine]);

  const handleBook = async () => {
    if (!slotId || !serviceId) return;
    setBooking(true);
    setError(null);
    try {
      const appt = await createBooking({ slotId, serviceId, notes: notes || undefined });
      setBooked(appt);
      setSlotId(null);
      setNotes('');
      loadMine();
    } catch (err) {
      setError(apiErrorMessage(err));
    } finally {
      setBooking(false);
    }
  };

  const handleCancel = async (id: number) => {
    try {
      await cancelBooking(id);
      loadMine();
    } catch (err) {
      setMineError(apiErrorMessage(err));
    }
  };

  if (!tenant) {
    return (
      <div className="page">
        <Empty text="No clinic selected." />
        <p className="center">
          <Link to="/" className="link">
            Pick your clinic
          </Link>
        </p>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="page">
        <Loading />
      </div>
    );
  }

  const selectedService = services.find((s) => s.id === serviceId);
  const selectedProvider = providers.find((p) => p.id === providerId);
  const selectedSlot = slots.find((s) => s.slotId === slotId);

  return (
    <div className="page">
      <h1 className="title">Book an appointment</h1>
      <ErrorBanner message={error} />
      <SuccessBanner
        message={
          booked
            ? `Booked! ${formatDate(booked.startAt)} at ${formatTime(booked.startAt)}${booked.paymentRef ? ` — payment ref ${booked.paymentRef}` : ''}`
            : null
        }
      />

      <div className="book-layout">
        <div className="card">
          <h2 className="section-title">
            <span className="step">1</span> Choose a service
          </h2>
          {services.length === 0 ? (
            <Empty text="No services offered yet." />
          ) : (
            <div className="pick-list">
              {services.map((s) => (
                <button
                  key={s.id}
                  className={`pick-item${serviceId === s.id ? ' selected' : ''}`}
                  onClick={() => setServiceId(s.id)}
                >
                  <strong>{s.name}</strong>
                  <span className="muted">
                    {s.durationMinutes} min · {formatPrice(s.priceCents)}
                  </span>
                  {s.description && <span className="muted small">{s.description}</span>}
                </button>
              ))}
            </div>
          )}

          <h2 className="section-title">
            <span className="step">2</span> Choose a provider
          </h2>
          {providers.length === 0 ? (
            <Empty text="No providers available." />
          ) : (
            <div className="pick-list">
              {providers.map((p) => (
                <button
                  key={p.id}
                  className={`pick-item${providerId === p.id ? ' selected' : ''}`}
                  onClick={() => {
                    setProviderId(p.id);
                    setSlotId(null);
                  }}
                >
                  <strong>{p.name}</strong>
                  {p.specialty && <span className="muted">{p.specialty}</span>}
                </button>
              ))}
            </div>
          )}

          <h2 className="section-title">
            <span className="step">3</span> Choose a date
          </h2>
          <div className="date-row">
            {DATE_OPTIONS.map((d) => (
              <button
                key={d}
                className={`date-chip${date === d ? ' selected' : ''}`}
                onClick={() => {
                  setDate(d);
                  setSlotId(null);
                }}
              >
                <span className="dow">{new Date(`${d}T12:00:00`).toLocaleDateString([], { weekday: 'short' })}</span>
                <span className="dom">{new Date(`${d}T12:00:00`).getDate()}</span>
              </button>
            ))}
          </div>

          <h2 className="section-title">
            <span className="step">4</span> Pick a time{' '}
            <span className="live-dot" title="Live updates" />
          </h2>
          {loadingSlots ? (
            <Loading label="Loading slots…" />
          ) : slots.length === 0 ? (
            <Empty text="No slots on this day. Try another date or provider." />
          ) : (
            <div className="slot-grid">
              {slots.map((s) => (
                <button
                  key={s.slotId}
                  disabled={s.status !== 'AVAILABLE'}
                  className={`slot${slotId === s.slotId ? ' selected' : ''}${s.status !== 'AVAILABLE' ? ' taken' : ''}`}
                  onClick={() => setSlotId(s.slotId)}
                >
                  {formatTime(s.startAt)}
                </button>
              ))}
            </div>
          )}

          <h2 className="section-title">
            <span className="step">5</span> Confirm
          </h2>
          <div className="summary">
            <div>
              <span className="muted">Service</span>
              <strong>{selectedService?.name ?? '—'}</strong>
            </div>
            <div>
              <span className="muted">Provider</span>
              <strong>{selectedProvider?.name ?? '—'}</strong>
            </div>
            <div>
              <span className="muted">When</span>
              <strong>
                {selectedSlot
                  ? `${formatDate(selectedSlot.startAt)} · ${formatTime(selectedSlot.startAt)}–${formatTime(selectedSlot.endAt)}`
                  : '—'}
              </strong>
            </div>
            <div>
              <span className="muted">Price</span>
              <strong>{selectedService ? formatPrice(selectedService.priceCents) : '—'}</strong>
            </div>
          </div>
          <label className="field">
            <span>Notes (optional)</span>
            <textarea
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="Anything the provider should know…"
              rows={2}
            />
          </label>
          {!user && (
            <p className="muted">
              <Link to="/login" className="link">
                Sign in
              </Link>{' '}
              to confirm your booking.
            </p>
          )}
          <button
            className="btn btn-primary btn-block"
            disabled={!user || !slotId || !serviceId || booking}
            onClick={handleBook}
          >
            {booking ? 'Booking…' : 'Confirm booking'}
          </button>
        </div>

        {user && (
          <div className="card">
            <h2 className="section-title">My bookings</h2>
            <ErrorBanner message={mineError} />
            {mine.length === 0 ? (
              <Empty text="No bookings yet." />
            ) : (
              <ul className="booking-list">
                {mine.map((b) => (
                  <li key={b.id} className="booking-row">
                    <div>
                      <strong>
                        {formatDate(b.startAt)} · {formatTime(b.startAt)}–{formatTime(b.endAt)}
                      </strong>
                      <div className="muted small">
                        Status: {b.status}
                        {b.paymentRef ? ` · Ref ${b.paymentRef}` : ''}
                      </div>
                    </div>
                    {b.status !== 'CANCELLED' && (
                      <button
                        className="btn btn-ghost btn-sm"
                        onClick={() => handleCancel(b.id)}
                      >
                        Cancel
                      </button>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
