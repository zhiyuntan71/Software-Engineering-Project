import React, { useEffect } from 'react';
import { View, Text, StyleProp, ViewStyle, TextStyle, TouchableOpacity } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { Marker } from 'react-native-maps';
import { CarparkLocation } from '@/services/carpark';
import RankPin, { SmallPin } from '../map/RankPin';
import { getCarParkRecommendations, upsertRecommendationPreferences, RecommendationPreference } from '@/services/recommendation';

type PreferenceMode = 'cheapest' | 'nearest' | 'most_available' | 'custom';

export const toBackendPreference = (p: PreferenceMode): RecommendationPreference => {
  if (p === 'cheapest') return 'CHEAPEST';
  if (p === 'nearest') return 'NEAREST';
  if (p === 'most_available') return 'MOST_AVAILABLE';
  return 'CUSTOM';
};

export const fetchCarparkRecommendations = async (params: {
  preference: PreferenceMode;
  durationHours: number;
  wDistance: number;
  wCost: number;
  lat: number;
  lng: number;
  searchRadiusKm: number;
}): Promise<{ candidates: CarparkLocation[]; recommendations: CarparkLocation[] }> => {
  const { preference, wDistance, wCost, lat, lng, searchRadiusKm, durationHours } = params;
  const backendPreference = toBackendPreference(preference);
  const preferencePayload: Parameters<typeof upsertRecommendationPreferences>[0] = {
    parkingDurationHours: durationHours,
  };

  if (backendPreference === 'CUSTOM') {
    const normalizedDistance = Number(wDistance.toFixed(2));
    const normalizedPrice = Number(wCost.toFixed(2));
    const normalizedAvailability = Number((1 - normalizedDistance - normalizedPrice).toFixed(2));
    preferencePayload.weightPrice = normalizedPrice;
    preferencePayload.weightDistance = normalizedDistance;
    preferencePayload.weightAvailability = normalizedAvailability;
  }

  await upsertRecommendationPreferences(preferencePayload);

  const result = await getCarParkRecommendations({
    preference: backendPreference,
    lat,
    lng,
    radius: Math.round(searchRadiusKm * 1000),
  });

  const toCarparkLocation = (item: { id?: number | null; name?: string | null; lat: number; lng: number; availableLots?: number | null; hourlyRate?: number | null; etaMinutes?: number | null; distanceMeters?: number | null }, idx: number): CarparkLocation => ({
    carParkID: item.id?.toString() ?? `cp-${idx}`,
    development: item.name || `Carpark ${idx + 1}`,
    latitude: item.lat,
    longitude: item.lng,
    availableLots: item.availableLots ?? -1,
    agency: '',
    hourlyRate: item.hourlyRate ?? null,
    etaMinutes: item.etaMinutes ?? null,
    drivingDistanceMeters: item.distanceMeters ?? null,
  });

  const candidates = result.candidates.map((item, idx) => toCarparkLocation(item, idx));
  const recommendations = result.recommendations.map((item, idx) => toCarparkLocation(item, idx));

  return { candidates, recommendations };
};

