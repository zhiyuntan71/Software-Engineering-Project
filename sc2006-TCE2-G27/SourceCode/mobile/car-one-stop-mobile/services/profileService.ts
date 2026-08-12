// services/profileService.ts
import api from './Registerapi'
import { CarType, ChargingType } from './authService'

export interface ProfileResponse {
  username: string
  email: string
  carType: CarType
  carModel: string
  chargingType: ChargingType
}

export interface UpdateProfileRequest {
  username?: string
  carType?: CarType
  carModel?: string
  chargingType?: ChargingType
  // no email
}
export const getProfile = async (): Promise<ProfileResponse> => {
  const { data } = await api.get<ProfileResponse>('/profile')
  return data
}

export const updateProfile = async (profileData: UpdateProfileRequest): Promise<ProfileResponse> => {
  const { data } = await api.put<ProfileResponse>('/profile', profileData)
  return data
}