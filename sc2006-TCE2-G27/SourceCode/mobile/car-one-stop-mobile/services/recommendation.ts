import api from './Registerapi';

export type FacilityType = 'CARPARK' | 'EV';
export type RecommendationPreference = 'CHEAPEST' | 'NEAREST' | 'MOST_AVAILABLE' | 'CUSTOM';

export type RecommendationItem = {
  id: number;
  type: FacilityType;
  name: string;
  address: string;
  lat: number;
  lng: number;
  hourlyRate: number | null;
  availableLots: number | null;
  sheltered: boolean | null;
  distanceMeters: number;
  etaMinutes: number | null;
  score: number;
};

export type PreferenceUpsertRequest = {
  maxHourlyRate?: number | null;
  minAvailableLots?: number | null;
  requireSheltered?: boolean | null;
  weightDistance?: number;
  weightPrice?: number;
  weightAvailability?: number;
  parkingDurationHours?: number;
  evParkingDurationHours?: number;
};

export type UserPreferenceResponse = {
  id: number;
  maxHourlyRate?: number | null;
  minAvailableLots?: number | null;
  requireSheltered?: boolean | null;
  weightDistance: number;
  weightPrice: number;
  weightAvailability: number;
  parkingDurationHours: number;
};

export const getRecommendations = async (params: {
  type: FacilityType;
  preference: RecommendationPreference;
  lat: number;
  lng: number;
  radius?: number;
  limit?: number;
}): Promise<RecommendationItem[]> => {
  const { data } = await api.get<RecommendationItem[]>('/recommendations', { params });
  return data;
};

export const upsertRecommendationPreferences = async (payload: PreferenceUpsertRequest) => {
  const { data } = await api.put('/recommendations/preferences', payload);
  return data;
};

export const getRecommendationPreferences = async () => {
  const { data } = await api.get<UserPreferenceResponse>('/recommendations/preferences');
  return data;
};

export type CandidateItem = {
  id: number;
  name: string;
  lat: number;
  lng: number;
  availableLots: number | null;
  hourlyRate?: number | null;
};

export type CarParkRecommendationResult = {
  candidates: CandidateItem[];
  recommendations: RecommendationItem[];
};

export const getCarParkRecommendations = async (params: {
  preference: RecommendationPreference;
  lat: number;
  lng: number;
  radius?: number;
}): Promise<CarParkRecommendationResult> => {
  const { data } = await api.get<CarParkRecommendationResult>('/recommendations/carparks', { params });
  return data;
};

export type EVRecommendationItem = {
  stationName: string;
  address: string;
  lat: number;
  lng: number;
  postalCode: string;
  availablePoints: number;
  evChargingCost: number;
  carparkCost: number;
  totalCost: number;
  nearestCarparkName: string | null;
  distanceMeters: number;
  etaMinutes: number | null;
  score: number;
};

export const getEVRecommendations = async (params: {
  preference: RecommendationPreference;
  lat: number;
  lng: number;
  radius?: number;
}): Promise<EVRecommendationItem[]> => {
  const { data } = await api.get<EVRecommendationItem[]>('/recommendations/ev-chargers', { params });
  return data;
};
