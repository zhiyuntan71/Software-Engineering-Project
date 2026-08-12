import StatusModal, { StatusModalDetail } from '@/components/ui/StatusModal';
import { BG, BORDER, INPUT_BG, PRIMARY, SUBTEXT, TEXT } from '@/constants/theme';
import { TopUp } from '@/context/UserContext';
import { BookingResponse, cancelBooking, completeBooking, getBookingHistory } from '@/services/evBooking';
import { getWalletTransactions, WalletTransactionResponse } from '@/services/walletService';
import { Ionicons } from '@expo/vector-icons';
import { useFocusEffect } from '@react-navigation/native';
import { router } from 'expo-router';
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Platform,
  SafeAreaView, ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';


const PAGE_SIZE = 3;

function mapTopUpTransactions(transactions: WalletTransactionResponse[]): TopUp[] {
  return transactions
    .filter((tx) => tx.type === 'TOP_UP')
    .map((tx) => {
      const createdAt = new Date(tx.createdAt);
      return {
        id: tx.transactionId,
        method: 'Wallet Top Up',
        date: createdAt.toLocaleDateString('en-GB'),
        time: createdAt.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' }).toLowerCase(),
        amount: `+$${Number(tx.amount).toFixed(2)}`,
      };
    });
}

function toStatusLabel(status: string) {
  return status.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (s) => s.toUpperCase());
}

