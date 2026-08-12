import { BG, BORDER, INPUT_BG, PRIMARY, SUBTEXT } from '@/constants/theme';
import { forgotPassword } from '@/services/authService';
import StatusModal from '@/components/ui/StatusModal';
import { Ionicons } from '@expo/vector-icons';
import { router } from 'expo-router';
import React, { useState } from 'react';
import { ActivityIndicator, SafeAreaView, StyleSheet, Text, TextInput, TouchableOpacity } from 'react-native';

export default function ForgotPassword() {
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
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

  const handleSend = async () => {
    if (!email) { openPopup('error', 'Error', 'Please enter your email'); return; }

    setLoading(true)
    try {
      await forgotPassword(email)
      openPopup(
        'success',
        'Code Sent',
        'Check your email for the reset OTP code',
        () => router.push({ pathname: '/verify-reset-otp', params: { email } })
      )
    } catch (err: any) {
      openPopup('error', 'Error', 'Email not found or something went wrong')
    } finally {
      setLoading(false)
    }
  }

  return (
    <SafeAreaView style={styles.container}>
      <TouchableOpacity onPress={() => router.back()} style={styles.topRightBackButton}>
        <Ionicons name="chevron-back" size={20} color={PRIMARY} />
      </TouchableOpacity>

      <Text style={styles.title}>Forgot your Password</Text>

      <Text style={styles.desc}>
        Enter your registered email to receive a password reset OTP code
      </Text>

      <Text style={styles.label}>Email Address</Text>
      <TextInput
        style={styles.input}
        placeholder="Enter your Email Address"
        placeholderTextColor={SUBTEXT}
        value={email}
        onChangeText={setEmail}
        keyboardType="email-address"
        autoCapitalize="none"
      />

      <TouchableOpacity
        style={[styles.button, { marginTop: 24 }, loading && { opacity: 0.7 }]}
        onPress={handleSend}
        disabled={loading}
      >
        {loading
          ? <ActivityIndicator color="#fff" />
          : <Text style={styles.buttonText}>Send</Text>
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
  container: { flex: 1, backgroundColor: BG, paddingHorizontal: 28, paddingTop: 60 },
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
  title: { fontSize: 22, fontWeight: 'bold', color: '#1A1A2E', textAlign: 'center', marginBottom: 20 },
  desc: { fontSize: 13, color: SUBTEXT, textAlign: 'center', marginVertical: 20, lineHeight: 20 },
  label: { fontSize: 13, color: '#1A1A2E', fontWeight: '600', marginBottom: 6 },
  input: {
    backgroundColor: INPUT_BG, borderWidth: 1, borderColor: BORDER,
    borderRadius: 12, padding: 14, fontSize: 14, color: '#1A1A2E', marginBottom: 0,
  },
  inputRow: {
    flexDirection: 'row', alignItems: 'center', borderWidth: 1, borderColor: BORDER,
    borderRadius: 12, backgroundColor: INPUT_BG, marginBottom: 4,
  },
  eyeBtn: { paddingHorizontal: 14 },
  button: { backgroundColor: PRIMARY, borderRadius: 30, padding: 16, alignItems: 'center' },
  buttonText: { color: '#fff', fontSize: 16, fontWeight: '700' },
});