export function CarparkMarkers({
  showCarparks,
  candidates,
  recommendations,
  preference,
  wDistance,
  wAvail,
  mapRef,
  originCoord,
  onSelectCarpark,
}: {
  showCarparks: boolean;
  candidates: CarparkLocation[];
  recommendations: CarparkLocation[];
  preference: PreferenceMode;
  wDistance: number;
  wAvail: number;
  mapRef: React.RefObject<any>;
  originCoord: { latitude: number; longitude: number } | null;
  onSelectCarpark?: (carpark: CarparkLocation | null) => void;
}) {
  const topResultKey = recommendations.slice(0, 3).map(r => r.carParkID).join(',');

  useEffect(() => {
    if (!showCarparks || recommendations.length === 0 || !originCoord || !mapRef.current) return;
    const coords = [
      originCoord,
      ...recommendations.slice(0, 3).map(r => ({ latitude: r.latitude, longitude: r.longitude })),
    ];
    mapRef.current.fitToCoordinates(coords, {
      edgePadding: { top: 120, right: 80, bottom: 160, left: 80 },
      animated: true,
    });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [showCarparks, topResultKey, originCoord?.latitude, originCoord?.longitude]);

  if (!showCarparks) return null;

  const recommendedIds = new Set(recommendations.map(r => r.carParkID));
  const orderedPins: CarparkLocation[] = [
    ...recommendations,
    ...candidates.filter(cp => !recommendedIds.has(cp.carParkID)),
  ];

  return (
    <>
      {orderedPins.map((cp: CarparkLocation, index: number) => {
        const lots = cp.availableLots;
        const isLive = lots !== -1;
        const lotsText = isLive ? `🅿 ${lots} lots available` : `🅿 Lots unavailable`;
        const priceText = cp.hourlyRate != null ? `💲 $${cp.hourlyRate.toFixed(2)}/hr` : `💲 Price unknown`;
        return (
          <Marker
            key={`${cp.carParkID}-${preference}-${index}-${wDistance}-${wAvail}`}
            coordinate={{ latitude: cp.latitude, longitude: cp.longitude }}
            title={cp.development}
            description={`${lotsText}\n${priceText}`}
            anchor={{ x: 0.5, y: 1 }}
            onPress={() => onSelectCarpark?.(cp)}
          >
            {index === 0 && recommendations.length > 0 ? <RankPin rank={1} /> :
             index === 1 && recommendations.length > 1 ? <RankPin rank={2} /> :
             index === 2 && recommendations.length > 2 ? <RankPin rank={3} /> :
             <SmallPin />}
          </Marker>
        );
      })}
    </>
  );
}

export function CarparkBadge({
  showCarparks,
  loadingCarparks,
  carparks,
  searchedCoord,
  durationSelected,
  searchRadiusKm,
  onPress,
  carparkBlue,
  badgeStyle,
  badgeTextStyle,
}: {
  showCarparks: boolean;
  loadingCarparks: boolean;
  carparks: CarparkLocation[];
  searchedCoord: { lat: number; lon: number } | null;
  durationSelected: boolean;
  searchRadiusKm: number;
  onPress?: () => void;
  carparkBlue: string;
  badgeStyle: StyleProp<ViewStyle>;
  badgeTextStyle: StyleProp<TextStyle>;
}) {
  if (!showCarparks) return null;

  const radiusLabel = searchRadiusKm < 1
    ? `${Math.round(searchRadiusKm * 1000)}m`
    : `${searchRadiusKm}km`;
  const radiusActionHint = searchRadiusKm < 1
    ? 'Tap to expand search radius'
    : 'Tap to reduce search radius';

  if (!durationSelected) {
    return (
      <View style={[badgeStyle, { backgroundColor: '#FFF8E1' }]}>
        <Text style={[badgeTextStyle, { color: '#AD6800' }]}>
          Select parking duration (1h, 2h, 3h, 4h+) to continue
        </Text>
      </View>
    );
  }

  if (loadingCarparks) {
    return (
      <View style={[badgeStyle, { backgroundColor: '#E3F2FD' }]}>
        <Text style={[badgeTextStyle, { color: carparkBlue }]}>Searching for carparks...</Text>
      </View>
    );
  }

  if (carparks.length > 0) {
    return (
      <TouchableOpacity
        style={[badgeStyle, { backgroundColor: '#E3F2FD' }]}
        onPress={onPress}
        disabled={!onPress}
      >
        <Text style={[badgeTextStyle, { color: carparkBlue }]}>
          {carparks.length} carpark{carparks.length > 1 ? 's' : ''} within {radiusLabel}
        </Text>
        {!!onPress && (
          <Text style={[badgeTextStyle, { color: carparkBlue, fontSize: 10, fontWeight: '500', marginTop: 0, opacity: 0.9 }]}>
            {radiusActionHint}
          </Text>
        )}
      </TouchableOpacity>
    );
  }

  if (searchedCoord) {
    return (
      <TouchableOpacity
        style={[badgeStyle, { backgroundColor: '#FFF3E0' }]}
        onPress={onPress}
        disabled={!onPress}
      >
        <Text style={[badgeTextStyle, { color: '#E65100' }]}>
          No carparks found within {radiusLabel}
        </Text>
        {!!onPress && (
          <Text style={[badgeTextStyle, { color: '#E65100', fontSize: 11, fontWeight: '500', marginTop: 2, opacity: 0.9 }]}>
            {radiusActionHint}
          </Text>
        )}
      </TouchableOpacity>
    );
  }

  return null;
}

export function CarparkBottomSheet({
  selectedCarpark,
  onClose,
  onNavigate,
  carparkBlue,
}: {
  selectedCarpark: CarparkLocation;
  onClose: () => void;
  onNavigate: () => void;
  carparkBlue: string;
}) {
  const { development, availableLots, agency, hourlyRate, etaMinutes, drivingDistanceMeters } = selectedCarpark;
  const hasLive = availableLots !== -1;
  const lotsColor = hasLive ? (availableLots > 0 ? '#27ae60' : '#e74c3c') : '#888';
  const lotsText = hasLive ? `${availableLots} lots available` : 'Availability unknown';
  const priceText = hourlyRate != null ? `$${hourlyRate.toFixed(2)}/hr` : 'Price unknown';
  const etaText = etaMinutes != null ? `${Math.round(etaMinutes)} min drive` : null;
  const distanceText = drivingDistanceMeters != null
    ? drivingDistanceMeters >= 1000
      ? `${(drivingDistanceMeters / 1000).toFixed(1)} km`
      : `${Math.round(drivingDistanceMeters)} m`
    : null;

  return (
    <View style={{
      backgroundColor: '#fff', borderRadius: 16, padding: 16,
      elevation: 6, shadowColor: '#000', shadowOpacity: 0.15, shadowRadius: 8,
    }}>
      {/* Header row: name + close */}
      <View style={{ flexDirection: 'row', alignItems: 'flex-start', marginBottom: 10 }}>
        <Text style={{ flex: 1, fontSize: 16, fontWeight: '700', color: '#1a1a2e', marginRight: 8 }} numberOfLines={2}>
          {development}
        </Text>
        <TouchableOpacity onPress={onClose} hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
          <Ionicons name="close" size={22} color="#666" />
        </TouchableOpacity>
      </View>

      {/* Available lots */}
      <Text style={{ fontSize: 14, fontWeight: '600', color: lotsColor, marginBottom: 4 }}>
        🅿 {lotsText}
      </Text>

      {/* Price */}
      <Text style={{ fontSize: 14, fontWeight: '600', color: '#555', marginBottom: 4 }}>
        💲 {priceText}
      </Text>

      {/* ETA / Distance */}
      {(etaText || distanceText) && (
        <Text style={{ fontSize: 14, fontWeight: '600', color: '#555', marginBottom: 4 }}>
          🚗 {[etaText, distanceText].filter(Boolean).join(' · ')}
        </Text>
      )}

      {/* Agency */}
      {!!agency && (
        <Text style={{ fontSize: 12, color: '#888', marginBottom: 14 }}>{agency}</Text>
      )}
      {!agency && <View style={{ marginBottom: 14 }} />}

      {/* Navigate button */}
      <TouchableOpacity
        style={{
          backgroundColor: carparkBlue, borderRadius: 10,
          paddingVertical: 12, alignItems: 'center',
        }}
        onPress={onNavigate}
      >
        <Text style={{ color: '#fff', fontWeight: '700', fontSize: 15 }}>Navigate Here</Text>
      </TouchableOpacity>
    </View>
  );
}
