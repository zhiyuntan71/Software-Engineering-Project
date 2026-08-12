import api from './Registerapi';

export type EVLocation = {
  name: string;
  address: string;
  latitude: number;
  longitude: number;
  postalCode: string;
  chargingPoints: any[];
  /** false = OCM station, no real-time availability data */
  hasLiveStatus: boolean;
};

export const getEVChargersNearby = async (
  lat: number,
  lon: number,
  radiusKm: number
): Promise<EVLocation[]> => {
  try {
    const { data } = await api.get<any[]>('/map/nearby/ev-chargers', {
      params: {
        lat,
        lng: lon,
        radiusKm,
      },
    });
    console.log('[EV] Total stations returned:', data.length);

    if (data.length > 0) {
      console.log('[EV] First station:', JSON.stringify(data[0], null, 2));
    }

    const normalizePrice = (rawPrice: any): number => {
      if (rawPrice == null) return 0.45;
      const parsed = Number(rawPrice);
      if (!Number.isFinite(parsed) || parsed === 0) return 0.45;
      return parsed;
    };

    return data.map(ev => ({
      ...ev,
      chargingPoints: (ev.chargingPoints ?? []).map((cp: any) => ({
        ...cp,
        plugTypes: (cp?.plugTypes ?? []).map((plug: any) => ({
          ...plug,
          price: normalizePrice(plug?.price),
        })),
      })),
      longitude: ev.longitude,
    }));

  } catch (error) {
    console.warn('[EV] Fetch error:', error);
    return [];
  }
};
