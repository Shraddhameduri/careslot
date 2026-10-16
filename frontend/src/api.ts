import axios, { AxiosError, type AxiosRequestConfig } from 'axios';
import type {
  ApiError,
  Appointment,
  AuthResponse,
  Provider,
  ProviderAppointment,
  Service,
  SignupTenantResponse,
  Slot,
  Tenant,
  Tokens,
  User,
  WorkingDay,
} from './types';

// ---------- localStorage keys ----------
const TENANT_KEY = 'careslot_tenant'; // JSON: {id, slug, name}
const ACCESS_KEY = 'careslot_access';
const REFRESH_KEY = 'careslot_refresh';
const USER_KEY = 'careslot_user';

export interface TenantRef {
  id: number;
  slug: string;
  name: string;
}

export function loadTenant(): TenantRef | null {
  try {
    const raw = localStorage.getItem(TENANT_KEY);
    return raw ? (JSON.parse(raw) as TenantRef) : null;
  } catch {
    return null;
  }
}

export function saveTenant(t: TenantRef): void {
  localStorage.setItem(TENANT_KEY, JSON.stringify(t));
}

export function clearTenant(): void {
  localStorage.removeItem(TENANT_KEY);
}

export function loadUser(): User | null {
  try {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? (JSON.parse(raw) as User) : null;
  } catch {
    return null;
  }
}

export function saveAuth(tokens: Tokens, user: User): void {
  localStorage.setItem(ACCESS_KEY, tokens.accessToken);
  localStorage.setItem(REFRESH_KEY, tokens.refreshToken);
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function clearAuth(): void {
  localStorage.removeItem(ACCESS_KEY);
  localStorage.removeItem(REFRESH_KEY);
  localStorage.removeItem(USER_KEY);
}

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS_KEY);
}

// ---------- axios client ----------
// Same-origin; vite dev proxy forwards /api -> http://localhost:8080
export const api = axios.create({ baseURL: '/api/v1' });

api.interceptors.request.use((config) => {
  const tenant = loadTenant();
  if (tenant) {
    config.headers['X-Tenant-Id'] = String(tenant.id);
  }
  const token = getAccessToken();
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (res) => res,
  (err: AxiosError<ApiError>) => {
    if (err?.response?.status === 401) {
      // Session invalid: drop credentials and send the user to login.
      clearAuth();
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login';
      }
    }
    return Promise.reject(err);
  }
);

export function apiErrorMessage(err: unknown): string {
  if (axios.isAxiosError<ApiError>(err)) {
    return err.response?.data?.message || err.message || 'Request failed';
  }
  return err instanceof Error ? err.message : 'Request failed';
}

export function buildUrl(path: string, params: Record<string, string | number | undefined>): string {
  const qs = Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== '')
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
    .join('&');
  return qs ? `${path}?${qs}` : path;
}

// ---------- auth ----------
export async function signupTenant(payload: {
  tenantName: string;
  adminName: string;
  adminEmail: string;
  password: string;
}): Promise<SignupTenantResponse> {
  const { data } = await api.post<SignupTenantResponse>('/auth/signup-tenant', payload);
  return data;
}

export async function register(payload: {
  email: string;
  password: string;
  fullName: string;
}): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>('/auth/register', payload);
  return data;
}

export async function login(payload: { email: string; password: string }): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>('/auth/login', payload);
  return data;
}

export async function refreshTokens(refreshToken: string): Promise<Tokens> {
  const { data } = await api.post<Tokens>('/auth/refresh', { refreshToken });
  return data;
}

export async function logout(refreshToken: string): Promise<void> {
  try {
    await api.post('/auth/logout', { refreshToken });
  } finally {
    clearAuth();
  }
}

// ---------- public ----------
export async function getTenantBySlug(slug: string): Promise<Tenant> {
  const { data } = await api.get<Tenant>(`/public/tenants/${encodeURIComponent(slug)}`);
  return data;
}

export async function getServices(slug: string): Promise<Service[]> {
  const { data } = await api.get<Service[]>(buildUrl('/public/services', { tenant: slug }));
  return data;
}

export async function getProviders(slug: string): Promise<Provider[]> {
  const { data } = await api.get<Provider[]>(buildUrl('/public/providers', { tenant: slug }));
  return data;
}

export async function getAvailability(
  slug: string,
  providerId: number | undefined,
  date: string
): Promise<Slot[]> {
  const { data } = await api.get<Slot[]>(
    buildUrl('/public/availability', { tenant: slug, providerId, date })
  );
  return data;
}

export function availabilityStreamUrl(
  slug: string,
  providerId: number | undefined,
  date: string
): string {
  return buildUrl('/api/v1/public/availability/stream', { tenant: slug, providerId, date });
}

// ---------- bookings (CUSTOMER) ----------
export async function createBooking(payload: {
  slotId: number;
  serviceId: number;
  notes?: string;
}): Promise<Appointment> {
  const { data } = await api.post<Appointment>('/bookings', payload);
  return data;
}

export async function getMyBookings(): Promise<Appointment[]> {
  const { data } = await api.get<Appointment[]>('/bookings/mine');
  return data;
}

export async function cancelBooking(id: number): Promise<void> {
  await api.post(`/bookings/${id}/cancel`);
}

// ---------- provider ----------
export async function getProviderAppointments(date: string): Promise<ProviderAppointment[]> {
  const { data } = await api.get<ProviderAppointment[]>(
    buildUrl('/provider/appointments', { date })
  );
  return data;
}

export async function getProviderSchedule(): Promise<WorkingDay[]> {
  const { data } = await api.get<WorkingDay[]>('/provider/schedule');
  return data;
}

export async function upsertWorkingDay(day: WorkingDay): Promise<WorkingDay> {
  const { data } = await api.put<WorkingDay>('/provider/schedule', day);
  return data;
}

export async function generateSlots(fromDate: string, toDate: string): Promise<{ generated: number }> {
  const { data } = await api.post<{ generated: number }>('/provider/slots/generate', {
    fromDate,
    toDate,
  });
  return data;
}

// ---------- admin ----------
export async function getAdminServices(): Promise<Service[]> {
  const { data } = await api.get<Service[]>('/admin/services');
  return data;
}

export async function createAdminService(s: {
  name: string;
  description: string;
  durationMinutes: number;
  priceCents: number;
}): Promise<Service> {
  const { data } = await api.post<Service>('/admin/services', s);
  return data;
}

export async function updateAdminService(id: number, s: Partial<Service>): Promise<Service> {
  const { data } = await api.put<Service>(`/admin/services/${id}`, s);
  return data;
}

export async function deleteAdminService(id: number): Promise<void> {
  await api.delete(`/admin/services/${id}`);
}

export async function getAdminProviders(): Promise<Provider[]> {
  const { data } = await api.get<Provider[]>('/admin/providers');
  return data;
}

export async function createAdminProvider(p: {
  name: string;
  specialty: string;
  email: string;
  password: string;
}): Promise<Provider> {
  const { data } = await api.post<Provider>('/admin/providers', p);
  return data;
}

export async function getAdminAppointments(date: string): Promise<Appointment[]> {
  const { data } = await api.get<Appointment[]>(buildUrl('/admin/appointments', { date }));
  return data;
}

// Re-export axios config type for convenience
export type { AxiosRequestConfig };
