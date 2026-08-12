import { BG, BORDER, INPUT_BG, PRIMARY, SUBTEXT } from '@/constants/theme';
import StatusModal from '@/components/ui/StatusModal';
import { resendOtp, verifyEmail } from '@/services/authService';
import { Ionicons } from '@expo/vector-icons';
import { router } from 'expo-router';
import React, { useState } from 'react';
import { ActivityIndicator, SafeAreaView, StyleSheet, Text, TextInput, TouchableOpacity, View } from 'react-native';

export default function ValidateEmail() {
  console.log('ValidateEmail SCREEN LOADED')
  const [code, setCode] = useState('');
  const [loading, setLoading] = useState(false);
  const [resending, setResending] = useState(false);
  // === POPUP STANDARDIZATION (StatusModal) ===
  const [popupVisible, setPopupVisible] = useState(false);
  const [popupVariant, setPopupVariant] = useState<'success' | 'error' | 'warning' | 'info' | 'confirm'>('info');
  const [popupTitle, setPopupTitle] = useState('');
  const [popupMessage, setPopupMessage] = useState('');
  const [popupOnPrimary, setPopupOnPrimary] = useState<() => void>(() => () => {});

  const openPopup = (
    variant: 'success' | 'error' | 'warning' | 'info' | 'confirm',
    title: string,
    message: string,
    onPrimary?: () => void
  ) => {
    setPopupVariant(variant);
    setPopupTitle(title);
    setPopupMessage(message);
    setPopupOnPrimary(() => onPrimary ?? (() => {}));
    setPopupVisible(true);
  };

  const handleBack = () => {
    if (router.canGoBack()) {
      router.back();
      return;
    }

    router.replace('/register');
  };

  const handleVerify = async () => {
    if (!code) { openPopup('error', 'Error', 'Please enter the code'); return; }
    console.log('handleVerify called with code:', code)
    setLoading(true);
    try {
      const data = await verifyEmail(code);
      console.log('verify response:', data)      
      openPopup('success', 'Success', 'Email verified successfully!', () => router.replace('/(tabs)'));
    } catch (err: any) {
      console.log('verify error:', err)     
      const status = err?.response?.status

      if (status === 422) {
        openPopup('warning', 'Invalid Code', 'The code you entered is incorrect')
      } else if (status === 410) {
        openPopup('warning', 'Code Expired', 'Your code has expired, request a new one')
      } else {
        openPopup('error', 'Error', 'Something went wrong, please try again')
      }
    } finally {
      setLoading(false);
    }
  }

    // ── Resend OTP ─────────────────────────────────────────
  const handleResend = async () => {
    setResending(true)
    try {
      await resendOtp()
      openPopup('info', 'Code Sent', 'A new code has been sent to your email')
    } catch (err: any) {
      openPopup('error', 'Error', 'Could not resend code, please try again')
    } finally {
      setResending(false)
    }
  }
  return (
    <SafeAreaView style={styles.container}>
      <TouchableOpacity style={styles.topRightBackButton} onPress={handleBack}>
        <Ionicons name="chevron-back" size={20} color={PRIMARY} />
      </TouchableOpacity>

      <Text style={styles.title}>Validate Email</Text>

      {/* Envelope Icon */}
      <View style={styles.iconContainer}>
        <Text style={styles.icon}>✉️</Text>
      </View>

      <TextInput
        style={styles.input}
        placeholder="Enter Code"
        placeholderTextColor={SUBTEXT}
        value={code}
        onChangeText={(val) => setCode(val.replace(/[^0-9]/g, ''))}
        keyboardType="number-pad"
        textAlign="center"
        maxLength={6}
      />

        {/* Verify button */}
      <TouchableOpacity
        style={[styles.button, loading && { opacity: 0.7 }]}
        onPress={handleVerify}
        disabled={loading}
      >
        {loading
          ? <ActivityIndicator color="#fff" />
          : <Text style={styles.buttonText}>Verify</Text>
        }
      </TouchableOpacity>

      <TouchableOpacity
        style={[styles.button, styles.outlineBtn, resending && { opacity: 0.7 }]}
        onPress={handleResend}
        disabled={resending}
      >
        {resending
          ? <ActivityIndicator color={PRIMARY} />
          : <Text style={[styles.buttonText, { color: PRIMARY }]}>Send Code Again</Text>
        }
      </TouchableOpacity>

      {/* === POPUP STANDARDIZATION (StatusModal) === */}
      <StatusModal
        visible={popupVisible}
        variant={popupVariant}
        title={popupTitle}
        message={popupMessage}
        primaryLabel="OK"
        onPrimary={() => {
          setPopupVisible(false);
          popupOnPrimary();
        }}
        onRequestClose={() => setPopupVisible(false)}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: BG, paddingHorizontal: 28, paddingTop: 80, alignItems: 'center' },
  topRightBackButton: {
    position: 'absolute',
    top: 48,
    left: 20,
    width: 38,
    height: 38,
    borderRadius: 19,
    borderWidth: 1,
    borderColor: BORDER,
    backgroundColor: '#fff',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 20,
  },
  title: { fontSize: 22, fontWeight: 'bold', color: '#1A1A2E', marginBottom: 30 },
  iconContainer: {
    width: 90, height: 90, backgroundColor: '#EAF5F7',
    borderRadius: 45, justifyContent: 'center', alignItems: 'center', marginBottom: 30,
  },
  icon: { fontSize: 40 },
  input: {
    width: '100%', backgroundColor: INPUT_BG, borderWidth: 1, borderColor: BORDER,
    borderRadius: 12, padding: 14, fontSize: 18, color: '#1A1A2E',
    letterSpacing: 8, marginBottom: 20,
  },
  button: {
    width: '100%', backgroundColor: PRIMARY, borderRadius: 30,
    padding: 16, alignItems: 'center', marginBottom: 14,
  },
  outlineBtn: { backgroundColor: '#EAF5F7' },
  buttonText: { color: '#fff', fontSize: 16, fontWeight: '700' },
});
