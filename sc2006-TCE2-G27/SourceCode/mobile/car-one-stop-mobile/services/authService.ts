import { decode } from 'base-64';
import api from './Registerapi';

import * as SecureStore from 'expo-secure-store';

// Mirror your Java CarType enum
export type CarType = 'SEDAN' | 'SUV' | 'ELECTRIC' | 'HYBRID';

// Mirror your Java CarModel enum
export type CarModel = string; // or enumerate all values if your enum is fixed

// Mirror your Java ChargingType enum
export type ChargingType = 'NOT_APPLICABLE' | 'TYPE_1_J1772' | 'TYPE_2_MENNEKES' | 'CCS' | 'CHADEMO' | 'TESLA_SUPERCHARGER';

// Matches your Java RegisterRequest record exactly
export interface RegisterPayload {
  email: string;          // @Email @NotBlank
  username: string;       // @NotBlank @Size(min=3, max=50)
  password: string;       // @NotBlank @Size(min=8, max=72)
  carType: CarType;       // @NotNull CarType
  carModel: CarModel;     // @NotNull CarModel
  chargingType: ChargingType; // @NotNull ChargingType
}

export interface RegisterResponse {
  userId: number
  username: string
  email: string
  token: string
  carType: CarType
  carModel: CarModel
  chargingType: ChargingType
}

export interface AuthResponse {
  userId: number
  username: string
  email: string
  token: string
}

// Maps validation errors from Spring's MethodArgumentNotValidException
export interface ValidationError {
  field: string;
  message: string;
}

export interface LoginResponse {
  userId: number
  username: string
  email: string
  token: string
  carType: CarType
  carModel: CarModel
  chargingType: ChargingType
}

export const registerUser = async (payload: RegisterPayload): Promise<RegisterResponse> => {
  console.log('Sending payload:', JSON.stringify(payload))
  const { data } = await api.post<RegisterResponse>('/auth/register', payload);
  await SecureStore.setItemAsync('userToken', data.token);
  return data;
};

export const logoutUser = async (): Promise<void> => {
  await SecureStore.deleteItemAsync('userToken');
};

export const verifyEmail = async (code: string): Promise<AuthResponse> => {
  const { data } = await api.post<AuthResponse>('/auth/verify', { OTPtoken: code })
  await SecureStore.setItemAsync('userToken', data.token) // overwrite pending token with full token
  return data
}

export const forgotPassword = async (email: string): Promise<void> => {
  await api.post('/auth/forgot-password', { email })
}

export const verifyResetOtp = async (email: string, OTPtoken: string): Promise<void> => {
  await api.post('/auth/verify-reset-otp', { email, OTPtoken })
}

export const resendResetOtp = async (email: string): Promise<void> => {
  await api.post('/auth/resend-reset-otp', { email })
}

export const resetPassword = async (email: string, OTPtoken: string, newPassword: string): Promise<void> => {
  await api.post('/auth/reset-password', { email, OTPtoken, newPassword })
}

export const loginUser = async (email: string, password: string): Promise<LoginResponse> => {
  const { data } = await api.post<LoginResponse>('/auth/login', { Email: email, password })
  const payload = JSON.parse(decode(data.token.split('.')[1]));
  
  await SecureStore.setItemAsync('userToken', data.token);
  await SecureStore.setItemAsync('userRole', payload.role);
  
  return data
}

// resend OTP (add this endpoint to backend too) (To Implement)
export const resendOtp = async (): Promise<void> => {
  await api.post('/auth/resend-otp')
}
