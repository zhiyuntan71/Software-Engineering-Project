import { CAR_MODELS, CHARGING_TYPES } from '@/constants/carData';
import { BG, BORDER, INPUT_BG, PRIMARY, SUBTEXT, TEXT } from '@/constants/theme';
import StatusModal from '@/components/ui/StatusModal';
import { useUser } from '@/context/UserContext';
import { CarType, ChargingType, registerUser } from '@/services/authService';
import { Ionicons } from '@expo/vector-icons';
import { router } from 'expo-router';
import React, { useState } from 'react';
import {
  ActivityIndicator,
  FlatList,
  KeyboardAvoidingView,
  Modal,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text, TextInput, TouchableOpacity,
  View,
} from 'react-native';


// ─── Password Rule Component ──────────────────────────────
function PasswordRule({ met, text }: { met: boolean; text: string }) {
  return (
    <View style={{ flexDirection: 'row', alignItems: 'center', marginBottom: 4 }}>
      <Text style={{ color: met ? '#27AE60' : '#E74C3C', marginRight: 8, fontSize: 14 }}>
        {met ? '✓' : '✗'}
      </Text>
      <Text style={{ color: met ? '#27AE60' : '#E74C3C', fontSize: 13 }}>{text}</Text>
    </View>
  );
}


// ─── Dropdown Component ───────────────────────────────────
function DropdownField({
  label, value, onSelect, options, disabled = false,
}: {
  label: string;
  value: string;
  onSelect: (val: string) => void;
  options: string[];
  disabled?: boolean;
}) {
  const [visible, setVisible] = useState(false);

  return (
    <View style={{ marginBottom: 14 }}>
      <Text style={styles.label}>{label}</Text>
      <TouchableOpacity
        style={[styles.dropdown, disabled && { opacity: 0.4 }]}
        onPress={() => !disabled && setVisible(true)}
      >
        <Text style={{ color: value ? TEXT : SUBTEXT, fontSize: 14 }}>
          {value || (disabled ? 'Select Car Type first' : `Select ${label}`)}
        </Text>
        <Text style={{ color: SUBTEXT }}>▾</Text>
      </TouchableOpacity>

      <Modal visible={visible} transparent animationType="fade" onRequestClose={() => setVisible(false)}>
        <TouchableOpacity style={styles.modalOverlay} activeOpacity={1} onPress={() => setVisible(false)}>
          <View style={styles.modalBox}>
            <Text style={styles.modalTitle}>{label}</Text>
            <FlatList
              data={options}
              keyExtractor={(item) => item}
              renderItem={({ item }) => (
                <TouchableOpacity
                  style={[styles.modalOption, value === item && styles.modalOptionActive]}
                  onPress={() => { onSelect(item); setVisible(false); }}
                >
                  <Text style={[
                    styles.modalOptionText,
                    value === item && { color: PRIMARY, fontWeight: '700' },
                  ]}>
                    {item}
                  </Text>
                </TouchableOpacity>
              )}
            />
          </View>
        </TouchableOpacity>
      </Modal>
    </View>
  );
}


// ─── Helpers ──────────────────────────────────────────────

// Makes enum names human-readable in the UI
// e.g. "THREE_PIN_AC" → "Three Pin Ac"
function formatEnumLabel(val: string) {
  return val.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase());
}


