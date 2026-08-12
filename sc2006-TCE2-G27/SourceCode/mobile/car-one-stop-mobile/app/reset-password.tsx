import { BG, BORDER, INPUT_BG, PRIMARY, SUBTEXT } from '@/constants/theme';
import StatusModal from '@/components/ui/StatusModal';
import { resetPassword } from '@/services/authService';
import { Ionicons } from '@expo/vector-icons';
import { router, useLocalSearchParams } from 'expo-router';
import React, { useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  SafeAreaView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';

function PasswordRule({ met, text }: { met: boolean; text: string }) {
  return (
    <Text style={[styles.ruleText, { color: met ? '#27AE60' : '#E74C3C' }]}>
      {met ? '[OK] ' : '[X] '}
      {text}
    </Text>
  );
}

export default function ResetPassword() {
  const { email: emailParam, otp: otpParam } = useLocalSearchParams();
  const email = typeof emailParam === 'string' ? emailParam : '';
  const otpCode = typeof otpParam === 'string' ? otpParam : '';

  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showNew, setShowNew] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
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

  const pwRules = useMemo(() => ({
    length: newPassword.length >= 8 && newPassword.length <= 72,
    upper: /[A-Z]/.test(newPassword),
    lower: /[a-z]/.test(newPassword),
    digit: /[0-9]/.test(newPassword),
    special: /[!@#$%^&*()_+\-=[\]{};':"\\|,.<>/?]/.test(newPassword),
  }), [newPassword]);

  const isPasswordValid = Object.values(pwRules).every(Boolean);

  useEffect(() => {
    if (!email || !otpCode) {
      openPopup(
        'warning',
        'Verification Required',
        'Please verify your reset OTP first.',
        () => router.replace('/forgot-password')
      );
    }
  }, [email, otpCode]);

  const handleReset = async () => {
    if (!newPassword || !confirmPassword) {
      openPopup('error', 'Error', 'Please fill in all fields');
      return;
    }
    if (!isPasswordValid) {
      openPopup(
        'warning',
        'Invalid Password',
        'Password must be 8-72 chars with uppercase, lowercase, number and special character.'
      );
      return;
    }
    if (newPassword !== confirmPassword) {
      openPopup('error', 'Error', 'Passwords do not match');
      return;
    }

    setLoading(true);
    try {
      await resetPassword(email, otpCode, newPassword);
      openPopup('success', 'Success', 'Password reset successfully', () => router.replace('/login'));
    } catch (err: any) {
      const status = err?.response?.status;
      if (status === 422) {
        openPopup('warning', 'Invalid Code', 'The OTP code you entered is incorrect');
      } else if (status === 400) {
        openPopup('warning', 'Invalid or Expired Code', 'Please request a new reset code');
      } else {
        openPopup('error', 'Error', 'Something went wrong');
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

      <Text style={styles.title}>Reset Password</Text>

      <Text style={styles.label}>New Password</Text>
      <View style={styles.inputRow}>
        <TextInput
          style={[styles.input, { flex: 1 }]}
          placeholder="Enter your New Password"
          placeholderTextColor={SUBTEXT}
          value={newPassword}
          onChangeText={setNewPassword}
          secureTextEntry={!showNew}
          maxLength={72}
        />
        <TouchableOpacity style={styles.eyeBtn} onPress={() => setShowNew(!showNew)}>
          <Ionicons
            name={showNew ? 'eye-off-outline' : 'eye-outline'}
            size={18}
            color={SUBTEXT}
          />
        </TouchableOpacity>
      </View>

      {newPassword.length > 0 && (
        <View style={styles.requirementsBox}>
          <PasswordRule met={pwRules.length} text="8-72 characters" />
          <PasswordRule met={pwRules.upper} text="At least one uppercase letter" />
          <PasswordRule met={pwRules.lower} text="At least one lowercase letter" />
          <PasswordRule met={pwRules.digit} text="At least one digit" />
          <PasswordRule met={pwRules.special} text="At least one special character" />
        </View>
      )}

      <Text style={[styles.label, { marginTop: 14 }]}>Confirm New Password</Text>
      <View style={styles.inputRow}>
        <TextInput
          style={[styles.input, { flex: 1 }]}
          placeholder="Confirm your New Password"
          placeholderTextColor={SUBTEXT}
          value={confirmPassword}
          onChangeText={setConfirmPassword}
          secureTextEntry={!showConfirm}
          maxLength={72}
        />
        <TouchableOpacity style={styles.eyeBtn} onPress={() => setShowConfirm(!showConfirm)}>
          <Ionicons
            name={showConfirm ? 'eye-off-outline' : 'eye-outline'}
            size={18}
            color={SUBTEXT}
          />
        </TouchableOpacity>
      </View>

      <TouchableOpacity
        style={[styles.button, { marginTop: 24 }, loading && { opacity: 0.7 }]}
        onPress={handleReset}
        disabled={loading}
      >
        {loading ? <ActivityIndicator color="#fff" /> : <Text style={styles.buttonText}>Reset Password</Text>}
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
  label: { fontSize: 13, color: '#1A1A2E', fontWeight: '600', marginBottom: 6 },
  input: { backgroundColor: INPUT_BG, padding: 14, fontSize: 14, color: '#1A1A2E' },
  inputRow: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: BORDER,
    borderRadius: 12,
    backgroundColor: INPUT_BG,
  },
  requirementsBox: {
    marginTop: 10,
    borderWidth: 1,
    borderColor: '#E6EEF3',
    borderRadius: 10,
    backgroundColor: '#FFFFFF',
    padding: 10,
  },
  ruleText: {
    fontSize: 13,
    marginBottom: 4,
  },
  eyeBtn: { paddingHorizontal: 14 },
  button: { backgroundColor: PRIMARY, borderRadius: 30, padding: 16, alignItems: 'center' },
  buttonText: { color: '#fff', fontSize: 16, fontWeight: '700' },
});
