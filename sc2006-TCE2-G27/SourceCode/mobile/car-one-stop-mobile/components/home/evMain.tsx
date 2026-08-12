import StatusModal, { StatusModalDetail } from '@/components/ui/StatusModal';
import { createBooking, getBookingHistory } from '@/services/evBooking';
import { EVLocation, getEVChargersNearby } from '@/services/evCharger';
import {
  EVRecommendationItem,
  getEVRecommendations,
  upsertRecommendationPreferences,
  RecommendationPreference as BackendPreference,
} from '@/services/recommendation';
import { Ionicons } from '@expo/vector-icons';
import { router } from 'expo-router';
import React, { useEffect, useState } from 'react';
import { StyleProp, Text, TextStyle, TouchableOpacity, View, ViewStyle } from 'react-native';
import { Marker } from 'react-native-maps';
import RankPin, { SmallPin } from '../map/RankPin';

type PreferenceMode = 'cheapest' | 'nearest' | 'most_available' | 'custom';

const normalize = (value: string) =>
  value.toLowerCase().replace(/[^a-z0-9]/g, '');

export const matchesSavedChargingType = (plugType: string, savedType: string) => {
  const plug = normalize(plugType || '');

  switch (savedType) {
    case 'TYPE_1_J1772':
      return plug.includes('type1') || plug.includes('j1772');
    case 'TYPE_2_MENNEKES':
      return plug.includes('type2') || plug.includes('mennekes');
    case 'CCS':
      return plug.includes('ccs');
    case 'CHADEMO':
      return plug.includes('chademo');
    case 'TESLA_SUPERCHARGER':
      return plug.includes('tesla') || plug.includes('supercharger');
    default:
      return true;
  }
};

export const hasSavedChargingType = (chargingType: string) =>
  !!chargingType && chargingType !== 'NOT_APPLICABLE';

export const fetchEVChargers = async (lat: number, lon: number, radiusKm: number): Promise<EVLocation[]> => {
  const chargers = await getEVChargersNearby(lat, lon, radiusKm);
  const plugTypes = new Set<string>();
  chargers.forEach(station => {
    station.chargingPoints?.forEach((cp: any) => {
      cp.plugTypes?.forEach((plug: any) => plugTypes.add(plug.plugType));
    });
  });
  console.log('All plug types found:', [...plugTypes]);
  return chargers;
};

const toBackendPreference = (p: PreferenceMode): BackendPreference => {
  if (p === 'cheapest') return 'CHEAPEST';
  if (p === 'nearest') return 'NEAREST';
  if (p === 'most_available') return 'MOST_AVAILABLE';
  return 'CUSTOM';
};

/**
 * Calls the backend EV recommendation endpoint and returns the sorted order
 * plus pricing data. Saves evParkingDurationHours + custom weights to preferences first.
 */
export const fetchEVRecommendations = async (params: {
  preference: PreferenceMode;
  durationHours: number;
  wDistance: number;
  wCost: number;
  lat: number;
  lng: number;
  searchRadiusKm: number;
}): Promise<EVRecommendationItem[]> => {
  const { preference, durationHours, wDistance, wCost, lat, lng, searchRadiusKm } = params;
  const backendPref = toBackendPreference(preference);

  const prefPayload: Parameters<typeof upsertRecommendationPreferences>[0] = {
    evParkingDurationHours: durationHours,
  };
  if (backendPref === 'CUSTOM') {
    const normalizedDistance = Number(wDistance.toFixed(2));
    const normalizedPrice    = Number(wCost.toFixed(2));
    const normalizedAvail    = Number((1 - normalizedDistance - normalizedPrice).toFixed(2));
    prefPayload.weightPrice        = normalizedPrice;
    prefPayload.weightDistance     = normalizedDistance;
    prefPayload.weightAvailability = normalizedAvail;
  }

  await upsertRecommendationPreferences(prefPayload);

  return getEVRecommendations({
    preference: backendPref,
    lat,
    lng,
    radius: Math.round(searchRadiusKm * 1000),
  });
};

