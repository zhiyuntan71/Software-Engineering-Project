import { BG, BORDER, INPUT_BG, PRIMARY, SUBTEXT, TEXT } from '@/constants/theme';
import StatusModal from '@/components/ui/StatusModal';
import { useUser } from '@/context/UserContext';
import { loginUser } from '@/services/authService';
import { Ionicons } from '@expo/vector-icons';
import { router } from 'expo-router';
import React, { useState } from 'react';
import { decode } from 'base-64';
import * as SecureStore from 'expo-secure-store';
import {
  ActivityIndicator,
  Image,
  KeyboardAvoidingView,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text, TextInput, TouchableOpacity,
  View,
} from 'react-native';

export default function LoginScreen() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPass, setShowPass] = useState(false);
  const [remember, setRemember] = useState(false);
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

  const { setUsername: setContextUsername, setCarType: setContextCarType,
          setCarModel: setContextCarModel, setChargingType: setContextChargingType } = useUser()

  // ── replace handleLogin ──
  const handleLogin = async () => {
    if (!username || !password) {
      openPopup('error', 'Error', 'Please fill in all fields')
      return
    }
    setLoading(true)
    try {
      const data = await loginUser(username, password)
      const role = await SecureStore.getItemAsync('userRole')
      if (role === 'ADMIN') {
        router.replace('/admin/dashboard')
      } else {
        setContextUsername(data.username)
        setContextCarType(data.carType)
        setContextCarModel(data.carModel)
        setContextChargingType(data.chargingType)
        router.replace('/(tabs)')
      }
    } catch (err: any) {
  const status = err?.response?.status
  if (status === 410) {
    openPopup('warning', 'Expired', 'Your verification expired. Please register again.', () => router.replace('/register'))
  } else if (status === 423) {
    await SecureStore.deleteItemAsync('userToken')
    await SecureStore.deleteItemAsync('userRole')
    openPopup('warning', 'Banned', 'Your account has been banned.')
  } else if (status === 403) {
    openPopup('warning', 'Not Verified', 'Please verify your email first.', () => router.replace('/validate-email'))
  } else if (status === 400) {
    openPopup('error', 'Error', err?.response?.data?.message ?? 'Invalid credentials')
  } else if (!err?.response) {
    openPopup('error', 'Network Error', 'Could not reach server. Please check your connection.')
  } else {
    openPopup('error', 'Error', err?.response?.data?.message ?? 'Login failed. Please try again.')
  }
} finally {
  setLoading(false)
}
  };

  return (
    <SafeAreaView style={styles.container}>
      <KeyboardAvoidingView style={styles.inner} behavior="height">
        <ScrollView
          contentContainerStyle={styles.scroll}
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}
        >
          {/* Logo */}
          <Image
            source={require('@/assets/images/logo.png')}
            style={styles.logo}
            resizeMode="contain"
          />

          {/* Title */}
          <Text style={styles.title}>
            Welcome to Car<Text style={{ color: PRIMARY }}>One</Text>Stop
          </Text>

          {/* Tab Switcher */}
          <View style={styles.tabBar}>
            <TouchableOpacity style={[styles.tabBtn, styles.tabBtnActive]}>
              <Text style={[styles.tabText, styles.tabTextActive]}>Login</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.tabBtn}
              onPress={() => router.replace('/register')}
            >
              <Text style={styles.tabText}>Register</Text>
            </TouchableOpacity>
          </View>

          <Text style={styles.subtitle}>Login to your existing account.</Text>

          <Text style={styles.label}>Email</Text>
          <TextInput
            style={styles.input}
            placeholder="Enter your email address"
            placeholderTextColor={SUBTEXT}
            value={username}
            onChangeText={setUsername}
            autoCapitalize="none"
          />

          <Text style={styles.label}>Password</Text>
          <View style={styles.inputRow}>
            <TextInput
              style={styles.inputFlex}
              placeholder="Enter your Password"
              placeholderTextColor={SUBTEXT}
              value={password}
              onChangeText={setPassword}
              secureTextEntry={!showPass}
            />
            <TouchableOpacity style={styles.eyeBtn} onPress={() => setShowPass(!showPass)}>
              <Ionicons
                name={showPass ? 'eye-off-outline' : 'eye-outline'}
                size={18}
                color={SUBTEXT}
              />
            </TouchableOpacity>
          </View>

          <View style={styles.row}>
            <TouchableOpacity style={styles.checkRow} onPress={() => setRemember(!remember)}>
              <View style={[styles.checkbox, remember && styles.checkboxActive]} />
              <Text style={styles.rememberText}>Remember me</Text>
            </TouchableOpacity>
            <TouchableOpacity onPress={() => router.push('/forgot-password')}>
              <Text style={[styles.rememberText, { color: PRIMARY }]}>Forgot Password?</Text>
            </TouchableOpacity>
          </View>

          <TouchableOpacity style={styles.button} onPress={handleLogin} disabled={loading}>
            {loading
              ? <ActivityIndicator color="#fff" />
              : <Text style={styles.buttonText}>Login</Text>
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
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: BG },
  inner: { flex: 1 },
  scroll: { flexGrow: 1, justifyContent: 'center', paddingHorizontal: 28, paddingVertical: 52 },
  logo: { width: 160, height: 160, alignSelf: 'center', marginBottom: 8 },
  title: { fontSize: 26, fontWeight: 'bold', textAlign: 'center', color: TEXT, marginBottom: 24 },
  tabBar: { flexDirection: 'row', backgroundColor: '#C7F9FF', borderRadius: 30, padding: 4, marginBottom: 24 },
  tabBtn: { flex: 1, paddingVertical: 10, borderRadius: 26, alignItems: 'center' },
  tabBtnActive: { backgroundColor: PRIMARY },
  tabText: { fontSize: 15, color: SUBTEXT, fontWeight: '600' },
  tabTextActive: { color: '#fff' },
  subtitle: { fontSize: 14, color: SUBTEXT, marginBottom: 20 },
  label: { fontSize: 13, color: TEXT, fontWeight: '600', marginBottom: 6 },
  input: { backgroundColor: INPUT_BG, borderWidth: 1, borderColor: BORDER, borderRadius: 30, padding: 14, fontSize: 14, color: TEXT, marginBottom: 14 },
  inputRow: { flexDirection: 'row', alignItems: 'center', borderWidth: 1, borderColor: BORDER, borderRadius: 30, backgroundColor: INPUT_BG, marginBottom: 14 },
  inputFlex: { flex: 1, padding: 14, fontSize: 14, color: TEXT },
  eyeBtn: { paddingHorizontal: 14 },
  row: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 },
  checkRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  checkbox: { width: 16, height: 16, borderWidth: 1.5, borderColor: SUBTEXT, borderRadius: 4 },
  checkboxActive: { backgroundColor: PRIMARY, borderColor: PRIMARY },
  rememberText: { fontSize: 13, color: SUBTEXT },
  button: { backgroundColor: PRIMARY, borderRadius: 30, padding: 16, alignItems: 'center' },
  buttonText: { color: '#fff', fontSize: 16, fontWeight: '700' },
});

