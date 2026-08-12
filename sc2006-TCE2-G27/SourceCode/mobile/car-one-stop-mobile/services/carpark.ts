import api from './Registerapi'; // your existing Axios instance with baseURL + JWT interceptor

export type CarparkLocation = {
  carParkID: string;
  development: string;
  latitude: number;
  longitude: number;
  availableLots: number;
  agency: string;
  hourlyRate?: number | null;
  etaMinutes?: number | null;
  drivingDistanceMeters?: number | null;
};

export const getCarparksNearby = async (
  lat: number,
  lon: number,
  radiusKm: number = 2.0
): Promise<CarparkLocation[]> => {
  const { data } = await api.get<CarparkLocation[]>('/map/nearby/carparks', {
    params: { lat, lng: lon, radiusKm },
  });
  return data;
};