/**
 * Re-orders evChargers according to the backend recommendation order,
 * filtering out stations that don't appear in the recommendation results.
 */
export const applyEVRecommendationOrder = (
  evChargers: EVLocation[],
  recommendations: EVRecommendationItem[]
): EVLocation[] => {
  if (recommendations.length === 0) return evChargers;
  const ordered: EVLocation[] = [];
  for (const rec of recommendations) {
    const match = evChargers.find(
      (ev) => ev.name === rec.stationName || (
        Math.abs(ev.latitude - rec.lat) < 0.0001 &&
        Math.abs(ev.longitude - rec.lng) < 0.0001
      )
    );
    if (match) ordered.push(match);
  }
  // Append any chargers not returned by recommendation (e.g. routing failures)
  for (const ev of evChargers) {
    const alreadyIn = ordered.some(
      (o) => o.name === ev.name && Math.abs(o.latitude - ev.latitude) < 0.0001
    );
    if (!alreadyIn) ordered.push(ev);
  }
  return ordered;
};

export const getSortedFilteredEVChargers = (params: {
  evChargers: EVLocation[];
  searchedCoord: { lat: number; lon: number } | null;
  preference: PreferenceMode;
  wDistance: number;
  wAvail: number;
  chargingType: string;
  showAllChargingTypes: boolean;
}): EVLocation[] => {
  const { evChargers, searchedCoord, preference, wDistance, wAvail, chargingType, showAllChargingTypes } = params;

  const shouldFilterBySavedType =
    !showAllChargingTypes && hasSavedChargingType(chargingType);

  const filteredEVChargers = shouldFilterBySavedType
    ? evChargers.filter((station) =>
        station.chargingPoints?.some((cp: any) =>
          cp?.plugTypes?.some((plug: any) =>
            matchesSavedChargingType(String(plug?.plugType ?? ''), chargingType)
          )
        )
      )
    : evChargers;

  if (!searchedCoord || filteredEVChargers.length === 0) return filteredEVChargers;

  const { lat, lon } = searchedCoord;
  const getDist = (ev: EVLocation) =>
    Math.sqrt(Math.pow(ev.latitude - lat, 2) + Math.pow(ev.longitude - lon, 2));
  const getAvail = (ev: EVLocation) =>
    ev.chargingPoints?.filter((cp: any) => cp.status === '1').length ?? 0;

  if (preference === 'nearest')
    return [...filteredEVChargers].sort((a, b) => getDist(a) - getDist(b));

  if (preference === 'most_available')
    return [...filteredEVChargers].sort((a, b) => {
      const availDiff = getAvail(b) - getAvail(a);
      if (availDiff !== 0) return availDiff;
      return getDist(a) - getDist(b);
    });

  if (preference === 'cheapest') {
    const getMinPrice = (ev: EVLocation): number => {
      const prices = ev.chargingPoints?.flatMap((cp: any) =>
        cp?.plugTypes?.map((pt: any) => Number(pt?.price ?? Infinity)) ?? []
      ) ?? [];
      return prices.length > 0 ? Math.min(...prices) : Infinity;
    };
    return [...filteredEVChargers].sort((a, b) => getMinPrice(a) - getMinPrice(b));
  }

  if (preference === 'custom') {
    const maxDist = Math.max(...filteredEVChargers.map(getDist), 1);
    const maxAvail = Math.max(...filteredEVChargers.map(getAvail), 1);
    return [...filteredEVChargers].sort((a, b) => {
      const scoreA = wDistance * (1 - getDist(a) / maxDist) + wAvail * (getAvail(a) / maxAvail);
      const scoreB = wDistance * (1 - getDist(b) / maxDist) + wAvail * (getAvail(b) / maxAvail);
      return scoreB - scoreA;
    });
  }

  return filteredEVChargers;
};