function formatRemainingTime(ms: number) {
  if (ms <= 0) return 'Expired';
  const totalSeconds = Math.floor(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}m ${String(seconds).padStart(2, '0')}s`;
}

function BookingCard({
  item,
  expanded,
  onToggleExpand,
}: {
  item: BookingResponse;
  expanded: boolean;
  onToggleExpand: () => void;
}) {
  const createdAt = new Date(item.createdAt);
  const date = createdAt.toLocaleDateString('en-GB');
  const time = createdAt.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' }).toLowerCase();
  const amount = `-$${Number(item.bookingFee ?? 0).toFixed(2)}`;
  const locationLabel = item.locationName || 'Unknown location';
  const lotLabel = item.lot ? `Lot ${item.lot}` : null;
  const titleLabel = lotLabel ? `${locationLabel} | ${lotLabel}` : locationLabel;
  const statusStripeColor =
    item.status === 'RESERVED'
      ? PRIMARY
      : item.status === 'CHECKED_IN' || item.status === 'COMPLETED'
        ? '#27AE60'
        : item.status === 'CANCELLED'
          ? '#E74C3C'
          : '#F39C12';

  const statusIcon = (() => {
    if (item.status === 'RESERVED') {
      return <Ionicons name="time-outline" size={24} color={PRIMARY} />;
    }
    if (item.status === 'CHECKED_IN') {
      return <Ionicons name="checkmark-circle" size={28} color="#27AE60" />;
    }
    if (item.status === 'COMPLETED') {
      return <Ionicons name="checkmark-circle" size={28} color="#27AE60" />;
    }
    if (item.status === 'CANCELLED') {
      return <Ionicons name="close-circle" size={28} color="#E74C3C" />;
    }
    return <Ionicons name="alert-circle" size={28} color="#F39C12" />;
  })();

  return (
    <View
      style={[
        styles.card,
        { borderLeftWidth: 4, borderLeftColor: statusStripeColor },
        expanded && styles.expandedCard,
      ]}
    >
      <View style={styles.cardIcon}>
        {statusIcon}
      </View>
      <View style={styles.cardBody}>
        <View style={styles.cardRow}>
          <Text style={styles.cardDate}>{date}  {time}</Text>
          <Text style={styles.cardAmount}>{amount}</Text>
        </View>
        <Text style={styles.cardTitle}>{titleLabel}</Text>
        <View style={styles.cardSubRow}>
          <Text style={styles.cardSub}>{toStatusLabel(item.status)}</Text>
          <TouchableOpacity style={styles.expandBtn} onPress={onToggleExpand}>
            <Ionicons name={expanded ? 'chevron-up' : 'chevron-down'} size={16} color={SUBTEXT} />
          </TouchableOpacity>
        </View>
        {expanded && (
          <View style={styles.detailsBox}>
            <View style={styles.detailsHeader}>
              <TouchableOpacity style={styles.detailsCloseBtn} onPress={onToggleExpand}>
                <Ionicons name="close" size={14} color={SUBTEXT} />
              </TouchableOpacity>
            </View>
            <View style={styles.detailsRow}>
              <View style={styles.detailsPill}>
                <Text style={styles.detailsLabel}>Plug</Text>
                <Text style={styles.detailsValue}>{item.plugType ?? 'N/A'}</Text>
              </View>
              <View style={styles.detailsPill}>
                <Text style={styles.detailsLabel}>Power</Text>
                <Text style={styles.detailsValue}>{item.powerRating ? `${item.powerRating} kW` : 'N/A'}</Text>
              </View>
              <View style={styles.detailsPill}>
                <Text style={styles.detailsLabel}>Price</Text>
                <Text style={styles.detailsValue}>{item.priceDisplay ?? 'N/A'}</Text>
              </View>
            </View>
            <View style={styles.detailsWidePill}>
              <Text style={styles.detailsLabel}>Booking ID</Text>
              <Text style={styles.detailsValue} numberOfLines={1}>{item.bookingId}</Text>
            </View>
            <View style={styles.detailsWidePill}>
              <Text style={styles.detailsLabel}>Charging Point ID</Text>
              <Text style={styles.detailsValue}>{item.evChargingPointId}</Text>
            </View>
          </View>
        )}
      </View>
    </View>
  );
}

function TopUpCard({ item }: { item: TopUp }) {
  return (
    <View style={styles.card}>
      <View style={styles.cardIcon}>
        <Ionicons name="wallet" size={28} color={PRIMARY} />
      </View>
      <View style={styles.cardBody}>
        <View style={styles.cardRow}>
          <Text style={styles.cardDate}>{item.date}  {item.time}</Text>
          <Text style={[styles.cardAmount, { color: '#27AE60' }]}>{item.amount}</Text>
        </View>
        <Text style={styles.cardTitle}>{item.method}</Text>
        <Text style={styles.cardSub}>Wallet Top Up</Text>
      </View>
    </View>
  );
}

function Pagination({ total, page, onPage }: { total: number; page: number; onPage: (p: number) => void }) {
  const totalPages = Math.ceil(total / PAGE_SIZE);
  if (totalPages <= 1) return null;

  const pages: (number | '...')[] = [];
  if (totalPages <= 5) {
    for (let i = 1; i <= totalPages; i++) pages.push(i);
  } else {
    pages.push(1, 2, '...', totalPages - 1, totalPages);
  }

  return (
    <View style={styles.pagination}>
      <TouchableOpacity
        style={[styles.pageBtn, page === 1 && styles.pageBtnDisabled]}
        onPress={() => page > 1 && onPage(page - 1)}
      >
        <Ionicons name="chevron-back" size={16} color={page === 1 ? '#ccc' : PRIMARY} />
      </TouchableOpacity>

      {pages.map((p, i) =>
        p === '...' ? (
          <Text key={`ellipsis-${i}`} style={styles.pageEllipsis}>...</Text>
        ) : (
          <TouchableOpacity
            key={p}
            style={[styles.pageBtn, page === p && styles.pageBtnActive]}
            onPress={() => onPage(p as number)}
          >
            <Text style={[styles.pageBtnText, page === p && styles.pageBtnTextActive]}>
              {p}
            </Text>
          </TouchableOpacity>
        )
      )}

      <TouchableOpacity
        style={[styles.pageBtn, page === totalPages && styles.pageBtnDisabled]}
        onPress={() => page < totalPages && onPage(page + 1)}
      >
        <Ionicons name="chevron-forward" size={16} color={page === totalPages ? '#ccc' : PRIMARY} />
      </TouchableOpacity>
    </View>
  );
}

export default function Transactions() {
  const [tab, setTab] = useState<'bookings' | 'topup'>('bookings');
  const [bookingPage, setBookingPage] = useState(1);
  const [topupPage, setTopupPage] = useState(1);
  const [nowMs, setNowMs] = useState(Date.now());
  const [expandedBookingIds, setExpandedBookingIds] = useState<Record<string, boolean>>({});

  const [bookings, setBookings] = useState<BookingResponse[]>([]);
  const [topups, setTopups] = useState<TopUp[]>([]);
  const [loadingBookings, setLoadingBookings] = useState(true);
  const [loadingTopups, setLoadingTopups] = useState(true);
  const [showCancelConfirmModal, setShowCancelConfirmModal] = useState(false);
  const [showCancelSuccessModal, setShowCancelSuccessModal] = useState(false);
  const [cancellingBooking, setCancellingBooking] = useState(false);
  const [cancelDetails, setCancelDetails] = useState<StatusModalDetail[]>([]);
  // === POPUP STANDARDIZATION (StatusModal) ===
  const [showFeedbackModal, setShowFeedbackModal] = useState(false);
  const [feedbackVariant, setFeedbackVariant] = useState<'success' | 'confirm' | 'warning' | 'error' | 'info'>('info');
  const [feedbackTitle, setFeedbackTitle] = useState('');
  const [feedbackMessage, setFeedbackMessage] = useState('');

  const openFeedbackModal = (
    variant: 'success' | 'confirm' | 'warning' | 'error' | 'info',
    title: string,
    message: string
  ) => {
    setFeedbackVariant(variant);
    setFeedbackTitle(title);
    setFeedbackMessage(message);
    setShowFeedbackModal(true);
  };

  const activeBooking = useMemo(
    () => bookings.find((b) => b.status === 'RESERVED' || b.status === 'CHECKED_IN') ?? null,
    [bookings]
  );

  const activeRemaining = useMemo(() => {
    if (!activeBooking) return '0m 00s';
    const startedMs = new Date(activeBooking.createdAt).getTime();
    const graceMinutes = activeBooking.gracePeriodMinutes || 30;
    const expiresAt = startedMs + graceMinutes * 60 * 1000;
    return formatRemainingTime(expiresAt - nowMs);
  }, [activeBooking, nowMs]);

  useEffect(() => {
    if (!activeBooking) return;
    const timer = setInterval(() => setNowMs(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [activeBooking]);

  const loadTransactions = useCallback(async () => {
    setLoadingBookings(true);
    setLoadingTopups(true);

    try {
      const [bookingData, walletTxs] = await Promise.all([
        getBookingHistory(),
        getWalletTransactions(),
      ]);

      setBookings(bookingData);
      setTopups(mapTopUpTransactions(walletTxs));
    } catch {
      setBookings([]);
      setTopups([]);
    } finally {
      setLoadingBookings(false);
      setLoadingTopups(false);
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      loadTransactions();
    }, [loadTransactions])
  );

  const paginatedBookings = bookings.slice((bookingPage - 1) * PAGE_SIZE, bookingPage * PAGE_SIZE);
  const paginatedTopups = topups.slice((topupPage - 1) * PAGE_SIZE, topupPage * PAGE_SIZE);

  const handleCancelPress = () => {
    if (!activeBooking) return;
    setCancelDetails([
      { label: 'Booking ID', value: activeBooking.bookingId, fullWidth: true },
      { label: 'Fee Charged', value: `-$${Number(activeBooking.bookingFee ?? 0).toFixed(2)}` },
      { label: 'Refund', value: 'No refund' },
    ]);
    setShowCancelConfirmModal(true);
  };

  const handleEndBooking = async () => {
    if (!activeBooking) return;
    setCancellingBooking(true);
    const ok = await cancelBooking(activeBooking.bookingId);
    setCancellingBooking(false);
    if (!ok) {
      openFeedbackModal('error', 'Unable to end booking', 'Please try again.');
      return;
    }
    setShowCancelConfirmModal(false);
    setShowCancelSuccessModal(true);
    await loadTransactions();
  };

  const handleFinishCharging = async () => {
  if (!activeBooking) return;
  const ok = await completeBooking(activeBooking.bookingId);
  if (!ok) {
    openFeedbackModal('error', 'Error', 'Unable to complete booking. Please try again.');
    return;
  }
  openFeedbackModal('success', 'Session Complete', 'Your charging session has ended.');
  await loadTransactions();
};

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
        <View style={styles.header}>
          <TouchableOpacity style={styles.backBtn} onPress={() => router.back()}>
            <Ionicons name="arrow-back-outline" size={20} color={PRIMARY} />
          </TouchableOpacity>
          <Text style={styles.headerTitle}>Transactions</Text>
          <TouchableOpacity style={styles.iconBtn} onPress={() => router.push('/profile')}>
            <Ionicons name="person-outline" size={20} color={PRIMARY} />
          </TouchableOpacity>
        </View>

        <View style={styles.tabBar}>
          <TouchableOpacity
            style={[styles.tabBtn, tab === 'bookings' && styles.tabBtnActive]}
            onPress={() => { setTab('bookings'); setBookingPage(1); }}
          >
            <Text style={[styles.tabText, tab === 'bookings' && styles.tabTextActive]}>
              Bookings
            </Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.tabBtn, tab === 'topup' && styles.tabBtnActive]}
            onPress={() => { setTab('topup'); setTopupPage(1); }}
          >
            <Text style={[styles.tabText, tab === 'topup' && styles.tabTextActive]}>
              Top Up
            </Text>
          </TouchableOpacity>
        </View>

        {tab === 'bookings' && (
          <View>
            {activeBooking && (
              <View style={styles.activeCard}>
                <View style={styles.activeHeader}>
                  <View style={styles.activeDot} />
                  <Text style={styles.activeLabel}>Active Booking</Text>
                  {activeBooking.status === 'RESERVED' && (
                    <Text style={styles.activeTime}>Time left: {activeRemaining}</Text>
                  )}
                </View>
                <View style={styles.activeBody}>
                  <View>
                    <Text style={styles.activeCarparkName}>
                      {activeBooking.locationName
                        ? `${activeBooking.locationName}${activeBooking.lot ? ` | Lot ${activeBooking.lot}` : ''}`
                        : `Point ${activeBooking.evChargingPointId}${activeBooking.lot ? ` | Lot ${activeBooking.lot}` : ''}`}
                    </Text>
                    <View style={styles.activeStatusRow}>
                      <Text style={styles.activeDate}>
                        {new Date(activeBooking.createdAt).toLocaleDateString('en-GB')} {' '}
                        {new Date(activeBooking.createdAt).toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' }).toLowerCase()}
                      </Text>
                      <View style={styles.activeRight}>
                        {activeBooking.status === 'RESERVED' ? (
                          <>
                            <TouchableOpacity
                              style={styles.arrivedBtn}
                              onPress={() =>
                                router.push({
                                  pathname: '/booking-checkin',
                                  params: { bookingId: activeBooking.bookingId },
                                })
                              }
                            >
                              <Text style={styles.arrivedBtnText}>I'm Here</Text>
                            </TouchableOpacity>
                            <TouchableOpacity
                              style={styles.endBtn}
                              onPress={handleCancelPress}
                            >
                              <Text style={styles.endBtnText}>Cancel</Text>
                            </TouchableOpacity>
                          </>
                        ) : (
                          <TouchableOpacity
                            style={[styles.arrivedBtn, { backgroundColor: '#27AE60' }]}
                            onPress={handleFinishCharging}
                          >
                            <Text style={styles.arrivedBtnText}>Finish Charging</Text>
                          </TouchableOpacity>
                        )}
                      </View>
                    </View>
                  </View>
                </View>
              </View>
            )}

            <Text style={styles.sectionTitle}>Booking History</Text>

            {loadingBookings ? (
              <Text style={styles.emptyText}>Loading bookings...</Text>
            ) : bookings.length === 0 ? (
              <Text style={styles.emptyText}>No bookings yet</Text>
            ) : (
              paginatedBookings.map((item) => (
                <BookingCard
                  key={item.bookingId}
                  item={item}
                  expanded={!!expandedBookingIds[item.bookingId]}
                  onToggleExpand={() =>
                    setExpandedBookingIds((prev) => ({
                      ...prev,
                      [item.bookingId]: !prev[item.bookingId],
                    }))
                  }
                />
              ))
            )}

            <Pagination total={bookings.length} page={bookingPage} onPage={setBookingPage} />
          </View>
        )}

        {tab === 'topup' && (
          <View>
            <Text style={styles.sectionTitle}>Top Up History</Text>
            {loadingTopups ? (
              <Text style={styles.emptyText}>Loading top ups...</Text>
            ) : topups.length === 0 ? (
              <Text style={styles.emptyText}>No top ups yet</Text>
            ) : (
              paginatedTopups.map((item) => (
                <TopUpCard key={item.id} item={item} />
              ))
            )}
            <Pagination total={topups.length} page={topupPage} onPage={setTopupPage} />
          </View>
        )}
      </ScrollView>

      <StatusModal
        visible={showCancelConfirmModal}
        variant="warning"
        title="Cancel Booking?"
        message="There will be no refund for this cancellation."
        details={cancelDetails}
        primaryLabel={cancellingBooking ? 'Cancelling...' : 'Cancel'}
        onPrimary={handleEndBooking}
        primaryDisabled={cancellingBooking}
        secondaryLabel="Back"
        onSecondary={() => setShowCancelConfirmModal(false)}
        onRequestClose={() => setShowCancelConfirmModal(false)}
      />

      <StatusModal
        visible={showCancelSuccessModal}
        variant="success"
        title="Booking Cancelled"
        message="Your booking has been cancelled. No refund was issued."
        primaryLabel="OK"
        onPrimary={() => setShowCancelSuccessModal(false)}
        onRequestClose={() => setShowCancelSuccessModal(false)}
      />

      {/* === POPUP STANDARDIZATION (StatusModal) === */}
      <StatusModal
        visible={showFeedbackModal}
        variant={feedbackVariant}
        title={feedbackTitle}
        message={feedbackMessage}
        primaryLabel="OK"
        onPrimary={() => setShowFeedbackModal(false)}
        onRequestClose={() => setShowFeedbackModal(false)}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1, backgroundColor: BG,
    paddingTop: Platform.OS === 'android' ? (StatusBar.currentHeight ?? 24) + 10 : 50,
  },
  scroll: { padding: 20, paddingBottom: 40 },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 24 },
  backBtn: {
    width: 36, height: 36, borderRadius: 18,
    backgroundColor: INPUT_BG, borderWidth: 1, borderColor: PRIMARY,
    alignItems: 'center', justifyContent: 'center',
  },
  iconBtn: {
    width: 36, height: 36, borderRadius: 18,
    backgroundColor: INPUT_BG, borderWidth: 1, borderColor: PRIMARY,
    alignItems: 'center', justifyContent: 'center',
  },
  headerTitle: { fontSize: 18, fontWeight: '700', color: TEXT },
  tabBar: { flexDirection: 'row', backgroundColor: '#C7F9FF', borderRadius: 30, padding: 4, marginBottom: 24 },
  tabBtn: { flex: 1, paddingVertical: 10, borderRadius: 26, alignItems: 'center' },
  tabBtnActive: { backgroundColor: PRIMARY },
  tabText: { fontSize: 15, color: SUBTEXT, fontWeight: '600' },
  tabTextActive: { color: '#fff' },
  sectionTitle: { fontSize: 14, fontWeight: '600', color: SUBTEXT, marginBottom: 12 },
  emptyText: { textAlign: 'center', color: SUBTEXT, fontSize: 14, marginTop: 40 },
  activeCard: {
    backgroundColor: '#EAF5F7', borderRadius: 16,
    padding: 16, marginBottom: 20,
    borderLeftWidth: 4, borderLeftColor: PRIMARY,
  },
  activeHeader: { flexDirection: 'row', alignItems: 'center', marginBottom: 12 },
  activeDot: { width: 8, height: 8, borderRadius: 4, backgroundColor: '#27AE60', marginRight: 8 },
  activeLabel: { fontSize: 13, fontWeight: '700', color: PRIMARY, flex: 1 },
  activeTime: { fontSize: 12, color: SUBTEXT, fontWeight: '600' },
  activeBody: { justifyContent: 'space-between' },
  activeCarparkName: { fontSize: 15, fontWeight: '700', color: TEXT, marginBottom: 4 },
  activeStatusRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 2 },
  activeSpace: { fontSize: 13, color: SUBTEXT, marginBottom: 2 },
  activeDate: { fontSize: 12, color: SUBTEXT },
  activeRight: { flexDirection: 'row', justifyContent: 'flex-end', gap: 8 },
  activeAmount: { fontSize: 18, fontWeight: '700', color: PRIMARY },
  arrivedBtn: { backgroundColor: PRIMARY, borderRadius: 9, paddingHorizontal: 10, paddingVertical: 7 },
  arrivedBtnText: { color: '#fff', fontWeight: '700', fontSize: 13 },
  endBtn: { backgroundColor: '#E74C3C', borderRadius: 10, paddingHorizontal: 12, paddingVertical: 7 },
  endBtnText: { color: '#fff', fontWeight: '700', fontSize: 13 },
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#F5F9FA',
    borderRadius: 16,
    padding: 14,
    marginBottom: 12,
    position: 'relative',
    overflow: 'visible',
  },
  expandedCard: {
    zIndex: 30,
    elevation: 6,
  },
  cardIcon: { marginRight: 12 },
  cardBody: { flex: 1, position: 'relative' },
  cardRow: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 2 },
  cardDate: { fontSize: 12, color: SUBTEXT },
  cardAmount: { fontSize: 14, fontWeight: '700', color: TEXT },
  cardTitle: { fontSize: 14, fontWeight: '600', color: TEXT, textAlign: 'right' },
  cardSubRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'flex-end' },
  cardSub: { fontSize: 12, color: SUBTEXT, textAlign: 'right' },
  expandBtn: { marginLeft: 6, padding: 2 },
  detailsBox: {
    position: 'absolute',
    top: 54,
    left: 0,
    right: 0,
    borderRadius: 10,
    paddingVertical: 10,
    paddingHorizontal: 10,
    backgroundColor: '#EDF3F7',
    borderWidth: 1,
    borderColor: '#D9E6EE',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.15,
    shadowRadius: 4,
    elevation: 5,
  },
  detailsRow: { flexDirection: 'row', gap: 8, marginBottom: 8 },
  detailsHeader: { alignItems: 'flex-end', marginBottom: 6 },
  detailsCloseBtn: {
    width: 22,
    height: 22,
    borderRadius: 11,
    borderWidth: 1,
    borderColor: '#D7E6EE',
    backgroundColor: '#F5FAFD',
    alignItems: 'center',
    justifyContent: 'center',
  },
  detailsPill: {
    flex: 1,
    borderRadius: 9,
    borderWidth: 1,
    borderColor: '#D7E6EE',
    backgroundColor: '#F5FAFD',
    paddingVertical: 7,
    paddingHorizontal: 8,
  },
  detailsWidePill: {
    borderRadius: 9,
    borderWidth: 1,
    borderColor: '#D7E6EE',
    backgroundColor: '#F5FAFD',
    paddingVertical: 7,
    paddingHorizontal: 8,
    marginBottom: 8,
  },
  detailsLabel: { fontSize: 10, color: SUBTEXT, fontWeight: '700', marginBottom: 2, textTransform: 'uppercase' },
  detailsValue: { fontSize: 11, color: TEXT, fontWeight: '600' },
  pagination: { flexDirection: 'row', justifyContent: 'center', alignItems: 'center', marginTop: 16, gap: 6 },
  pageBtn: { width: 34, height: 34, borderRadius: 17, borderWidth: 1, borderColor: BORDER, alignItems: 'center', justifyContent: 'center' },
  pageBtnActive: { backgroundColor: PRIMARY, borderColor: PRIMARY },
  pageBtnDisabled: { borderColor: '#eee' },
  pageBtnText: { fontSize: 13, color: TEXT, fontWeight: '600' },
  pageBtnTextActive: { color: '#fff' },
  pageEllipsis: { fontSize: 13, color: SUBTEXT, paddingHorizontal: 4 },
});



