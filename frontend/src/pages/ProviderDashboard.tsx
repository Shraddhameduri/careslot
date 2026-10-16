import { useEffect, useState, type FormEvent } from 'react';
import {
  apiErrorMessage,
  generateSlots,
  getProviderAppointments,
  getProviderSchedule,
  upsertWorkingDay,
} from '../api';
import { DAY_NAMES, ErrorBanner, Empty, Loading, SuccessBanner, formatTime, toISODate } from '../components/ui';
import type { ProviderAppointment, WorkingDay } from '../types';

export default function ProviderDashboard() {
  const [date, setDate] = useState(toISODate(new Date()));
  const [appts, setAppts] = useState<ProviderAppointment[]>([]);
  const [loadingAppts, setLoadingAppts] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [schedule, setSchedule] = useState<WorkingDay[]>([]);
  const [scheduleError, setScheduleError] = useState<string | null>(null);
  const [scheduleOk, setScheduleOk] = useState<string | null>(null);

  const [fromDate, setFromDate] = useState(toISODate(new Date()));
  const [toDate, setToDate] = useState(toISODate(new Date(Date.now() + 13 * 86400000)));
  const [genBusy, setGenBusy] = useState(false);
  const [genResult, setGenResult] = useState<string | null>(null);

  const loadAppts = async (d: string) => {
    setLoadingAppts(true);
    setError(null);
    try {
      setAppts(await getProviderAppointments(d));
    } catch (err) {
      setError(apiErrorMessage(err));
    } finally {
      setLoadingAppts(false);
    }
  };

  const loadSchedule = async () => {
    try {
      setSchedule(await getProviderSchedule());
    } catch (err) {
      setScheduleError(apiErrorMessage(err));
    }
  };

  useEffect(() => {
    loadAppts(date);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [date]);

  useEffect(() => {
    loadSchedule();
  }, []);

  const scheduleFor = (day: string): WorkingDay =>
    schedule.find((s) => s.dayOfWeek === day) ?? { dayOfWeek: day, startTime: '09:00', endTime: '17:00' };

  const editDay = (day: string, patch: Partial<WorkingDay>) => {
    const cur = scheduleFor(day);
    const next = { ...cur, ...patch };
    setSchedule((prev) => {
      const others = prev.filter((s) => s.dayOfWeek !== day);
      return [...others, next];
    });
  };

  const saveDay = async (day: string) => {
    setScheduleError(null);
    setScheduleOk(null);
    try {
      const saved = await upsertWorkingDay(scheduleFor(day));
      setSchedule((prev) => {
        const others = prev.filter((s) => s.dayOfWeek !== saved.dayOfWeek);
        return [...others, saved];
      });
      setScheduleOk(`${day}: hours saved.`);
    } catch (err) {
      setScheduleError(apiErrorMessage(err));
    }
  };

  const onGenerate = async (e: FormEvent) => {
    e.preventDefault();
    setGenBusy(true);
    setGenResult(null);
    setError(null);
    try {
      const res = await generateSlots(fromDate, toDate);
      setGenResult(`Generated ${res.generated} slot(s).`);
      loadAppts(date);
    } catch (err) {
      setError(apiErrorMessage(err));
    } finally {
      setGenBusy(false);
    }
  };

  return (
    <div className="page">
      <h1 className="title">Provider dashboard</h1>
      <ErrorBanner message={error} />
      <SuccessBanner message={genResult ?? scheduleOk} />

      <div className="dash-layout">
        <div className="card">
          <h2 className="section-title">Appointments</h2>
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
                    <th>Customer</th>
                    <th>Service</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {appts.map((a) => (
                    <tr key={a.id}>
                      <td>
                        {formatTime(a.startAt)}–{formatTime(a.endAt)}
                      </td>
                      <td>{a.customerName}</td>
                      <td>{a.serviceName}</td>
                      <td>
                        <span className={`badge badge-${a.status.toLowerCase()}`}>{a.status}</span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        <div className="card">
          <h2 className="section-title">Working hours</h2>
          <ErrorBanner message={scheduleError} />
          {DAY_NAMES.map((day) => {
            const row = scheduleFor(day);
            return (
              <div key={day} className="hours-row">
                <span className="day-label">{day.slice(0, 3)}</span>
                <input
                  type="time"
                  value={row.startTime}
                  onChange={(e) => editDay(day, { startTime: e.target.value })}
                />
                <input
                  type="time"
                  value={row.endTime}
                  onChange={(e) => editDay(day, { endTime: e.target.value })}
                />
                <button className="btn btn-ghost btn-sm" onClick={() => saveDay(day)}>
                  Save
                </button>
              </div>
            );
          })}
        </div>

        <div className="card">
          <h2 className="section-title">Generate slots</h2>
          <p className="muted">
            Creates bookable slots from your working hours for the selected date range.
          </p>
          <form onSubmit={onGenerate} className="form">
            <label className="field">
              <span>From</span>
              <input type="date" value={fromDate} onChange={(e) => setFromDate(e.target.value)} required />
            </label>
            <label className="field">
              <span>To</span>
              <input type="date" value={toDate} onChange={(e) => setToDate(e.target.value)} required />
            </label>
            <button className="btn btn-primary btn-block" disabled={genBusy}>
              {genBusy ? 'Generating…' : 'Generate slots'}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}