export function EVMarkers({
  showEV,
  sortedEVChargers,
  recommendations,
  preference,
  wDistance,
  wAvail,
  mapRef,
  originCoord,
  onSelectEV,
}: {
  showEV: boolean;
  sortedEVChargers: EVLocation[];
  recommendations: EVRecommendationItem[];
  preference: PreferenceMode;
  wDistance: number;
  wAvail: number;
  mapRef: React.RefObject<any>;
  originCoord: { latitude: number; longitude: number } | null;
  onSelectEV: (loc: EVLocation) => void;
}) {
  const topRecsKey = recommendations.slice(0, 3).map(r => r.stationName).join(',');

  useEffect(() => {
    if (!showEV || recommendations.length === 0 || !originCoord || !mapRef.current) return;
    const coords = [
      originCoord,
      ...recommendations.slice(0, 3).map(r => ({ latitude: r.lat, longitude: r.lng })),
    ];
    mapRef.current.fitToCoordinates(coords, {
      edgePadding: { top: 120, right: 80, bottom: 160, left: 80 },
      animated: true,
    });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [showEV, topRecsKey, originCoord?.latitude, originCoord?.longitude]);

  if (!showEV) return null;

  return (
    <>
      {sortedEVChargers.map((loc: EVLocation, index: number) => {
        const rec = recommendations.find(
          (r) => r.stationName === loc.name || (
            Math.abs(r.lat - loc.latitude) < 0.0001 &&
            Math.abs(r.lng - loc.longitude) < 0.0001
          )
        );
        const descParts: string[] = [loc.address];
        if (!loc.hasLiveStatus) {
          descParts.push('Availability unknown');
        }
        if (rec) {
          descParts.push(`Est. $${rec.totalCost.toFixed(2)} total`);
          if (rec.carparkCost > 0 && rec.nearestCarparkName) {
            descParts.push(`Parking: $${rec.carparkCost.toFixed(2)}`);
          }
          if (rec.etaMinutes != null) {
            descParts.push(`${Math.round(rec.etaMinutes)} min drive`);
          }
        }
        return (
          <Marker
            key={`${loc.postalCode + loc.name}-${preference}-${index}-${wDistance}-${wAvail}`}
            coordinate={{ latitude: loc.latitude, longitude: loc.longitude }}
            title={loc.name}
            description={descParts.join(' · ')}
            anchor={{ x: 0.5, y: 1 }}
            onPress={() => onSelectEV(loc)}
          >
            {recommendations.length > 0 && index === 0 ? <RankPin rank={1} /> :
             recommendations.length > 0 && index === 1 ? <RankPin rank={2} /> :
             recommendations.length > 0 && index === 2 ? <RankPin rank={3} /> :
             <SmallPin />}
          </Marker>
        );
      })}
    </>
  );
}

export function EVBadge({
  showEV,
  loadingEV,
  sortedEVChargers,
  evChargers,
  showAllChargingTypes,
  canUseSavedType,
  searchedCoord,
  searchRadiusKm,
  onPress,
  badgeStyle,
  badgeTextStyle,
}: {
  showEV: boolean;
  loadingEV: boolean;
  sortedEVChargers: EVLocation[];
  evChargers: EVLocation[];
  showAllChargingTypes: boolean;
  canUseSavedType: boolean;
  searchedCoord: { lat: number; lon: number } | null;
  searchRadiusKm: number;
  onPress?: () => void;
  badgeStyle: StyleProp<ViewStyle>;
  badgeTextStyle: StyleProp<TextStyle>;
}) {
  if (!showEV) return null;

  const radiusLabel = searchRadiusKm < 1
    ? `${Math.round(searchRadiusKm * 1000)}m`
    : `${searchRadiusKm}km`;
  const radiusActionHint = searchRadiusKm < 1
    ? 'Tap to expand search radius'
    : 'Tap to reduce search radius';

  if (loadingEV) {
    return (
      <View style={badgeStyle}>
        <Text style={badgeTextStyle}>Searching for EV chargers...</Text>
      </View>
    );
  }

  if (sortedEVChargers.length > 0) {
    return (
      <TouchableOpacity style={badgeStyle} onPress={onPress} disabled={!onPress}>
        <Text style={badgeTextStyle}>
          {sortedEVChargers.length} EV charger{sortedEVChargers.length > 1 ? 's' : ''} within {radiusLabel}
        </Text>
        {!!onPress && (
          <Text style={[badgeTextStyle, { fontSize: 11, fontWeight: '500', marginTop: 2, opacity: 0.9 }]}>
            {radiusActionHint}
          </Text>
        )}
      </TouchableOpacity>
    );
  }

  if (evChargers.length > 0 && !showAllChargingTypes && canUseSavedType) {
    return (
      <View style={[badgeStyle, { backgroundColor: '#FFF3E0' }]}>
        <Text style={[badgeTextStyle, { color: '#E65100' }]}>
          No chargers match your saved charging type nearby.
        </Text>
      </View>
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
          No EV chargers found within {radiusLabel}
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

export function EVBottomSheet({
  selectedEV,
  selectedPlug,
  setSelectedEV,
  setSelectedPlug,
  setBalance,
  userCarType,
  userChargingType,
  text,
  subtext,
  evGreen,
}: {
  selectedEV: EVLocation | null;
  selectedPlug: any | null;
  setSelectedEV: (ev: EVLocation | null) => void;
  setSelectedPlug: React.Dispatch<React.SetStateAction<any | null>>;
  setBalance: (val: number) => void;
  userCarType: string;
  userChargingType: string;
  text: string;
  subtext: string;
  evGreen: string;
}) {
  const [showEvOnlyModal, setShowEvOnlyModal] = useState(false);
  const [showConfirmModal, setShowConfirmModal] = useState(false);
  const [showSuccessModal, setShowSuccessModal] = useState(false);
  const [submittingBooking, setSubmittingBooking] = useState(false);
  const [confirmDetails, setConfirmDetails] = useState<StatusModalDetail[]>([]);
  const [successDetails, setSuccessDetails] = useState<StatusModalDetail[]>([]);
  // === POPUP STANDARDIZATION (StatusModal) ===
  const [showFeedbackModal, setShowFeedbackModal] = useState(false);
  const [feedbackVariant, setFeedbackVariant] = useState<'success' | 'confirm' | 'warning' | 'error' | 'info'>('info');
  const [feedbackTitle, setFeedbackTitle] = useState('');
  const [feedbackMessage, setFeedbackMessage] = useState('');
  const [navigateToWalletOnFeedbackClose, setNavigateToWalletOnFeedbackClose] = useState(false);
  const [pendingBookingPayload, setPendingBookingPayload] = useState<{
    evChargingPointId: string;
  } | null>(null);
  const [lotsPage, setLotsPage] = useState(1);

  const LOTS_PER_PAGE = 3;
  const allChargingPoints = selectedEV?.chargingPoints ?? [];
  const totalLots = allChargingPoints.length;
  const totalLotPages = Math.max(1, Math.ceil(totalLots / LOTS_PER_PAGE));
  const pageStart = (lotsPage - 1) * LOTS_PER_PAGE;
  const pagedChargingPoints = allChargingPoints.slice(pageStart, pageStart + LOTS_PER_PAGE);

  const openFeedbackModal = (
    variant: 'success' | 'confirm' | 'warning' | 'error' | 'info',
    title: string,
    message: string,
    options?: {
      navigateToWalletOnClose?: boolean;
    }
  ) => {
    setFeedbackVariant(variant);
    setFeedbackTitle(title);
    setFeedbackMessage(message);
    setNavigateToWalletOnFeedbackClose(options?.navigateToWalletOnClose ?? false);
    setShowFeedbackModal(true);
  };

  const handleFeedbackModalPrimary = () => {
    setShowFeedbackModal(false);

    if (navigateToWalletOnFeedbackClose) {
      setNavigateToWalletOnFeedbackClose(false);
      setSelectedEV(null);
      setSelectedPlug(null);
      router.push('/wallet');
      return;
    }

    setNavigateToWalletOnFeedbackClose(false);
  };

  useEffect(() => {
    setLotsPage(1);
  }, [selectedEV?.name, selectedEV?.latitude, selectedEV?.longitude, totalLots]);

  useEffect(() => {
    if (lotsPage > totalLotPages) {
      setLotsPage(totalLotPages);
    }
  }, [lotsPage, totalLotPages]);

  if (!selectedEV) return null;

  const handleConfirmBooking = async () => {
    if (!pendingBookingPayload) return;

    setSubmittingBooking(true);
    try {
      const result = await createBooking(pendingBookingPayload);

      if (!result) {
        openFeedbackModal('error', 'Booking Failed', 'Please top up your wallet.', {
          navigateToWalletOnClose: true,
        });
        return;
      }

      if (typeof result.walletBalanceAfterBooking === 'number') {
        setBalance(result.walletBalanceAfterBooking);
      }

      setShowConfirmModal(false);
      setSuccessDetails([
        { label: 'Booking ID', value: result.bookingId, fullWidth: true },
        { label: 'Fee', value: `$${Number(result.bookingFee ?? 0).toFixed(2)}` },
        { label: 'Balance', value: `$${(result.walletBalanceAfterBooking ?? 0).toFixed(2)}` },
      ]);
      setShowSuccessModal(true);
      setSelectedPlug(null);
    } catch (error) {
      console.log('Booking error:', error);
      openFeedbackModal('error', 'Error', 'Something went wrong.');
    } finally {
      setSubmittingBooking(false);
    }
  };

  return (
    <View style={{
      backgroundColor: '#FFFFFF',
      borderTopLeftRadius: 22,
      borderTopRightRadius: 22,
      borderBottomLeftRadius: 14,
      borderBottomRightRadius: 14,
      paddingTop: 12, paddingHorizontal: 14, paddingBottom: 12,
      shadowColor: '#000',
      shadowOffset: { width: 0, height: 4 },
      shadowOpacity: 0.12,
      shadowRadius: 10,
      elevation: 8,
    }}>
      <TouchableOpacity
        style={{ position: 'absolute', right: 16, top: 16, zIndex: 10 }}
        onPress={() => { setSelectedEV(null); setSelectedPlug(null); }}
      >
        <Ionicons name="close-circle" size={28} color="#ccc" />
      </TouchableOpacity>

      <Text style={{ fontSize: 17, fontWeight: '800', color: text, paddingRight: 36, marginBottom: 2}}>{selectedEV.name}
      </Text>
      <Text style={{ fontSize: 14, color: subtext, marginBottom: 4 }}>{selectedEV.address}</Text>

      <View style={{ height: 1, backgroundColor: '#f0f0f0', marginBottom: 6 }} />

      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
        <Text style={{ fontSize: 13, color: subtext, fontWeight: '700' }}>
          Total lots: {totalLots}
        </Text>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
          <TouchableOpacity
            onPress={() => setLotsPage((prev) => Math.max(1, prev - 1))}
            disabled={lotsPage <= 1}
            style={{
              width: 26,
              height: 26,
              borderRadius: 13,
              borderWidth: 1,
              borderColor: '#CDE2EE',
              alignItems: 'center',
              justifyContent: 'center',
              opacity: lotsPage <= 1 ? 0.4 : 1,
            }}
          >
            <Ionicons name="chevron-back" size={14} color={text} />
          </TouchableOpacity>
          <Text style={{ fontSize: 12, color: subtext, minWidth: 58, textAlign: 'center' }}>
            {lotsPage}/{totalLotPages}
          </Text>
          <TouchableOpacity
            onPress={() => setLotsPage((prev) => Math.min(totalLotPages, prev + 1))}
            disabled={lotsPage >= totalLotPages}
            style={{
              width: 26,
              height: 26,
              borderRadius: 13,
              borderWidth: 1,
              borderColor: '#CDE2EE',
              alignItems: 'center',
              justifyContent: 'center',
              opacity: lotsPage >= totalLotPages ? 0.4 : 1,
            }}
          >
            <Ionicons name="chevron-forward" size={14} color={text} />
          </TouchableOpacity>
        </View>
      </View>

      <View style={{ flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'flex-start', gap: 4, marginBottom: 4 }}>
        {pagedChargingPoints.map((cp: any, index: number) => {
          const plug = cp.plugTypes?.[0];
          const status = cp.status;
          const plugType = String(plug?.plugType ?? '');
          const hasSavedType = hasSavedChargingType(userChargingType);
          const isCompatible = hasSavedType && matchesSavedChargingType(plugType, userChargingType);
          const powerRating = plug?.powerRating ?? 0;
          const price = plug?.price ? `$${parseFloat(plug.price).toFixed(4)}/${plug.priceType ?? 'kWh'}` : null;
          const isUnknownStatus = status === 'unknown' || (!selectedEV.hasLiveStatus);
          let bgColor = '#e74c3c';
          let statusEmoji = '🔴';
          let statusLabel = 'Unavailable';
          if (isUnknownStatus) { bgColor = '#95a5a6'; statusEmoji = '⚪'; statusLabel = 'Status unknown'; }
          else if (status === '1') { bgColor = '#2ecc71'; statusEmoji = '🟢'; statusLabel = 'Available'; }
          else if (status === '0') { bgColor = '#f39c12'; statusEmoji = '🟡'; statusLabel = 'Occupied'; }
          const canSelect = status === '1' && isCompatible;
          const isSelected = selectedPlug === cp;
          return (
            <TouchableOpacity
              key={`${pageStart + index}-${cp.position ?? 'lot'}`}
              disabled={!canSelect}
              onPress={() => {
                if (!isCompatible) {
                  openFeedbackModal('warning', 'Incompatible Plug', 'This charging head does not match your saved charging type.');
                  return;
                }
                setSelectedPlug(cp);
              }}
              style={{
                width: '31.5%', minHeight: 86, borderRadius: 14, paddingVertical: 2, paddingHorizontal: 4, alignItems: 'center', justifyContent: 'center',
                backgroundColor: isSelected ? '#1A5276' : bgColor + '22',
                borderWidth: 2, borderColor: isSelected ? '#2C6E85' : bgColor,
                opacity: canSelect ? 1 : 0.55,
              }}
            >
              <Text style={{ fontWeight: '700', fontSize: 13, marginTop: 2, color: isSelected ? '#fff' : text, textAlign: 'center' }}>
                {plug?.plugType ?? 'N/A'}
              </Text>
              <Text style={{ fontSize: 12, color: isSelected ? '#ddd' : subtext, marginTop: 2 }}>{powerRating} kW</Text>
              {price && (
                <Text style={{ fontSize: 12, color: isSelected ? '#90EE90' : evGreen, marginTop: 2, fontWeight: '700' }}>{price}</Text>
              )}
              <Text style={{ fontSize: 12, color: isSelected ? '#ccc' : subtext, marginTop: 1 }}>📍 {cp.position}</Text>
              <Text style={{ fontSize: 12, marginTop: 5 }}>{statusEmoji} {statusLabel}</Text>
            </TouchableOpacity>
          );
        })}
      </View>

      <TouchableOpacity
        style={{
          marginTop: 8,
          minHeight: 44,
          borderRadius: 14,
          alignItems: 'center',
          justifyContent: 'center',
          paddingHorizontal: 16,
          backgroundColor: selectedPlug ? '#2C6E85' : '#ccc',
        }}
        onPress={async () => {
          if (userCarType !== 'ELECTRIC') {
            setShowEvOnlyModal(true);
            return;
          }

          const history = await getBookingHistory();
          const hasActiveBooking = history.some(
            (booking) => booking.status === 'RESERVED'
          );
          if (hasActiveBooking) {
            openFeedbackModal('warning', 'Active Booking Exists', 'You already have an active booking. Please complete or cancel it first.');
            return;
          }

          if (!hasSavedChargingType(userChargingType)) {
            openFeedbackModal('warning', 'Missing Charging Type', 'Please set your charging type in Profile before booking.');
            return;
          }
          if (!selectedPlug) {
            openFeedbackModal('warning', 'Select Plug', 'Please select an available charging plug first.');
            return;
          }

          const plug = selectedPlug.plugTypes?.[0];
          if (!matchesSavedChargingType(String(plug?.plugType ?? ''), userChargingType)) {
            openFeedbackModal('warning', 'Incompatible Plug', 'Selected charging head is incompatible with your saved charging type.');
            return;
          }
          const evId = plug?.evIds?.[0]?.evCpId;

          if (!evId) {
            openFeedbackModal('error', 'Error', 'Unable to retrieve charging point ID.');
            return;
          }

          const priceStr = plug?.price
            ? `$${parseFloat(plug.price).toFixed(4)}/${plug.priceType ?? 'kWh'}`
            : 'Price unavailable';

          setPendingBookingPayload({
            evChargingPointId: evId,
          });
          setConfirmDetails([
            { label: 'Plug', value: `${plug?.plugType} (${plug?.powerRating}kW)` },
            { label: 'Lot', value: selectedPlug.position },
            { label: 'Location', value: selectedEV.name, fullWidth: true },
            { label: 'Price', value: priceStr, fullWidth: true },
            { label: 'Notice', value: 'You will be charged $2.00. Non refundable.', fullWidth: true },
          ]);
          setShowConfirmModal(true);
        }}
      >
        <Text style={{ color: 'white', fontWeight: '700', fontSize: 15 }}>
          {selectedPlug ? ' Book Charger' : ' Select a plug first'}
        </Text>
      </TouchableOpacity>

      <StatusModal
        visible={showConfirmModal}
        variant="confirm"
        title="Confirm Booking"
        details={confirmDetails}
        primaryLabel={submittingBooking ? 'Confirming...' : 'Confirm'}
        onPrimary={handleConfirmBooking}
        primaryDisabled={submittingBooking}
        secondaryLabel="Cancel"
        onSecondary={() => setShowConfirmModal(false)}
        onRequestClose={() => setShowConfirmModal(false)}
      />

      <StatusModal
        visible={showSuccessModal}
        variant="success"
        title="Booking Confirmed"
        details={successDetails}
        primaryLabel="OK"
        onPrimary={() => setShowSuccessModal(false)}
        onRequestClose={() => setShowSuccessModal(false)}
      />

      <StatusModal
        visible={showEvOnlyModal}
        variant="warning"
        title="EV Only"
        message="Only electric vehicle users can access EV charging booking."
        primaryLabel="OK"
        onPrimary={() => setShowEvOnlyModal(false)}
        onRequestClose={() => setShowEvOnlyModal(false)}
      />

      {/* === POPUP STANDARDIZATION (StatusModal) === */}
      <StatusModal
        visible={showFeedbackModal}
        variant={feedbackVariant}
        title={feedbackTitle}
        message={feedbackMessage}
        primaryLabel="OK"
        onPrimary={handleFeedbackModalPrimary}
        onRequestClose={() => {
          setShowFeedbackModal(false);
          setNavigateToWalletOnFeedbackClose(false);
        }}
      />
    </View>
  );
}
