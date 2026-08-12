import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  View, Text, StyleSheet, SafeAreaView,
  TextInput, TouchableOpacity, FlatList,
  Platform, StatusBar,
} from 'react-native';
import MapView, { Marker } from 'react-native-maps';
import MapViewDirections from 'react-native-maps-directions';
import { router } from 'expo-router';
import * as Location from 'expo-location';
import { Ionicons } from '@expo/vector-icons';

import { searchSuggestions, Suggestion } from '@/services';
import { EVLocation } from '@/services/evCharger';
import { CarparkLocation } from '@/services/carpark';
import { PRIMARY, BG, INPUT_BG, BORDER, TEXT, SUBTEXT, EV_GREEN, CARPARK_BLUE } from '@/constants/theme';
import { SEARCH_DEBOUNCE_MS, DEFAULT_REGION } from '@/constants/config';
import { useUser } from '@/context/UserContext';
import api from '@/services/Registerapi';
import { getRecommendationPreferences } from '@/services/recommendation';
import StatusModal from '@/components/ui/StatusModal';

import {
  EVMarkers, EVBottomSheet, EVBadge,
  fetchEVChargers, getSortedFilteredEVChargers, hasSavedChargingType,
  fetchEVRecommendations, applyEVRecommendationOrder,
} from '@/components/home/evMain';
import { EVRecommendationItem } from '@/services/recommendation';
import {
  CarparkMarkers, CarparkBadge, CarparkBottomSheet, fetchCarparkRecommendations,
} from '@/components/home/parkingMain';
import { getLatestAnnouncement } from '@/services/adminService';

type PreferenceMode = 'cheapest' | 'nearest' | 'most_available' | 'custom';
type ParkingDuration = 1 | 2 | 3 | 4;
const ROUTE_MID_BLUE = '#1A73E8';
const EV_BASE_SEARCH_RADIUS_KM = 0.5;
const EV_EXPANDED_SEARCH_RADIUS_KM = 1.0;
const CARPARK_BASE_SEARCH_RADIUS_KM = 0.5;
const CARPARK_EXPANDED_SEARCH_RADIUS_KM = 1.0;

