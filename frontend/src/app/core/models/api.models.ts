/**
 * The API contract, typed.
 *
 * These interfaces mirror the backend DTOs one-to-one. Keeping them in a single file
 * means a backend change breaks the build in one obvious place instead of failing
 * silently at runtime in five components — the whole reason to use TypeScript rather
 * than sprinkling `any` over HTTP responses.
 */

export type Role = 'MEMBER' | 'MANAGER' | 'ADMIN';
export type Sport = 'PADEL' | 'TENNIS' | 'SQUASH';
export type BookingStatus = 'HOLD' | 'CONFIRMED' | 'CANCELLED' | 'NO_SHOW';

// ---------- auth ----------

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface Me {
  email: string;
  fullName: string;
  role: Role;
}

// ---------- clubs & courts ----------

export interface Club {
  id: number;
  name: string;
  city: string;
  address: string;
  managerId: number | null;
  managerName: string | null;
}

export interface Court {
  id: number;
  clubId: number;
  name: string;
  sport: Sport;
  slotMinutes: number;
  pricePerSlot: number;
  active: boolean;
}

export interface OpeningHours {
  id: number;
  dayOfWeek: number; // 1 = Monday ... 7 = Sunday (ISO)
  opens: string;     // 'HH:mm:ss'
  closes: string;
}

/** Mirrors the backend's PageResponse — a stable contract, not Spring's Page. */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

// ---------- availability & bookings ----------

export interface Slot {
  start: string;  // ISO-8601 with offset
  end: string;
  available: boolean;
  price: number;
}

export interface Availability {
  courtId: number;
  courtName: string;
  date: string;
  clubOpen: boolean;
  slots: Slot[];
}

export interface Booking {
  id: number;
  courtId: number;
  courtName: string;
  clubName: string;
  start: string;
  end: string;
  status: BookingStatus;
  price: number;
  createdAt: string;
}

export interface BookingRequest {
  courtId: number;
  start: string;
  end: string;
}

// ---------- realtime ----------

export interface AvailabilityEvent {
  type: 'SLOT_BOOKED' | 'SLOT_RELEASED';
  clubId: number;
  courtId: number;
  date: string;
  slotStart: string;
  slotEnd: string;
}

// ---------- errors ----------

/** The single error shape the API returns for every failure. */
export interface ApiError {
  status: number;
  error: string;
  message: string;
  fieldErrors?: Record<string, string>;
}
