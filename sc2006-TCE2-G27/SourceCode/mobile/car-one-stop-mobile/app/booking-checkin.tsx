import StatusModal from '@/components/ui/StatusModal';
import { BG, INPUT_BG, PRIMARY, SUBTEXT, TEXT } from '@/constants/theme';
import { checkInBooking } from '@/services/evBooking';
import { Ionicons } from '@expo/vector-icons';
import { router, useLocalSearchParams } from 'expo-router';
import React, { useState } from 'react';
import {
  Platform,
  SafeAreaView,
  StatusBar,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';

export default function BookingCheckInScreen() {
  const { bookingId } = useLocalSearchParams<{ bookingId?: string }>();
  const [code, setCode] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [showCompletedModal, setShowCompletedModal] = useState(false);
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

  const submitCheckIn = async () => {
    if (!bookingId) {
      openFeedbackModal('warning', 'Missing booking', 'Booking ID was not provided.');
      return;
    }
    setSubmitting(true);
    const result = await checkInBooking(bookingId);
    setSubmitting(false);

    if (!result) {
      openFeedbackModal('error', 'Check-in failed', 'Please try again.');
      return;
    }

    setShowCompletedModal(true);
  };

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity style={styles.backBtn} onPress={() => router.back()}>
          <Ionicons name="arrow-back-outline" size={20} color={PRIMARY} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>Check In</Text>
        <View style={{ width: 36 }} />
      </View>

      <View style={styles.card}>
        <Text style={styles.label}>Scanner Placeholder</Text>
        <View style={styles.scannerBox}>
          <Text style={styles.scannerText}>Camera scanning (placeholder only)</Text>
        </View>
        <TouchableOpacity style={styles.scanBtn} onPress={submitCheckIn} disabled={submitting}>
          <Text style={styles.scanBtnText}>{submitting ? 'Submitting...' : 'Simulate Scan Success'}</Text>
        </TouchableOpacity>

        <Text style={[styles.label, { marginTop: 20 }]}>Key In Charger Code</Text>
        <TextInput
          style={styles.input}
          placeholder="Enter any code"
          placeholderTextColor={SUBTEXT}
          value={code}
          onChangeText={setCode}
        />
        <TouchableOpacity style={styles.submitBtn} onPress={submitCheckIn} disabled={submitting}>
          <Text style={styles.submitBtnText}>{submitting ? 'Submitting...' : 'Submit Code'}</Text>
        </TouchableOpacity>
      </View>

      <StatusModal
        visible={showCompletedModal}
        variant="success"
        title="Your have checked in successfully!"
        primaryLabel="OK"
        onPrimary={() => {
          setShowCompletedModal(false);
          router.back();
        }}
        onRequestClose={() => {
          setShowCompletedModal(false);
          router.back();
        }}
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
    flex: 1,
    backgroundColor: BG,
    paddingTop: Platform.OS === 'android' ? (StatusBar.currentHeight ?? 24) + 10 : 50,
    padding: 20,
  },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 22 },
  backBtn: {
    width: 36, height: 36, borderRadius: 18,
    backgroundColor: INPUT_BG, borderWidth: 1, borderColor: PRIMARY,
    alignItems: 'center', justifyContent: 'center',
  },
  headerTitle: { fontSize: 18, fontWeight: '700', color: TEXT },
  card: {
    backgroundColor: '#F5F9FA',
    borderRadius: 16,
    padding: 16,
    borderWidth: 1,
    borderColor: '#E3EEF4',
  },
  label: { fontSize: 13, color: SUBTEXT, fontWeight: '700', marginBottom: 8 },
  scannerBox: {
    height: 180,
    borderRadius: 14,
    borderWidth: 1.5,
    borderColor: PRIMARY,
    borderStyle: 'dashed',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#EAF5F7',
    paddingHorizontal: 16,
  },
  scannerText: { fontSize: 14, color: TEXT, textAlign: 'center', fontWeight: '600' },
  input: {
    borderWidth: 1,
    borderColor: PRIMARY,
    borderRadius: 10,
    backgroundColor: BG,
    padding: 12,
    color: TEXT,
    fontSize: 14,
  },
  scanBtn: {
    marginTop: 12,
    backgroundColor: PRIMARY,
    borderRadius: 24,
    paddingVertical: 12,
    alignItems: 'center',
  },
  scanBtnText: { color: '#fff', fontWeight: '700', fontSize: 14 },
  submitBtn: {
    marginTop: 12,
    backgroundColor: PRIMARY,
    borderRadius: 24,
    paddingVertical: 12,
    alignItems: 'center',
  },
  submitBtnText: { color: '#fff', fontWeight: '700', fontSize: 14 },
});