// ─── Main Register Screen ─────────────────────────────────
export default function RegisterScreen() {
  const {
    setUsername: setContextUsername,
    setCarType: setContextCarType,
    setCarModel: setContextCarModel,
    setChargingType: setContextChargingType,
  } = useUser();

  const [step, setStep]       = useState(1);
  const [loading, setLoading] = useState(false);

  // Step 1 — matches @Email @NotBlank, @Size constraints
  const [email, setEmail]       = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPass, setShowPass] = useState(false);

  // Step 2 — matches Java enum types
  const [carType, setCarType]           = useState<CarType | ''>('');
  const [carModel, setCarModel]         = useState('');
  const [chargingType, setChargingType] = useState<ChargingType>('NOT_APPLICABLE');
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

  // Password rules (mirrors @Size(min=8, max=72) + strong password UX)
  const pwRules = {
    length:  password.length >= 8 && password.length <= 72,
    upper:   /[A-Z]/.test(password),
    lower:   /[a-z]/.test(password),
    digit:   /[0-9]/.test(password),
    special: /[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?]/.test(password),
  };
  const isPasswordValid = Object.values(pwRules).every(Boolean);

  // Frontend validation mirrors @Size(min=3, max=50) on username
  const isUsernameValid = username.length >= 3 && username.length <= 50;

  // ── Step 1: Validate ───────────────────────────────────
  const handleBack = () => {
    if (step === 2) {
      setStep(1);
      return;
    }

    if (router.canGoBack()) {
      router.back();
      return;
    }

    router.replace('/login');
  };

  const handleNextStep = () => {
    if (!email || !username || !password) {
      openPopup('error', 'Error', 'Please fill in all fields');
      return;
    }
    if (!isUsernameValid) {
      openPopup('error', 'Error', 'Username must be between 3 and 50 characters');
      return;
    }
    if (!isPasswordValid) {
      openPopup('error', 'Error', 'Password does not meet all requirements');
      return;
    }
    setStep(2);
  };

  // ── Step 2: POST to /auth/register ────────────────────
  const handleRegister = async () => {
    if (!carType || !carModel) {
      openPopup('error', 'Error', 'Please select your car type and model');
      return;
    }
    if (carType === 'ELECTRIC' && chargingType === 'NOT_APPLICABLE') {
      openPopup('error', 'Error', 'Please select a charging type for your EV');
      return;
    }

    setLoading(true);
    try {
      const data = await registerUser({
        email,
        username,
        password,
        carType,                // sent as "ELECTRIC", "PETROL", etc.
        carModel,               // sent as Java CarModel enum constant
        chargingType,           // sent as "CCS2", "TYPE2_AC", etc.
      });
      setContextUsername(data.username);
      setContextCarType(data.carType);
      setContextCarModel(data.carModel);
      setContextChargingType(data.chargingType);

      router.push('/validate-email');

    } catch (error: any) {
      const status   = error?.response?.status;
      const data     = error?.response?.data;

      if (status === 400) {
        // Spring @Valid returns field-level errors
        // Typical shape: { errors: [{ field: "email", message: "..." }] }
        const fieldErrors: string = data?.errors
          ? data.errors.map((e: any) => `• ${e.field}: ${e.message}`).join('\n')
          : data?.message ?? 'Invalid input. Please check your details.';
        openPopup('error', 'Validation Error', fieldErrors);

      } else if (status === 409) {
        openPopup('warning', 'Account Exists', data?.message ?? 'Email or username is already taken.');

      } else if (status === 410) {
          openPopup('warning', 'Expired', 'Your verification expired. Please register again.', () => router.replace('/register'))
      }
        else if (status === 403) {
          openPopup('warning', 'Already Registered', 'Please verify your email first.', () => router.replace('/validate-email'))
      } 
        else if (!error?.response) {
        openPopup('error', 'Network Error', 'Could not reach server. Please check your connection.');
      }
        else {
        openPopup('error', 'Registration Failed', data?.message ?? 'Something went wrong. Try again.');
      }
    } finally {
      setLoading(false);
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
          {step === 2 && (
            <TouchableOpacity style={styles.topRightBackButton} onPress={handleBack}>
              <Ionicons name="chevron-back" size={20} color={PRIMARY} />
            </TouchableOpacity>
          )}

          {/* Title */}
          <Text style={styles.title}>
            Welcome to Car<Text style={{ color: PRIMARY }}>One</Text>Stop
          </Text>

          {/* Tab Switcher */}
          <View style={styles.tabBar}>
            <TouchableOpacity style={styles.tabBtn} onPress={() => router.replace('/login')}>
              <Text style={styles.tabText}>Login</Text>
            </TouchableOpacity>
            <TouchableOpacity style={[styles.tabBtn, styles.tabBtnActive]}>
              <Text style={[styles.tabText, styles.tabTextActive]}>Register</Text>
            </TouchableOpacity>
          </View>

          {step === 1 ? (
            // ── Step 1: Account Details ──────────────────
            <View>
              <Text style={styles.subtitle}>Register and create an account with us.</Text>

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

              <Text style={styles.label}>Username</Text>
              <TextInput
                style={styles.input}
                placeholder="3–50 characters"
                placeholderTextColor={SUBTEXT}
                value={username}
                onChangeText={setUsername}
                autoCapitalize="none"
                maxLength={50}
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
                  maxLength={72}
                />
                <TouchableOpacity style={styles.eyeBtn} onPress={() => setShowPass(!showPass)}>
                  <Ionicons
                    name={showPass ? 'eye-off-outline' : 'eye-outline'}
                    size={18}
                    color={SUBTEXT}
                  />
                </TouchableOpacity>
              </View>

              {password.length > 0 && (
                <View style={styles.requirementsBox}>
                  <PasswordRule met={pwRules.length}  text="8–72 characters" />
                  <PasswordRule met={pwRules.upper}   text="At least one uppercase letter" />
                  <PasswordRule met={pwRules.lower}   text="At least one lowercase letter" />
                  <PasswordRule met={pwRules.digit}   text="At least one digit" />
                  <PasswordRule met={pwRules.special} text="At least one special character" />
                </View>
              )}

              <TouchableOpacity style={[styles.button, { marginTop: 24 }]} onPress={handleNextStep}>
                <Text style={styles.buttonText}>Next</Text>
              </TouchableOpacity>
            </View>

          ) : (
            // ── Step 2: Car Details ──────────────────────
            <View>
              <Text style={styles.subtitle}>Tell us what you drive.</Text>

              {/* Car Type — sends enum name e.g. "ELECTRIC" */}
              <DropdownField
                label="Car Type"
                value={carType ? formatEnumLabel(carType) : ''}
                onSelect={(val) => {
                  setCarType(val as CarType);
                  setCarModel('');
                  setChargingType(val === 'ELECTRIC' ? '' as any : 'NOT_APPLICABLE');
                }}
                options={Object.keys(CAR_MODELS)}       // ["PETROL","DIESEL","HYBRID","ELECTRIC"]
              />

              {/* Car Model — sends enum name */}
              <DropdownField
                label="Car Model"
                value={carModel ? formatEnumLabel(carModel) : ''}
                onSelect={setCarModel}
                options={carType ? (CAR_MODELS[carType] ?? []) : []}
                disabled={!carType}
              />

              {/* Charging Type — only enabled for ELECTRIC */}
              <DropdownField
                label="Charging Type (EV)"
                value={chargingType ? formatEnumLabel(chargingType) : ''}
                onSelect={(val) => setChargingType(val as ChargingType)}
                options={CHARGING_TYPES}
                disabled={carType !== 'ELECTRIC'}
              />

              <TouchableOpacity
                style={[styles.button, { marginTop: 24 }, loading && { opacity: 0.7 }]}
                onPress={handleRegister}
                disabled={loading}
              >
                {loading
                  ? <ActivityIndicator color="#fff" />
                  : <Text style={styles.buttonText}>Register</Text>
                }
              </TouchableOpacity>
            </View>
          )}
        </ScrollView>

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
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}


// ─── Styles ───────────────────────────────────────────────
const styles = StyleSheet.create({
  container:         { flex: 1, backgroundColor: BG },
  inner:             { flex: 1 },
  scroll:            { flexGrow: 1, justifyContent: 'center', paddingHorizontal: 28, paddingVertical: 40 },
  title:             { fontSize: 26, fontWeight: 'bold', textAlign: 'center', color: TEXT, marginBottom: 24 },
  tabBar:            { flexDirection: 'row', backgroundColor: '#C7F9FF', borderRadius: 30, padding: 4, marginBottom: 24 },
  tabBtn:            { flex: 1, paddingVertical: 10, borderRadius: 26, alignItems: 'center' },
  tabBtnActive:      { backgroundColor: PRIMARY },
  tabText:           { fontSize: 15, color: SUBTEXT, fontWeight: '600' },
  tabTextActive:     { color: '#fff' },
  subtitle:          { fontSize: 14, color: SUBTEXT, marginBottom: 20 },
  label:             { fontSize: 13, color: TEXT, fontWeight: '600', marginBottom: 6 },
  input:             { backgroundColor: INPUT_BG, borderWidth: 1, borderColor: BORDER, borderRadius: 30, padding: 14, fontSize: 14, color: TEXT, marginBottom: 14 },
  inputRow:          { flexDirection: 'row', alignItems: 'center', borderWidth: 1, borderColor: BORDER, borderRadius: 30, backgroundColor: INPUT_BG, marginBottom: 14 },
  inputFlex:         { flex: 1, padding: 14, fontSize: 14, color: TEXT },
  eyeBtn:            { paddingHorizontal: 14 },
  button:            { backgroundColor: PRIMARY, borderRadius: 30, padding: 16, alignItems: 'center' },
  buttonText:        { color: '#fff', fontSize: 16, fontWeight: '700' },
  dropdown:          { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', backgroundColor: INPUT_BG, borderWidth: 1, borderColor: BORDER, borderRadius: 30, padding: 14, marginBottom: 14 },
  requirementsBox:   { backgroundColor: '#F5F9FA', borderRadius: 12, padding: 12, marginBottom: 14 },
  modalOverlay:      { flex: 1, backgroundColor: 'rgba(0,0,0,0.4)', justifyContent: 'center', paddingHorizontal: 28 },
  modalBox:          { backgroundColor: BG, borderRadius: 16, paddingVertical: 8, maxHeight: 320 },
  modalTitle:        { fontSize: 15, fontWeight: '700', color: TEXT, paddingHorizontal: 20, paddingVertical: 14, borderBottomWidth: 1, borderBottomColor: BORDER },
  modalOption:       { paddingHorizontal: 20, paddingVertical: 14, borderBottomWidth: 1, borderBottomColor: '#F0F0F0' },
  modalOptionActive: { backgroundColor: '#EAF5F7' },
  modalOptionText:   { fontSize: 14, color: TEXT },
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
});
