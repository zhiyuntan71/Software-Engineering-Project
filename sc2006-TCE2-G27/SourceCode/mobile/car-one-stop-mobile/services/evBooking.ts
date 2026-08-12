import api from './Registerapi';
import { getWalletBalance } from './walletService';

export interface CreateBookingPayload {
  evChargingPointId: string;
}

export type BookingResponse = {
  bookingId: string;
  evChargingPointId: string;
  locationName?: string | null;
  lot?: string | null;
  plugType?: string | null;
  priceDisplay?: string | null;
  priceType?: string | null;
  powerRating?: string | null;
  currentType?: string | null;
  voltage?: string | null;
  status: string;
  createdAt: string;
  reservedTime: string;
  checkInTime: string | null;
  gracePeriodMinutes: number;
  startTime: string;
  endTime: string;
  bookingFee: number;
  walletBalanceAfterBooking?: number;
};

export const createBooking = async (
  payload: CreateBookingPayload
): Promise<BookingResponse | null> => {
  try {
    const endpoint = '/bookings';

    console.log('[BOOKING] POST ->', endpoint);
    console.log('[BOOKING] Payload:', payload);

    const { data } = await api.post<BookingResponse>(endpoint, payload);
    const wallet = await getWalletBalance();

    const responseWithWallet: BookingResponse = {
      ...data,
      walletBalanceAfterBooking: Number(wallet.balance ?? 0),
    };

    console.log('[BOOKING] Success:', responseWithWallet);
    return responseWithWallet;
  } catch (error) {
    console.warn('[BOOKING] Error:', error);
    return null;
  }
};

export const getBookingHistory = async (): Promise<BookingResponse[]> => {
  try {
    const endpoint = '/bookings';

    console.log('[BOOKING] GET ->', endpoint);

    const { data } = await api.get<BookingResponse[]>(endpoint);
    console.log('[BOOKING] History count:', data.length);

    return data;
  } catch (error) {
    console.warn('[BOOKING] History error:', error);
    return [];
  }
};

export const cancelBooking = async (bookingId: string): Promise<boolean> => {
  try {
    const endpoint = `/bookings/${bookingId}/cancel`;
    await api.post(endpoint);
    return true;
  } catch (error) {
    console.warn('[BOOKING] Cancel error:', error);
    return false;
  }
};

export const checkInBooking = async (bookingId: string): Promise<BookingResponse | null> => {
  try {
    const endpoint = `/bookings/${bookingId}/check-in`;
    const { data } = await api.post<BookingResponse>(endpoint);
    return data;
  } catch (error) {
    console.warn('[BOOKING] Check-in error:', error);
    return null;
  }
};

export const completeBooking = async (bookingId: string): Promise<boolean> => {
  try {
    await api.post(`/bookings/${bookingId}/complete`);
    return true;
  } catch (error) {
    console.warn('[BOOKING] Complete error:', error);
    return false;
  }
};
