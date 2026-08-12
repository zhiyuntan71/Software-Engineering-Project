import { BG, BORDER, INPUT_BG, PRIMARY, SUBTEXT } from '@/constants/theme';
import StatusModal from '@/components/ui/StatusModal';
import { resendResetOtp, verifyResetOtp } from '@/services/authService';
import { Ionicons } from '@expo/vector-icons';
import { router, useLocalSearchParams } from 'expo-router';
import React, { useRef, useState } from 'react';
import { ActivityIndicator, SafeAreaView, StyleSheet, Text, TextInput, TouchableOpacity, View } from 'react-native';

export default function VerifyResetOtp() {
  const { email: initialEmail } = useLocalSearchParams();
  const [email, setEmail] = useState(typeof initialEmail === 'string' ? initialEmail : '');
  const [code, setCode] = useState('');
  const [loading, setLoading] = useState(false);
  const [resending, setResending] = useState(false);
  const codeInputRef = useRef<TextInput>(null);
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

  const handleResend = async () => {
    if (!email) {
      openPopup('error', 'Error', 'Please enter your email first');
      return;
    }
    setResending(true);
    try {
      await resendResetOtp(email);
      openPopup('info', 'Code Sent', 'A new reset OTP code has been sent to your email');
    } catch {
      openPopup('error', 'Error', 'Could not resend reset OTP code, please try again');
    } finally {
      setResending(false);
    }
  };

  const handleVerify = async () => {
    if (!email || !code) {
      openPopup('error', 'Error', 'Please enter email and OTP code');
      return;
    }
    if (code.length !== 6) {
      openPopup('error', 'Error', 'OTP code must be 6 digits');
      return;
    }

    setLoading(true);
    try {
      await verifyResetOtp(email, code);
      openPopup('success', 'Verified', 'OTP verified. You can now set your new password.', () =>
        router.push({ pathname: '/reset-password', params: { email, otp: code } })
      );
    } catch (err: any) {
      const status = err?.response?.status;
      const backendMessage =
        err?.response?.data?.message ??
        err?.response?.data?.error ??
        '';
      if (status === 422) {
        openPopup('warning', 'Invalid Code', 'The OTP code you entered is incorrect');
      } else if (status === 400) {
        openPopup('warning', 'Invalid or Expired Code', 'Please request a new reset OTP code');
      } else if (status === 403) {
        openPopup(
          'error',
          'Access Blocked',
          'Reset OTP endpoint is blocked by backend security. Please restart backend with latest changes.'
        );
      } else {
        openPopup(
          'error',
          'Error',
          `Something went wrong (${status ?? 'no status'}). ${backendMessage || 'Please try again.'}`
        );
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <TouchableOpacity style={styles.topRightBackButton} onPress={() => router.back()}>
        <Ionicons name="chevron-back" size={20} color={PRIMARY} />
      </TouchableOpacity>

      <Text style={styles.title}>Verify Reset OTP</Text>

      <View style={styles.iconContainer}>
        <Text style={styles.icon}>✉️</Text>
      </View>

      <Text style={styles.label}>Email Address</Text>
      <TextInput
        style={styles.input}
        placeholder="Enter your email"
        placeholderTextColor={SUBTEXT}
        value={email}
        onChangeText={setEmail}
        autoCapitalize="none"
        keyboardType="email-address"
      />

      <Text style={[styles.label, { marginTop: 14 }]}>OTP Code</Text>
      <TextInput
        ref={codeInputRef}
        style={styles.hiddenCodeInput}
        value={code}
        onChangeText={(val) => setCode(val.replace(/[^0-9]/g, ''))}
        keyboardType="number-pad"
        maxLength={6}
      />
      <TouchableOpacity
        activeOpacity={0.9}
        onPress={() => codeInputRef.current?.focus()}
        style={styles.codeBoxesWrap}
      >
        {[0, 1, 2, 3, 4, 5].map((idx) => (
          <View key={idx} style={styles.codeBox}>
            <Text style={styles.codeBoxText}>{code[idx] ?? ''}</Text>
          </View>
        ))}
      </TouchableOpacity>

      <TouchableOpacity
        style={[styles.button, loading && { opacity: 0.7 }]}
        onPress={handleVerify}
        disabled={loading}
      >
        {loading ? <ActivityIndicator color="#fff" /> : <Text style={styles.buttonText}>Verify OTP</Text>}
      </TouchableOpacity>

      <TouchableOpacity
        style={[styles.button, styles.outlineBtn, resending && { opacity: 0.7 }]}
        onPress={handleResend}
        disabled={resending}
      >
        {resending
          ? <ActivityIndicator color={PRIMARY} />
          : <Text style={[styles.buttonText, { color: PRIMARY }]}>Send Code Again</Text>}
      </TouchableOpacity>

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
  title: { fontSize: 22, fontWeight: 'bold', color: '#1A1A2E', marginBottom: 24 },
  iconContainer: {
    width: 90, height: 90, backgroundColor: '#EAF5F7',
    borderRadius: 45, justifyContent: 'center', alignItems: 'center', marginBottom: 26,
  },
  icon: { fontSize: 40 },
  label: { width: '100%', fontSize: 13, color: '#1A1A2E', fontWeight: '600', marginBottom: 6 },
  input: {
    width: '100%', backgroundColor: INPUT_BG, borderWidth: 1, borderColor: BORDER,
    borderRadius: 12, padding: 14, fontSize: 14, color: '#1A1A2E',
  },
  hiddenCodeInput: {
    position: 'absolute',
    width: 1,
    height: 1,
    opacity: 0,
  },
  codeBoxesWrap: {
    width: '100%',
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 20,
  },
  codeBox: {
    width: 46,
    height: 52,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: BORDER,
    backgroundColor: INPUT_BG,
    alignItems: 'center',
    justifyContent: 'center',
  },
  codeBoxText: {
    fontSize: 22,
    color: '#1A1A2E',
    fontWeight: '700',
  },
  button: {
    width: '100%', backgroundColor: PRIMARY, borderRadius: 30,
    padding: 16, alignItems: 'center', marginBottom: 14,
  },
  outlineBtn: { backgroundColor: '#EAF5F7' },
  buttonText: { color: '#fff', fontSize: 16, fontWeight: '700' },
});
