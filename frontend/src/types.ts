// ---- Shared domain types matching the CareSlot API contract ----

export type Role = 'TENANT_ADMIN' | 'PROVIDER' | 'CUSTOMER';

export interface User {
  id: number;
  email: string;
  fullName: string;
  role: Role;
  tenantId: number;
}

export interface Tenant {
  id: number;
  name: string;
  slug: string;
}

export interface Tokens {
  accessToken: string;
  refreshToken: string;
}

export interface AuthResponse extends Tokens {
  user: User;
}

export interface SignupTenantResponse extends Tokens {
  tenantId: number;
  slug: string;
  user: User;
}

export interface Service {
  id: number;
  name: string;
  description: string;
  durationMinutes: number;
  priceCents: number;
}

export interface Provider {
  id: number;
  name: string;
  specialty: string;
}

export type SlotStatus = 'AVAILABLE' | 'BOOKED';

export interface Slot {
  slotId: number;
  startAt: string;
  endAt: string;
  status: SlotStatus;
  serviceId: number | null;
}

export interface Appointment {
  id: number;
  slotId: number;
  serviceId: number;
  providerId: number;
  startAt: string;
  endAt: string;
  status: string;
  paymentRef: string | null;
}

export interface ProviderAppointment {
  id: number;
  customerName: string;
  serviceName: string;
  startAt: string;
  endAt: string;
  status: string;
}

export interface WorkingDay {
  dayOfWeek: string;
  startTime: string;
  endTime: string;
}

export interface ApiError {
  error: string;
  message: string;
  status: number;
}