export default function Index() {
  const { setBalance, carType, chargingType } = useUser();
  const mapRef = useRef<MapView>(null);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const locationWatchRef = useRef<Location.LocationSubscription | null>(null);
  const carparkRequestSeqRef = useRef(0);
  const routeWaypointsRef = useRef<Array<{ latitude: number; longitude: number }>>([]);
  const isMapRecenteredRef = useRef(true);

  const [search, setSearch] = useState('');
  const [suggestions, setSuggestions] = useState<Suggestion[]>([]);
  const [markerCoord, setMarkerCoord] = useState<{ latitude: number; longitude: number } | null>(null);
  const [markerTitle, setMarkerTitle] = useState('');
  const [searchedCoord, setSearchedCoord] = useState<{ lat: number; lon: number } | null>(null);
  const [userLocation, setUserLocation] = useState<{ latitude: number; longitude: number } | null>(null);
  const [navDestination, setNavDestination] = useState<{
    latitude: number; longitude: number; title: string;
  } | null>(null);

  const [evChargers, setEVChargers] = useState<EVLocation[]>([]);
  const [loadingEV, setLoadingEV] = useState(false);
  const [showEV, setShowEV] = useState(false);
  const [showAllChargingTypes, setShowAllChargingTypes] = useState(false);

  const [carparks, setCarparks] = useState<CarparkLocation[]>([]);
  const [carparkRecommendations, setCarparkRecommendations] = useState<CarparkLocation[]>([]);
  const [loadingCarparks, setLoadingCarparks] = useState(false);
  const [showCarparks, setShowCarparks] = useState(false);

  const [selectedEV, setSelectedEV] = useState<EVLocation | null>(null);
  const [selectedPlug, setSelectedPlug] = useState<any | null>(null);
  const [selectedCarpark, setSelectedCarpark] = useState<CarparkLocation | null>(null);

  // ── Carpark preference state ────────────────────────────────────────────────
  const [preference, setPreference] = useState<PreferenceMode>('nearest');
  const [durationHours, setDurationHours] = useState<number | null>(null);
  const [carparkSearchRadiusKm, setCarparkSearchRadiusKm] = useState(CARPARK_BASE_SEARCH_RADIUS_KM);
  const [showCustom, setShowCustom] = useState(false);
  const [showPreferencePanel, setShowPreferencePanel] = useState(true);
  const [wCost, setWCost] = useState(0.33);
  const [wDistance, setWDistance] = useState(0.34);
  const [wAvail, setWAvail] = useState(0.33);
  const [costStars, setCostStars] = useState(3);
  const [distStars, setDistStars] = useState(3);
  const [availStars, setAvailStars] = useState(3);

  // ── EV preference state (independent from carpark) ─────────────────────────
  const [evPreference, setEvPreference] = useState<PreferenceMode>('nearest');
  const [evDurationHours, setEvDurationHours] = useState<number | null>(null);
  const [evSearchRadiusKm, setEvSearchRadiusKm] = useState(EV_BASE_SEARCH_RADIUS_KM);
  const [evShowCustom, setEvShowCustom] = useState(false);
  const [evWCost, setEvWCost] = useState(0.33);
  const [evWDistance, setEvWDistance] = useState(0.34);
  const [evWAvail, setEvWAvail] = useState(0.33);
  const [evCostStars, setEvCostStars] = useState(3);
  const [evDistStars, setEvDistStars] = useState(3);
  const [evAvailStars, setEvAvailStars] = useState(3);
  const [evRecommendations, setEvRecommendations] = useState<EVRecommendationItem[]>([]);
  const [loadingEVRec, setLoadingEVRec] = useState(false);
  const evRequestSeqRef = React.useRef(0);

  const [isNavigating, setIsNavigating] = useState(false);
  const [googleMapsApiKey, setGoogleMapsApiKey] = useState<string | null>(null);
  const [navSteps, setNavSteps] = useState<any[]>([]);
  const [currentStepIndex, setCurrentStepIndex] = useState(0);
  const [eta, setEta] = useState<string | null>(null);
  const [distanceRemaining, setDistanceRemaining] = useState<string | null>(null);
  const [isMapRecentered, setIsMapRecentered] = useState(true);
  // === POPUP STANDARDIZATION (StatusModal) ===
  const [popupVisible, setPopupVisible] = useState(false);
  const [popupVariant, setPopupVariant] = useState<'success' | 'confirm' | 'warning' | 'error' | 'info'>('info');
  const [popupTitle, setPopupTitle] = useState('');
  const [popupMessage, setPopupMessage] = useState('');
  const [latestAnnouncement, setLatestAnnouncement] = useState<{ title: string; message: string } | null>(null);

  const openPopup = (
    variant: 'success' | 'confirm' | 'warning' | 'error' | 'info',
    title: string,
    message: string
  ) => {
    setPopupVariant(variant);
    setPopupTitle(title);
    setPopupMessage(message);
    setPopupVisible(true);
  };

  // Helper — keeps state and ref in sync
  const setRecentered = (val: boolean) => {
    setIsMapRecentered(val);
    isMapRecenteredRef.current = val;
  };

  useEffect(() => {
    api.get('/map/config/maps-key')
      .then(res => setGoogleMapsApiKey(res.data.apiKey))
      .catch(err => console.warn('Failed to fetch maps API key:', err));
  }, []);

  useEffect(() => {
    getRecommendationPreferences()
      .then((pref) => setDurationHours(pref.parkingDurationHours))
      .catch(() => {
        // Ignore preference load failures; user can still pick duration manually.
      });
  }, []);

  useEffect(() => {
    const checkAnnouncement = async () => {
      try {
        const announcement = await getLatestAnnouncement();
        if (announcement) {
          setLatestAnnouncement({ title: announcement.title, message: announcement.message });
          openPopup('info', announcement.title, announcement.message);
        }
      } catch {
      }
    };
    checkAnnouncement();
  }, []);

  const handleAnnouncementBubblePress = () => {
    if (!latestAnnouncement) return;
    openPopup('info', latestAnnouncement.title, latestAnnouncement.message);
  };

  const originCoord = useMemo(
    () => searchedCoord
      ? { latitude: searchedCoord.lat, longitude: searchedCoord.lon }
      : userLocation,
    [searchedCoord, userLocation]
  );

  const sortedCarparks = useMemo(() => carparks, [carparks]);
  const canUseSavedChargingType = hasSavedChargingType(chargingType);
  const savedChargingTypeLabel = chargingType ? chargingType.replace(/_/g, ' ') : 'NOT SET';

  const sortedEVChargers = useMemo(() => {
    if (evRecommendations.length > 0) {
      return applyEVRecommendationOrder(evChargers, evRecommendations);
    }
    return getSortedFilteredEVChargers({
      evChargers, searchedCoord, preference: evPreference,
      wDistance: evWDistance, wAvail: evWAvail, chargingType, showAllChargingTypes,
    });
  }, [evChargers, evRecommendations, searchedCoord, evPreference, evWDistance, evWAvail, chargingType, showAllChargingTypes]);

  const handleConfirmCustom = () => {
    if (costStars < 1 || distStars < 1 || availStars < 1) {
      openPopup('warning', 'Missing Ratings', 'Each preference must have at least 1 star. Please rate all 3 parameters before confirming.');
      return;
    }
    // Convert stars to weights: weight = stars / totalStars (always sums to 1.0)
    const totalStars = costStars + distStars + availStars;
    const newWCost = Math.round((costStars / totalStars) * 10000) / 10000;
    const newWDist = Math.round((distStars / totalStars) * 10000) / 10000;
    const newWAvail = Math.round((1 - newWCost - newWDist) * 10000) / 10000;
    setWCost(newWCost);
    setWDistance(newWDist);
    setWAvail(newWAvail);
    if (showCarparks && searchedCoord && durationHours !== null) {
      fetchCarparksOnConfirm({ pref: 'custom', dur: durationHours, wC: newWCost, wD: newWDist, wA: newWAvail });
    } else {
      setShowPreferencePanel(false);
    }
  };

  const preferenceLabel = (p: PreferenceMode) => {
    if (p === 'cheapest')       return 'Cheapest';
    if (p === 'nearest')        return 'Nearest';
    if (p === 'most_available') return 'Most Available';
    return 'Custom';
  };

  const getOrFetchUserLocation = async (): Promise<{ lat: number; lon: number } | null> => {
    if (userLocation) return { lat: userLocation.latitude, lon: userLocation.longitude };
    const { status } = await Location.requestForegroundPermissionsAsync();
    if (status !== 'granted') {
      openPopup('warning', 'Permission Denied', 'Allow location access to search nearby.');
      return null;
    }
    const location = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.High });
    const { latitude, longitude } = location.coords;
    setUserLocation({ latitude, longitude });
    return { lat: latitude, lon: longitude };
  };

  const computeBearing = (
    from: { latitude: number; longitude: number },
    to: { latitude: number; longitude: number }
  ): number => {
    const lat1 = (from.latitude * Math.PI) / 180;
    const lat2 = (to.latitude * Math.PI) / 180;
    const dLon = ((to.longitude - from.longitude) * Math.PI) / 180;
    const y = Math.sin(dLon) * Math.cos(lat2);
    const x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
    return ((Math.atan2(y, x) * 180) / Math.PI + 360) % 360;
  };

  const getRouteBearing = (userPos: { latitude: number; longitude: number }): number => {
    const waypoints = routeWaypointsRef.current;
    if (!waypoints || waypoints.length < 2) return 0;
    let closestIdx = 0;
    let minDist = Infinity;
    for (let i = 0; i < waypoints.length; i++) {
      const dLat = waypoints[i].latitude - userPos.latitude;
      const dLon = waypoints[i].longitude - userPos.longitude;
      const dist = dLat * dLat + dLon * dLon;
      if (dist < minDist) { minDist = dist; closestIdx = i; }
    }
    const lookAheadIdx = Math.min(closestIdx + 2, waypoints.length - 1);
    if (lookAheadIdx === closestIdx) return computeBearing(userPos, waypoints[closestIdx]);
    return computeBearing(userPos, waypoints[lookAheadIdx]);
  };

  const computeAheadCenter = (latitude: number, longitude: number, heading: number) => {
    const headingRad = (heading * Math.PI) / 180;
    const offsetDistance = 0.0006;
    return {
      latitude: latitude + offsetDistance * Math.cos(headingRad),
      longitude: longitude + offsetDistance * Math.sin(headingRad),
    };
  };

  const recenterMap = () => {
    if (!userLocation) return;
    setRecentered(true);
    const routeHeading = getRouteBearing(userLocation);
    const aheadCenter = computeAheadCenter(userLocation.latitude, userLocation.longitude, routeHeading);
    mapRef.current?.animateCamera({
      center: aheadCenter, heading: routeHeading, pitch: 70, zoom: 19, altitude: 100,
    }, { duration: 600 });
  };

  const getNavDestinationCoord = () => {
    if (selectedCarpark) return { latitude: selectedCarpark.latitude, longitude: selectedCarpark.longitude };
    if (selectedEV) return { latitude: selectedEV.latitude, longitude: selectedEV.longitude };
    return navDestination ?? markerCoord;
  };

  const startNavigation = async () => {
    const destination = getNavDestinationCoord();
    if (!destination) {
      openPopup('warning', 'No destination', 'Select an EV station, carpark, or search for a place first.');
      return;
    }
    if (!googleMapsApiKey) {
      openPopup('error', 'Error', 'Maps API key not loaded yet. Try again.');
      return;
    }
    let origin = userLocation;
    if (!origin) {
      const loc = await getOrFetchUserLocation();
      if (!loc) return;
      origin = { latitude: loc.lat, longitude: loc.lon };
    }

    const navTitle = selectedCarpark?.development ?? selectedEV?.name ?? markerTitle ?? 'Destination';
    setNavDestination({ latitude: destination.latitude, longitude: destination.longitude, title: navTitle });
    setIsNavigating(true);
    setCurrentStepIndex(0);
    setRecentered(true);

    const initialHeading = computeBearing(origin, destination);
    const aheadCenter = computeAheadCenter(origin.latitude, origin.longitude, initialHeading);
    mapRef.current?.animateCamera({
      center: aheadCenter, heading: initialHeading, pitch: 70, zoom: 19, altitude: 100,
    }, { duration: 800 });

    locationWatchRef.current = await Location.watchPositionAsync(
      { accuracy: Location.Accuracy.BestForNavigation, distanceInterval: 10 },
      (loc) => {
        const { latitude, longitude } = loc.coords;
        setUserLocation({ latitude, longitude });
        if (isMapRecenteredRef.current) {
          const routeHeading = getRouteBearing({ latitude, longitude });
          const aheadCenter = computeAheadCenter(latitude, longitude, routeHeading);
          mapRef.current?.animateCamera({
            center: aheadCenter, heading: routeHeading, pitch: 70, zoom: 19, altitude: 100,
          }, { duration: 500 });
        }
      }
    );
  };

  const stopNavigation = () => {
    // Remove watch FIRST — prevents final GPS tick from firing animateCamera
    isMapRecenteredRef.current = false
    locationWatchRef.current?.remove();
    locationWatchRef.current = null;

    routeWaypointsRef.current = [];
    setNavDestination(null);
    setIsNavigating(false);
    setNavSteps([]);
    setCurrentStepIndex(0);
    setEta(null);
    setDistanceRemaining(null);
    setRecentered(true);

    if (userLocation) {
      mapRef.current?.animateCamera(
        { center: userLocation, heading: 0, pitch: 0, zoom: 15 },
        { duration: 800 }
      )
    }
  };

  const handleSelectEV = (ev: EVLocation | null) => {
    setSelectedEV(ev);
    if (!ev && !selectedCarpark) setNavDestination(null);
  };

  const handleSelectCarpark = (carpark: CarparkLocation | null) => {
    setSelectedCarpark(carpark);
    if (!carpark && !selectedEV) setNavDestination(null);
  };

  const handleMyLocation = async () => {
    const loc = await getOrFetchUserLocation();
    if (!loc) return;
    await flyToLocation(loc.lat, loc.lon, 'My Location');
  };

  const flyToLocation = async (lat: number, lon: number, title: string) => {
    const coords = { latitude: lat, longitude: lon };
    setMarkerCoord(coords);
    setMarkerTitle(title);
    setSearchedCoord({ lat, lon });
    setSuggestions([]);
    setShowEV(false);
    setShowAllChargingTypes(false);
    setShowCarparks(false);
    setEVChargers([]);
    setEvRecommendations([]);
    setCarparks([]);
    setCarparkRecommendations([]);
    setNavDestination(null);
    setSelectedCarpark(null);
    if (isNavigating) stopNavigation();
    mapRef.current?.animateToRegion({ ...coords, latitudeDelta: 0.02, longitudeDelta: 0.02 }, 1000);
  };

  const flyToCoordSilent = (lat: number, lon: number, title: string) => {
    const coords = { latitude: lat, longitude: lon };
    setMarkerCoord(coords);
    setMarkerTitle(title);
    setSearchedCoord({ lat, lon });
    mapRef.current?.animateToRegion({ ...coords, latitudeDelta: 0.02, longitudeDelta: 0.02 }, 1000);
  };

  const handleEVPress = async () => {
    const newShowEV = !showEV;
    setShowEV(newShowEV);
    if (newShowEV) {
      const activeRadiusKm = EV_BASE_SEARCH_RADIUS_KM;
      setEvSearchRadiusKm(activeRadiusKm);
      setShowAllChargingTypes(false);
      let coord = searchedCoord;
      if (!coord) {
        const loc = await getOrFetchUserLocation();
        if (!loc) { setShowEV(false); return; }
        coord = loc;
        flyToCoordSilent(loc.lat, loc.lon, 'My Location');
      }
      setShowCarparks(false);
      setCarparks([]);
      setCarparkRecommendations([]);
      setShowPreferencePanel(true);
      setLoadingEV(true);
      try {
        const chargers = await fetchEVChargers(coord.lat, coord.lon, activeRadiusKm);
        setEVChargers(chargers);
      } catch (error) {
        console.warn('EV fetch failed:', error);
        openPopup('error', 'Error', 'Failed to load EV chargers. Try again.');
      } finally {
        setLoadingEV(false);
      }
    } else {
      setEvSearchRadiusKm(EV_BASE_SEARCH_RADIUS_KM);
      setShowAllChargingTypes(false);
      setEVChargers([]);
      setEvRecommendations([]);
      setSelectedEV(null);
      setSelectedPlug(null);
      if (!selectedCarpark) setNavDestination(null);
    }
  };

  const handleCarparksPress = async () => {
    const newShow = !showCarparks;
    setShowCarparks(newShow);
    if (newShow) {
      setCarparkSearchRadiusKm(CARPARK_BASE_SEARCH_RADIUS_KM);
      let coord = searchedCoord;
      if (!coord) {
        const loc = await getOrFetchUserLocation();
        if (!loc) { setShowCarparks(false); return; }
        coord = loc;
        flyToCoordSilent(loc.lat, loc.lon, 'My Location');
      }
      setShowEV(false);
      setEVChargers([]);
      setSelectedEV(null);
      setSelectedPlug(null);
      setCarparks([]);
      setCarparkRecommendations([]);
      setShowPreferencePanel(true);
    } else {
      setCarparkSearchRadiusKm(CARPARK_BASE_SEARCH_RADIUS_KM);
      setCarparks([]);
      setCarparkRecommendations([]);
      setSelectedCarpark(null);
      if (!selectedEV) setNavDestination(null);
    }
  };

  // Called exclusively from Confirm buttons — never fires reactively.
  const fetchCarparksOnConfirm = async (opts: {
    pref: PreferenceMode;
    dur: number;
    wC: number;
    wD: number;
    wA: number;
    radiusKm?: number;
  }) => {
    if (!searchedCoord) return;
    const requestSeq = ++carparkRequestSeqRef.current;
    setLoadingCarparks(true);
    try {
      const { candidates, recommendations } = await fetchCarparkRecommendations({
        preference: opts.pref,
        durationHours: opts.dur,
        wDistance: opts.wD,
        wCost: opts.wC,
        lat: searchedCoord.lat,
        lng: searchedCoord.lon,
        searchRadiusKm: opts.radiusKm ?? carparkSearchRadiusKm,
      });
      if (requestSeq === carparkRequestSeqRef.current) {
        setCarparks(candidates);
        setCarparkRecommendations(recommendations);
      }
    } catch (error) {
      if (requestSeq !== carparkRequestSeqRef.current) return;
      console.warn('Carparks fetch failed:', error);
      openPopup('error', 'Error', 'Failed to load recommended carparks. Try again.');
    } finally {
      if (requestSeq === carparkRequestSeqRef.current) {
        setLoadingCarparks(false);
      }
    }
    setShowPreferencePanel(false);
  };

  const handleConfirmNonCustom = () => {
    if (!searchedCoord || durationHours === null) return;
    fetchCarparksOnConfirm({ pref: preference, dur: durationHours, wC: wCost, wD: wDistance, wA: wAvail });
  };

  const handleToggleCarparkSearchRadius = async () => {
    if (!showCarparks || durationHours === null) return;

    const nextRadiusKm = carparkSearchRadiusKm >= CARPARK_EXPANDED_SEARCH_RADIUS_KM
      ? CARPARK_BASE_SEARCH_RADIUS_KM
      : CARPARK_EXPANDED_SEARCH_RADIUS_KM;

    setCarparkSearchRadiusKm(nextRadiusKm);
    await fetchCarparksOnConfirm({
      pref: preference,
      dur: durationHours,
      wC: wCost,
      wD: wDistance,
      wA: wAvail,
      radiusKm: nextRadiusKm,
    });
  };

  const fetchEVRecommendationsOnConfirm = async (opts: {
    pref: PreferenceMode;
    dur: number;
    wC: number;
    wD: number;
    wA: number;
    radiusKm?: number;
  }) => {
    if (!searchedCoord) return;
    const seq = ++evRequestSeqRef.current;
    setLoadingEVRec(true);
    try {
      const results = await fetchEVRecommendations({
        preference: opts.pref,
        durationHours: opts.dur,
        wDistance: opts.wD,
        wCost: opts.wC,
        lat: searchedCoord.lat,
        lng: searchedCoord.lon,
        searchRadiusKm: opts.radiusKm ?? evSearchRadiusKm,
      });
      if (seq === evRequestSeqRef.current) {
        setEvRecommendations(results);
        setShowPreferencePanel(false);
      }
    } catch (error) {
      if (seq !== evRequestSeqRef.current) return;
      console.warn('EV recommendation fetch failed:', error);
      openPopup('error', 'Error', 'Failed to load EV recommendations. Try again.');
    } finally {
      if (seq === evRequestSeqRef.current) setLoadingEVRec(false);
    }
  };

  const handleToggleEVSearchRadius = async () => {
    if (!showEV) return;

    let coord = searchedCoord;
    if (!coord) {
      const loc = await getOrFetchUserLocation();
      if (!loc) return;
      coord = loc;
      flyToCoordSilent(loc.lat, loc.lon, 'My Location');
    }

    const nextRadiusKm = evSearchRadiusKm >= EV_EXPANDED_SEARCH_RADIUS_KM
      ? EV_BASE_SEARCH_RADIUS_KM
      : EV_EXPANDED_SEARCH_RADIUS_KM;
    setLoadingEV(true);
    try {
      setEvSearchRadiusKm(nextRadiusKm);
      const chargers = await fetchEVChargers(coord.lat, coord.lon, nextRadiusKm);
      setEVChargers(chargers);

      if (evDurationHours !== null) {
        await fetchEVRecommendationsOnConfirm({
          pref: evPreference,
          dur: evDurationHours,
          wC: evWCost,
          wD: evWDistance,
          wA: evWAvail,
          radiusKm: nextRadiusKm,
        });
      } else {
        setEvRecommendations([]);
      }
    } catch (error) {
      console.warn('EV toggle search radius failed:', error);
      openPopup('error', 'Error', 'Failed to update EV search radius. Try again.');
    } finally {
      setLoadingEV(false);
    }
  };

  const handleConfirmEVNonCustom = () => {
    if (!searchedCoord || evDurationHours === null) return;
    fetchEVRecommendationsOnConfirm({ pref: evPreference, dur: evDurationHours, wC: evWCost, wD: evWDistance, wA: evWAvail });
  };

  const handleConfirmEVCustom = () => {
    if (evCostStars < 1 || evDistStars < 1 || evAvailStars < 1) {
      openPopup('warning', 'Missing Ratings', 'Each preference must have at least 1 star. Please rate all 3 parameters before confirming.');
      return;
    }
    const total = evCostStars + evDistStars + evAvailStars;
    const newWC = Math.round((evCostStars / total) * 10000) / 10000;
    const newWD = Math.round((evDistStars / total) * 10000) / 10000;
    const newWA = Math.round((1 - newWC - newWD) * 10000) / 10000;
    setEvWCost(newWC);
    setEvWDistance(newWD);
    setEvWAvail(newWA);
    if (showEV && searchedCoord && evDurationHours !== null) {
      fetchEVRecommendationsOnConfirm({ pref: 'custom', dur: evDurationHours, wC: newWC, wD: newWD, wA: newWA });
    } else {
      setShowPreferencePanel(false);
    }
  };

  const handleInputChange = (text: string) => {
    setSearch(text);
    setSuggestions([]);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    if (text.trim().length < 3) return;
    debounceRef.current = setTimeout(async () => {
      const data = await searchSuggestions(text);
      setSuggestions(data);
    }, SEARCH_DEBOUNCE_MS);
  };

  const handleGoPress = async () => {
    if (!search.trim()) return;
    const data = await searchSuggestions(search);
    if (data.length === 0) {
      openPopup('warning', 'Not Found', 'Location not found. Try a different search.');
      return;
    }
    setSearch(data[0].display_name);
    await flyToLocation(parseFloat(data[0].lat), parseFloat(data[0].lon), data[0].display_name);
  };

  const handleSelect = async (item: Suggestion) => {
    setSearch(item.display_name);
    await flyToLocation(parseFloat(item.lat), parseFloat(item.lon), item.display_name);
  };

  const handleClear = () => {
    setSearch('');
    setSuggestions([]);
    setMarkerCoord(null);
    setMarkerTitle('');
    setEVChargers([]);
    setEvRecommendations([]);
    setCarparks([]);
    setCarparkRecommendations([]);
    setSearchedCoord(null);
    setShowEV(false);
    setShowAllChargingTypes(false);
    setShowCarparks(false);
    setSelectedEV(null);
    setSelectedPlug(null);
    setSelectedCarpark(null);
    setNavDestination(null);
    if (isNavigating) stopNavigation();
    if (debounceRef.current) clearTimeout(debounceRef.current);
  };

  const activeDestinationCoord = getNavDestinationCoord();
  const currentInstruction = navSteps[currentStepIndex]
    ?.html_instructions?.replace(/<[^>]+>/g, '') ?? 'Follow the route';

  return (
    <SafeAreaView style={styles.container}>
      {!isNavigating && (
        <>
          <View style={styles.headerRow}>
            <Text style={styles.title}>
              Car<Text style={{ color: PRIMARY }}>One</Text>Stop
            </Text>
            <View style={styles.iconRow}>
              <TouchableOpacity style={styles.iconBtn} onPress={() => router.push('/profile')}>
                <Ionicons name="person-outline" size={22} color={PRIMARY} />
              </TouchableOpacity>
              {!!latestAnnouncement && (
                <TouchableOpacity
                  style={styles.iconBtn}
                  onPress={handleAnnouncementBubblePress}
                  accessibilityRole="button"
                  accessibilityLabel="Open latest announcement"
                >
                  <Ionicons name="megaphone-outline" size={22} color={PRIMARY} />
                </TouchableOpacity>
              )}
              <TouchableOpacity style={styles.iconBtn} onPress={() => router.push('/wallet')}>
                <Ionicons name="wallet-outline" size={22} color={PRIMARY} />
              </TouchableOpacity>
              <TouchableOpacity style={styles.iconBtn} onPress={() => router.push('/transactions')}>
                <Ionicons name="receipt-outline" size={22} color={PRIMARY} />
              </TouchableOpacity>
            </View>
          </View>

          <View style={styles.topSection}>
            <View style={styles.searchRow}>
              <View style={styles.inputWrapper}>
                <TextInput
                  style={styles.input}
                  placeholder="Search destination..."
                  placeholderTextColor={SUBTEXT}
                  value={search}
                  onChangeText={handleInputChange}
                  onSubmitEditing={handleGoPress}
                  returnKeyType="search"
                />
                {search.length > 0 && (
                  <TouchableOpacity style={styles.clearBtn} onPress={handleClear}>
                    <Ionicons name="close" size={16} color={SUBTEXT} />
                  </TouchableOpacity>
                )}
              </View>
              <TouchableOpacity style={styles.searchBtn} onPress={handleGoPress}>
                <Text style={styles.searchBtnText}>Go</Text>
              </TouchableOpacity>
            </View>
            {suggestions.length > 0 && (
              <View style={styles.dropdown}>
                <FlatList
                  data={suggestions}
                  keyExtractor={(item) => item.place_id}
                  keyboardShouldPersistTaps="handled"
                  renderItem={({ item }) => (
                    <TouchableOpacity style={styles.suggestionItem} onPress={() => handleSelect(item)}>
                      <Text style={styles.suggestionText} numberOfLines={2}>{item.display_name}</Text>
                    </TouchableOpacity>
                  )}
                />
              </View>
            )}
          </View>

          {/* Duration selector — must be picked before carparks can be fetched */}
          <View style={styles.tabRow}>
            <TouchableOpacity
              style={[
                styles.tabBtn,
                showCarparks ? styles.tabBtnActive : styles.tabBtnInactive,
              ]}
              onPress={handleCarparksPress}
              disabled={loadingCarparks}
            >
              <Ionicons
                name="car-outline" size={20}
                color={showCarparks ? '#fff' : PRIMARY}
                style={styles.tabIcon}
              />
              <Text style={[
                styles.tabText,
                showCarparks ? styles.tabTextActive : styles.tabTextInactive,
              ]}>
                {loadingCarparks ? 'Loading...' : 'Carparks'}
              </Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.tabBtn, showEV ? styles.tabBtnActive : styles.tabBtnInactive]}
              onPress={handleEVPress}
              disabled={loadingEV}
            >
              <Ionicons name="flash-outline" size={18} color={showEV ? '#fff' : PRIMARY} style={styles.tabIcon} />
              <Text style={[styles.tabText, showEV ? styles.tabTextActive : styles.tabTextInactive]}>
                {loadingEV ? 'Loading...' : 'EV Stations'}
              </Text>
            </TouchableOpacity>
          </View>
        </>
      )}

      <View style={[styles.mapWrapper, isNavigating && styles.mapWrapperFullscreen]}>
        <MapView
          ref={mapRef}
          style={styles.map}
          initialRegion={DEFAULT_REGION}
          rotateEnabled={true}
          pitchEnabled={true}
          onPanDrag={() => {
            if (isNavigating) {
              setIsMapRecentered(false);
              isMapRecenteredRef.current = false;
            }
          }}
          onRegionChangeComplete={(_region, details) => {
            if (isNavigating && details?.isGesture) {
              setIsMapRecentered(false);
              isMapRecenteredRef.current = false;
            }
          }}
        >
          {markerCoord && !navDestination && (
            <Marker coordinate={markerCoord} title={markerTitle} pinColor="red" />
          )}
          {navDestination && (
            <Marker
              coordinate={{ latitude: navDestination.latitude, longitude: navDestination.longitude }}
              title={navDestination.title}
              pinColor="red"
            />
          )}
          {userLocation && (
            <Marker coordinate={userLocation} title="You are here" pinColor="purple" />
          )}

          {isNavigating && userLocation && activeDestinationCoord && googleMapsApiKey && (
            <MapViewDirections
              origin={userLocation}
              destination={activeDestinationCoord}
              apikey={googleMapsApiKey}
              strokeWidth={9}
              strokeColor={ROUTE_MID_BLUE}
              mode="DRIVING"
              onReady={(result) => {
                routeWaypointsRef.current = result.coordinates;
                setNavSteps(result.legs[0]?.steps ?? []);
                setEta(`${Math.round(result.duration)} min`);
                setDistanceRemaining(`${result.distance.toFixed(1)} km`);
              }}
              onError={(err) => {
                openPopup('error', 'Route Error', err);
                stopNavigation();
              }}
            />
          )}

          <EVMarkers
            showEV={showEV}
            sortedEVChargers={sortedEVChargers}
            recommendations={evRecommendations}
            preference={evPreference}
            wDistance={evWDistance}
            wAvail={evWAvail}
            mapRef={mapRef}
            originCoord={originCoord}
            onSelectEV={handleSelectEV}
          />
          <CarparkMarkers
            showCarparks={showCarparks && durationHours !== null}
            candidates={sortedCarparks}
            recommendations={carparkRecommendations}
            preference={preference}
            wDistance={wDistance}
            wAvail={wAvail}
            mapRef={mapRef}
            originCoord={originCoord}
            onSelectCarpark={handleSelectCarpark}
          />
        </MapView>

        {isNavigating && (
          <View style={styles.navHud}>
            <View style={styles.navInstructionRow}>
              <View style={styles.navIconBox}>
                <Ionicons name="navigate" size={22} color="#fff" />
              </View>
              <Text style={styles.navInstructionText} numberOfLines={2}>
                {currentInstruction}
              </Text>
            </View>
            <View style={styles.navMetaRow}>
              <View style={styles.navMetaItem}>
                <Ionicons name="time-outline" size={16} color={SUBTEXT} />
                <Text style={styles.navMetaText}>{eta ?? '...'}</Text>
              </View>
              <View style={styles.navMetaDivider} />
              <View style={styles.navMetaItem}>
                <Ionicons name="location-outline" size={16} color={SUBTEXT} />
                <Text style={styles.navMetaText}>{distanceRemaining ?? '...'}</Text>
              </View>
              <View style={styles.navMetaDivider} />
              <View style={styles.navMetaItem}>
                <Ionicons name="list-outline" size={16} color={SUBTEXT} />
                <Text style={styles.navMetaText}>
                  Step {currentStepIndex + 1}/{navSteps.length || '...'}
                </Text>
              </View>
            </View>
          </View>
        )}

        {isNavigating && !isMapRecentered && (
          <TouchableOpacity style={styles.recenterBtn} onPress={recenterMap}>
            <Ionicons name="locate" size={20} color="#fff" />
            <Text style={styles.recenterBtnText}>Recenter</Text>
          </TouchableOpacity>
        )}

        {!isNavigating && searchedCoord && (showEV || showCarparks) && (
          <View style={styles.preferenceOverlay}>
            <View style={styles.preferenceCard}>
              <TouchableOpacity
                style={styles.preferenceHeader}
                onPress={() => setShowPreferencePanel((p) => !p)}
              >
                <View style={styles.preferenceHeaderTextWrap}>
                  <Text style={styles.preferenceHeaderLabel}>Preference:</Text>
                  <Text style={styles.preferenceHeaderValue}>
                    {preferenceLabel(showEV ? evPreference : preference)}
                  </Text>
                </View>
                <Ionicons name={showPreferencePanel ? 'chevron-up' : 'chevron-down'} size={20} color={SUBTEXT} />
              </TouchableOpacity>

              {showPreferencePanel && (
                <>
                  {/* ── EV panel ───────────────────────────────────────────── */}
                  {showEV && (
                    <>

                    
                      {/* Saved charging type info + toggle */}
                      <View style={styles.savedChargingTypeRow}>
                        <Text style={styles.savedChargingTypeLabel}>Saved charging type:</Text>
                        <Text style={styles.savedChargingTypeValue}>
                          {canUseSavedChargingType ? savedChargingTypeLabel : 'NOT_APPLICABLE'}
                        </Text>
                      </View>
                      {canUseSavedChargingType && !loadingEV && (
                        <TouchableOpacity
                          style={styles.inlineChargingTypeToggle}
                          onPress={() => setShowAllChargingTypes((prev) => !prev)}
                        >
                          <Text style={styles.inlineChargingTypeToggleText}>
                            {showAllChargingTypes ? 'Use Saved Charging Type' : 'Show All Charging Types'}
                          </Text>
                        </TouchableOpacity>
                      )}

                      {/* EV duration selector */}
                      <View style={styles.durationSection}>
                        <View style={styles.durationHeaderRow}>
                          <Text style={styles.durationLabel}>Charging Duration</Text>
                          <Text style={styles.durationValue}>
                            {evDurationHours === null
                              ? 'Required'
                              : (evDurationHours === 4 ? '4h+' : `${evDurationHours}h`)}
                          </Text>
                        </View>
                        <View style={styles.durationPillRow}>
                          {([1, 2, 3, 4] as ParkingDuration[]).map((hours) => {
                            const selected = evDurationHours === hours;
                            return (
                              <TouchableOpacity
                                key={hours}
                                onPress={() => setEvDurationHours(hours)}
                                style={[styles.durationPill, selected && styles.durationPillSelected]}
                              >
                                <Text style={[styles.durationPillText, selected && styles.durationPillTextSelected]}>
                                  {hours === 4 ? '4h+' : `${hours}h`}
                                </Text>
                              </TouchableOpacity>
                            );
                          })}
                        </View>
                      </View>

                      {/* EV preference pills */}
                      <View style={styles.preferenceGrid}>
                        {([
                          { key: 'cheapest',       label: 'Cheapest' },
                          { key: 'nearest',        label: 'Nearest' },
                          { key: 'most_available', label: 'Most Available' },
                          { key: 'custom',         label: 'Custom' },
                        ] as { key: PreferenceMode; label: string }[]).map(({ key, label }) => {
                          const selected = evPreference === key;
                          return (
                            <TouchableOpacity
                              key={key}
                              onPress={() => {
                                setEvPreference(key);
                                setEvShowCustom(key === 'custom');
                              }}
                              style={[styles.preferencePill, selected && styles.preferencePillSelected]}
                            >
                              <Text style={[styles.preferencePillText, selected && styles.preferencePillTextSelected]}>
                                {label}
                              </Text>
                            </TouchableOpacity>
                          );
                        })}
                      </View>

                      {/* EV confirm (non-custom) */}
                      {!evShowCustom && (
                        <TouchableOpacity
                          style={[
                            styles.confirmButton,
                            (evDurationHours === null || !searchedCoord || loadingEVRec)
                              && styles.confirmButtonDisabled,
                          ]}
                          onPress={handleConfirmEVNonCustom}
                          disabled={evDurationHours === null || !searchedCoord || loadingEVRec}
                        >
                          <Text style={styles.confirmButtonText}>
                            {loadingEVRec ? 'Searching…' : 'Confirm'}
                          </Text>
                        </TouchableOpacity>
                      )}

                      {/* EV custom star rating */}
                      {evShowCustom && (() => {
                        const allRated = evCostStars >= 1 && evDistStars >= 1 && evAvailStars >= 1;
                        const rows = [
                          { label: 'Cost',         stars: evCostStars,  setStars: setEvCostStars  },
                          { label: 'Distance',     stars: evDistStars,  setStars: setEvDistStars  },
                          { label: 'Availability', stars: evAvailStars, setStars: setEvAvailStars },
                        ];
                        return (
                          <View style={styles.starSection}>
                            {rows.map(({ label, stars, setStars }) => (
                              <View key={label} style={styles.starRow}>
                                <Text style={styles.starLabel}>{label}</Text>
                                <View style={styles.starPicker}>
                                  {[1, 2, 3, 4, 5].map((n) => (
                                    <TouchableOpacity key={n} onPress={() => setStars(n)} hitSlop={{ top: 8, bottom: 8, left: 4, right: 4 }}>
                                      <Text style={[styles.starGlyph, n <= stars && styles.starGlyphFilled]}>
                                        {n <= stars ? '★' : '☆'}
                                      </Text>
                                    </TouchableOpacity>
                                  ))}
                                </View>
                              </View>
                            ))}
                            <TouchableOpacity
                              style={[styles.confirmButton, (!allRated || evDurationHours === null) && styles.confirmButtonDisabled]}
                              onPress={handleConfirmEVCustom}
                              disabled={!allRated || evDurationHours === null}
                            >
                              <Text style={styles.confirmButtonText}>Confirm</Text>
                            </TouchableOpacity>
                          </View>
                        );
                      })()}
                    </>
                  )}

                  {/* ── Carpark panel ──────────────────────────────────────── */}
                  {showCarparks && (
                    <>
                      <View style={styles.durationSection}>
                        <View style={styles.durationHeaderRow}>
                          <Text style={styles.durationLabel}>Parking Duration</Text>
                          <Text style={styles.durationValue}>
                            {durationHours === null
                              ? 'Required'
                              : (durationHours === 4 ? '4h+' : `${durationHours}h`)}
                          </Text>
                        </View>
                        <View style={styles.durationPillRow}>
                          {([1, 2, 3, 4] as ParkingDuration[]).map((hours) => {
                            const selected = durationHours === hours;
                            return (
                              <TouchableOpacity
                                key={hours}
                                onPress={() => setDurationHours(hours)}
                                style={[styles.durationPill, selected && styles.durationPillSelected]}
                              >
                                <Text style={[styles.durationPillText, selected && styles.durationPillTextSelected]}>
                                  {hours === 4 ? '4h+' : `${hours}h`}
                                </Text>
                              </TouchableOpacity>
                            );
                          })}
                        </View>
                      </View>
                      <View style={styles.preferenceGrid}>
                        {([
                          { key: 'cheapest',       label: 'Cheapest' },
                          { key: 'nearest',        label: 'Nearest' },
                          { key: 'most_available', label: 'Most Available' },
                          { key: 'custom',         label: 'Custom' },
                        ] as { key: PreferenceMode; label: string }[]).map(({ key, label }) => {
                          const selected = preference === key;
                          return (
                            <TouchableOpacity
                              key={key}
                              onPress={() => {
                                setPreference(key);
                                setShowCustom(key === 'custom');
                              }}
                              style={[styles.preferencePill, selected && styles.preferencePillSelected]}
                            >
                              <Text style={[styles.preferencePillText, selected && styles.preferencePillTextSelected]}>
                                {label}
                              </Text>
                            </TouchableOpacity>
                          );
                        })}
                      </View>
                      {!showCustom && (
                        <TouchableOpacity
                          style={[
                            styles.confirmButton,
                            (durationHours === null || !searchedCoord || loadingCarparks)
                              && styles.confirmButtonDisabled,
                          ]}
                          onPress={handleConfirmNonCustom}
                          disabled={durationHours === null || !searchedCoord || loadingCarparks}
                        >
                          <Text style={styles.confirmButtonText}>
                            {loadingCarparks ? 'Searching…' : 'Confirm'}
                          </Text>
                        </TouchableOpacity>
                      )}
                      {showCustom && (() => {
                        const allRated = costStars >= 1 && distStars >= 1 && availStars >= 1;
                        const rows = [
                          { label: 'Cost',         stars: costStars,  setStars: setCostStars  },
                          { label: 'Distance',     stars: distStars,  setStars: setDistStars  },
                          { label: 'Availability', stars: availStars, setStars: setAvailStars },
                        ];
                        return (
                          <View style={styles.starSection}>
                            {rows.map(({ label, stars, setStars }) => (
                              <View key={label} style={styles.starRow}>
                                <Text style={styles.starLabel}>{label}</Text>
                                <View style={styles.starPicker}>
                                  {[1, 2, 3, 4, 5].map((n) => (
                                    <TouchableOpacity key={n} onPress={() => setStars(n)} hitSlop={{ top: 8, bottom: 8, left: 4, right: 4 }}>
                                      <Text style={[styles.starGlyph, n <= stars && styles.starGlyphFilled]}>
                                        {n <= stars ? '★' : '☆'}
                                      </Text>
                                    </TouchableOpacity>
                                  ))}
                                </View>
                              </View>
                            ))}
                            <TouchableOpacity
                              style={[styles.confirmButton, !allRated && styles.confirmButtonDisabled]}
                              onPress={handleConfirmCustom}
                              disabled={!allRated}
                            >
                              <Text style={styles.confirmButtonText}>Confirm</Text>
                            </TouchableOpacity>
                          </View>
                        );
                      })()}
                    </>
                  )}
                </>
              )}
            </View>
          </View>
        )}

        {!selectedEV && !selectedCarpark && !isNavigating && (
          <View style={styles.mapBottomOverlay}>
            <EVBadge
              showEV={showEV} loadingEV={loadingEV}
              sortedEVChargers={sortedEVChargers} evChargers={evChargers}
              showAllChargingTypes={showAllChargingTypes}
              canUseSavedType={canUseSavedChargingType}
              searchedCoord={searchedCoord} searchRadiusKm={evSearchRadiusKm}
              onPress={showEV && !loadingEV ? handleToggleEVSearchRadius : undefined}
              badgeStyle={[styles.overlayBadge, styles.overlayBadgeEv]}
              badgeTextStyle={[styles.overlayBadgeText, styles.overlayBadgeTextEv]}
            />
            <CarparkBadge
              showCarparks={showCarparks && durationHours !== null} loadingCarparks={loadingCarparks}
              carparks={carparks} searchedCoord={searchedCoord}
              durationSelected={durationHours !== null}
              searchRadiusKm={carparkSearchRadiusKm}
              onPress={showCarparks && durationHours !== null && !loadingCarparks ? handleToggleCarparkSearchRadius : undefined}
              carparkBlue={CARPARK_BLUE}
              badgeStyle={styles.overlayBadge} badgeTextStyle={styles.overlayBadgeText}
            />
          </View>
        )}

        {!selectedEV && !selectedCarpark && (
          <TouchableOpacity style={styles.locationFab} onPress={handleMyLocation}>
            <Ionicons name="locate-outline" size={24} color="#fff" />
          </TouchableOpacity>
        )}

      </View>

      {selectedEV && (
        <View style={styles.evSheetOverlay}>
          <EVBottomSheet
            selectedEV={selectedEV}
            selectedPlug={selectedPlug}
            setSelectedEV={(ev) => {
              setSelectedEV(ev);
              if (!ev && !selectedCarpark) setNavDestination(null);
            }}
            setSelectedPlug={setSelectedPlug}
            setBalance={setBalance}
            userCarType={carType}
            userChargingType={chargingType}
            text={TEXT}
            subtext={SUBTEXT}
            evGreen={EV_GREEN}
          />
        </View>
      )}

      {selectedCarpark && !selectedEV && !isNavigating && (
        <View style={styles.evSheetOverlay}>
          <CarparkBottomSheet
            selectedCarpark={selectedCarpark}
            onClose={() => setSelectedCarpark(null)}
            onNavigate={startNavigation}
            carparkBlue={CARPARK_BLUE}
          />
        </View>
      )}

      <TouchableOpacity
        style={[
          styles.button,
          !isNavigating && (selectedEV || selectedCarpark) && styles.buttonHidden,
          isNavigating && styles.buttonStop,
        ]}
        disabled={!isNavigating && !!(selectedEV || selectedCarpark)}
        onPress={isNavigating ? stopNavigation : startNavigation}
      >
        <Text style={styles.buttonText}>
          {isNavigating ? 'Stop Navigation' : 'Start Navigation'}
        </Text>
      </TouchableOpacity>

      {/* === POPUP STANDARDIZATION (StatusModal) === */}
      <StatusModal
        visible={popupVisible}
        variant={popupVariant}
        title={popupTitle}
        message={popupMessage}
        primaryLabel="OK"
        onPrimary={() => setPopupVisible(false)}
        onRequestClose={() => setPopupVisible(false)}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1, backgroundColor: BG, padding: 20,
    paddingTop: Platform.OS === 'android' ? (StatusBar.currentHeight ?? 24) + 10 : 50,
  },
  headerRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 },
  title: { fontSize: 24, fontWeight: 'bold', color: TEXT },
  iconRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  iconBtn: {
    width: 38, height: 38, borderRadius: 19,
    backgroundColor: INPUT_BG, borderWidth: 1, borderColor: BORDER,
    alignItems: 'center', justifyContent: 'center',
  },
  topSection: { paddingTop: 2, marginBottom: 4, zIndex: 50, position: 'relative' },
  searchRow: { flexDirection: 'row', alignItems: 'center', marginBottom: 6 },
  inputWrapper: {
    flex: 1, flexDirection: 'row', alignItems: 'center',
    backgroundColor: '#FFFFFF', borderWidth: 1, borderColor: '#D7E4EC',
    borderTopLeftRadius: 20, borderBottomLeftRadius: 20,
    borderTopRightRadius: 0, borderBottomRightRadius: 0,
    minHeight: 44, paddingLeft: 14, paddingRight: 6,
    shadowColor: '#000', shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.08, shadowRadius: 6, elevation: 2,
  },
  input: { flex: 1, color: TEXT, fontSize: 15, paddingVertical: 8 },
  clearBtn: { width: 28, height: 28, borderRadius: 14, alignItems: 'center', justifyContent: 'center' },
  searchBtn: {
    minHeight: 44, paddingHorizontal: 16, backgroundColor: PRIMARY,
    borderTopRightRadius: 20, borderBottomRightRadius: 20,
    borderTopLeftRadius: 0, borderBottomLeftRadius: 0,
    alignItems: 'center', justifyContent: 'center',
    shadowColor: '#000', shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.08, shadowRadius: 6, elevation: 2, marginLeft: -1,
  },
  searchBtnText: { color: '#FFFFFF', fontWeight: '700', fontSize: 15 },
  dropdown: {
    position: 'absolute', top: 50, left: 0, right: 72,
    backgroundColor: '#FFFFFF', borderWidth: 1, borderColor: '#D6E3EC',
    borderRadius: 14, maxHeight: 220, overflow: 'hidden', zIndex: 100,
    shadowColor: '#000', shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.08, shadowRadius: 8, elevation: 6,
  },
  suggestionItem: { padding: 12, borderBottomWidth: 1, borderBottomColor: '#eee' },
  suggestionText: { fontSize: 13, color: TEXT },
  tabRow: { flexDirection: 'row', justifyContent: 'space-between', gap: 10, marginBottom: 16 },
  tabBtn: {
    flex: 1, minHeight: 38, borderRadius: 19,
    flexDirection: 'row', alignItems: 'center',
    justifyContent: 'center', paddingHorizontal: 10,
  },
  tabBtnActive: { backgroundColor: PRIMARY },
  tabBtnInactive: { backgroundColor: '#FFFFFF', borderWidth: 1.4, borderColor: '#7FAEC6' },
  tabIcon: { marginRight: 5 },
  tabText: { fontSize: 13, fontWeight: '700' },
  tabTextActive: { color: '#FFFFFF' },
  tabTextInactive: { color: PRIMARY },
  mapWrapper: {
    flex: 1, marginBottom: 8, position: 'relative', borderRadius: 20,
    // overflow:'hidden' breaks MapView on Android — the native layer ignores JS clipping
    // and the map renders black until an interaction triggers re-layout. Use 'visible' on
    // Android; iOS can keep 'hidden' for the rounded-corner clip effect.
    overflow: Platform.OS === 'android' ? 'visible' : 'hidden',
    backgroundColor: BG, // white while map tiles load, instead of black
  },
  mapWrapperFullscreen: { marginBottom: 0 },
  map: { flex: 1, borderRadius: Platform.OS === 'android' ? 0 : 20 },
  navHud: {
    position: 'absolute', bottom: 0, left: 0, right: 0,
    backgroundColor: '#fff',
    borderTopLeftRadius: 20, borderTopRightRadius: 20,
    padding: 16, zIndex: 30,
    elevation: 10,
    shadowColor: '#000', shadowOffset: { width: 0, height: -2 },
    shadowOpacity: 0.12, shadowRadius: 8,
  },
  navInstructionRow: {
    flexDirection: 'row', alignItems: 'center', marginBottom: 14,
  },
  navIconBox: {
    width: 40, height: 40, borderRadius: 10,
    backgroundColor: ROUTE_MID_BLUE,
    alignItems: 'center', justifyContent: 'center',
    marginRight: 12,
  },
  navInstructionText: {
    flex: 1, fontSize: 16, fontWeight: '700', color: TEXT,
  },
  navMetaRow: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-around',
    paddingTop: 12, borderTopWidth: 1, borderTopColor: '#F0F0F0',
  },
  navMetaItem: {
    flexDirection: 'row', alignItems: 'center', gap: 6,
  },
  navMetaText: {
    fontSize: 14, fontWeight: '600', color: SUBTEXT,
  },
  navMetaDivider: {
    width: 1, height: 20, backgroundColor: '#E0E0E0',
  },
  recenterBtn: {
    position: 'absolute', bottom: 160, alignSelf: 'center',
    flexDirection: 'row', alignItems: 'center',
    backgroundColor: '#1A73E8',
    borderRadius: 20,
    paddingVertical: 8, paddingHorizontal: 16,
    elevation: 6, zIndex: 31,
    shadowColor: '#000', shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.25, shadowRadius: 4,
    gap: 6,
  },
  recenterBtnText: {
    color: '#fff', fontWeight: '700', fontSize: 14,
  },
  preferenceOverlay: { position: 'absolute', top: 10, left: 10, right: 10, zIndex: 20 },
  preferenceCard: {
    backgroundColor: '#FFFFFF', borderRadius: 12, padding: 10,
    borderWidth: 1, borderColor: '#E6EDF2',
    elevation: 4, shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.08, shadowRadius: 6,
  },
  preferenceHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  preferenceHeaderTextWrap: { flexDirection: 'row', alignItems: 'center' },
  preferenceHeaderLabel: { fontWeight: '700', fontSize: 16, color: TEXT },
  preferenceHeaderValue: { marginLeft: 8, fontWeight: '700', fontSize: 16, color: PRIMARY },
  durationSection: { marginTop: 12, marginBottom: 10 },
  durationHeaderRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 },
  durationLabel: { fontSize: 13, color: SUBTEXT, fontWeight: '600' },
  durationValue: { fontSize: 13, color: PRIMARY, fontWeight: '700' },
  durationPillRow: { flexDirection: 'row', gap: 8 },
  durationRow: { flexDirection: 'row', gap: 8, marginTop: 8 },
  durationPill: {
    flex: 1, height: 38, borderRadius: 11,
    alignItems: 'center', justifyContent: 'center',
    backgroundColor: '#F7FBFE', borderWidth: 1, borderColor: '#CFE0EA',
  },
  durationPillSelected: { backgroundColor: PRIMARY, borderColor: PRIMARY },
  durationPillText: { fontSize: 13, fontWeight: '700', color: TEXT },
  durationPillTextSelected: { color: '#FFFFFF' },
  preferenceGrid: {
    flexDirection: 'row', flexWrap: 'wrap',
    justifyContent: 'space-between', rowGap: 10, marginTop: 4,
  },
  preferencePill: {
    width: '48.5%', height: 40, borderRadius: 12,
    alignItems: 'center', justifyContent: 'center',
    backgroundColor: '#F8FBFD', borderWidth: 1, borderColor: '#D3D3D3',
  },
  preferencePillSelected: { backgroundColor: PRIMARY, borderColor: PRIMARY },
  preferencePillText: { fontSize: 14, fontWeight: '700', color: TEXT },
  preferencePillTextSelected: { color: '#FFFFFF' },
  starSection: { marginTop: 14, paddingHorizontal: 0, rowGap: 8 },
  starRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', height: 34 },
  starLabel: { fontSize: 13, color: TEXT, fontWeight: '600', flex: 1, textAlign: 'left' },
  starPicker: { flexDirection: 'row', gap: 3, marginRight: 0, paddingRight: 0, justifyContent: 'flex-end', alignItems: 'center' },
  starGlyph: { fontSize: 26, color: '#E0D8A8' },
  starGlyphFilled: { color: '#FFD700' },
  confirmButton: { backgroundColor: PRIMARY, borderRadius: 20, paddingVertical: 10, alignItems: 'center', marginTop: 14 },
  confirmButtonDisabled: { backgroundColor: SUBTEXT },
  confirmButtonText: { color: '#fff', fontWeight: '700', fontSize: 14 },
  inlineChargingTypeToggle: {
    minHeight: 42, borderRadius: 14, borderWidth: 1, borderColor: '#A9C4D5',
    backgroundColor: '#F8FBFD', alignItems: 'center', justifyContent: 'center',
    marginTop: 14, marginBottom: 10, paddingHorizontal: 14,
  },
  inlineChargingTypeToggleText: { color: TEXT, fontWeight: '700', fontSize: 14 },
  savedChargingTypeRow: { marginTop: 10, marginBottom: 6, flexDirection: 'row', alignItems: 'center' },
  savedChargingTypeLabel: { fontSize: 13, color: SUBTEXT, fontWeight: '600' },
  savedChargingTypeValue: { marginLeft: 6, fontSize: 13, color: TEXT, fontWeight: '700' },
  mapBottomOverlay: { position: 'absolute', left: 10, right: 76, bottom: 14, zIndex: 19 },
  overlayBadge: {
    borderRadius: 20, paddingVertical: 3, paddingHorizontal: 6,
    alignItems: 'center', borderWidth: 1, borderColor: '#D3D3D3',
  },
  overlayBadgeText: { fontWeight: '700', fontSize: 14 },
  overlayBadgeEv: { backgroundColor: '#E8F5E9' },
  overlayBadgeTextEv: { color: EV_GREEN },
  locationFab: {
    position: 'absolute', bottom: 16, right: 16,
    width: 48, height: 48, borderRadius: 24,
    backgroundColor: PRIMARY, alignItems: 'center', justifyContent: 'center',
    elevation: 5, shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 }, shadowOpacity: 0.25, shadowRadius: 4,
  },
  evSheetOverlay: { position: 'absolute', left: 10, right: 10, bottom: 16, zIndex: 40 },
  button: {
    backgroundColor: PRIMARY, height: 56, borderRadius: 30,
    alignItems: 'center', justifyContent: 'center',
  },
  buttonStop: { backgroundColor: '#E53935' },
  buttonHidden: { opacity: 0 },
  buttonText: { color: '#fff', fontWeight: '700' },
});


