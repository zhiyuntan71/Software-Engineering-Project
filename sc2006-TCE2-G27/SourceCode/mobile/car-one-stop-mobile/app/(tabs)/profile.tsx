import { CAR_MODELS, CHARGING_TYPES } from '@/constants/carData';
import { BG, BORDER, CARD_BG, INPUT_BG, PRIMARY, SUBTEXT, TEXT } from '@/constants/theme';
import StatusModal, { StatusModalDetail } from '@/components/ui/StatusModal';
import { SavedCard, useUser } from '@/context/UserContext';
import { CarType, ChargingType, logoutUser } from '@/services/authService';
import { getProfile, updateProfile } from '@/services/profileService';
import { getWalletBalance } from '@/services/walletService';
import { Ionicons } from '@expo/vector-icons';
import { router } from 'expo-router';
import React, { useEffect, useState } from 'react';
import {
  FlatList,
  Linking,
  Modal,
  Platform,
  SafeAreaView, ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';

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
    <View>
      <TouchableOpacity
        style={[styles.dropdown, disabled && { opacity: 0.4 }]}
        onPress={() => !disabled && setVisible(true)}
      >
        <Text style={{ color: value ? TEXT : SUBTEXT, fontSize: 14, flex: 1 }}>
          {value || (disabled ? 'Select Car Type first' : `Select ${label}`)}
        </Text>
        <Ionicons name="chevron-down" size={16} color={SUBTEXT} />
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
                  <Text style={[styles.modalOptionText, value === item && { color: PRIMARY, fontWeight: '700' }]}>
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

// ─── Main Profile Screen ──────────────────────────────────
export default function Profile() {
  const {
    balance,
    setBalance,
    username, setUsername,
    carType, setCarType,
    carModel, setCarModel,
    chargingType, setChargingType,
    savedCards, setSavedCards,
  } = useUser();

  const [editingUsername, setEditingUsername] = useState(false);
  // === PROFILE SAVE STAGING (LOCAL DRAFT) ===
  const [draftUsername, setDraftUsername] = useState('');
  const [draftCarType, setDraftCarType] = useState('');
  const [draftCarModel, setDraftCarModel] = useState('');
  const [draftChargingType, setDraftChargingType] = useState('NOT_APPLICABLE');
  const [savingProfile, setSavingProfile] = useState(false);
  const [showSaveConfirmModal, setShowSaveConfirmModal] = useState(false);
  const [showSaveResultModal, setShowSaveResultModal] = useState(false);
  const [saveResultVariant, setSaveResultVariant] = useState<'success' | 'error'>('success');
  const [saveResultTitle, setSaveResultTitle] = useState('');
  const [saveResultMessage, setSaveResultMessage] = useState('');
  // === POPUP STANDARDIZATION (StatusModal) ===
  const [pendingRemoveCardId, setPendingRemoveCardId] = useState<string | null>(null);
  const [showLogoutConfirmModal, setShowLogoutConfirmModal] = useState(false);

  useEffect(() => {
    const loadProfile = async () => {
      try {
        // === PROFILE + WALLET INITIAL LOAD ===
        const [profile, wallet] = await Promise.all([
          getProfile(),
          getWalletBalance(),
        ]);
        const loadedUsername = profile.username ?? '';
        const loadedCarType = profile.carType ?? '';
        const loadedCarModel = profile.carModel ?? '';
        const loadedChargingType = profile.chargingType ?? 'NOT_APPLICABLE';
        const loadedBalance = Number(wallet.balance ?? 0);

        setUsername(loadedUsername);
        setCarType(loadedCarType);
        setCarModel(loadedCarModel);
        setChargingType(loadedChargingType);
        setBalance(loadedBalance);

        setDraftUsername(loadedUsername);
        setDraftCarType(loadedCarType);
        setDraftCarModel(loadedCarModel);
        setDraftChargingType(loadedChargingType);
      } catch (err) {
        console.log('Failed to load profile:', err);
      }
    };
    loadProfile();
  }, [setUsername, setCarType, setCarModel, setChargingType, setBalance]);

  const handleDeleteCard = (id: string) => {
    setPendingRemoveCardId(id);
  };

  const handleLogOut = () => {
    setShowLogoutConfirmModal(true);
  };

  // === PROFILE SAVE STAGING (LOCAL DRAFT) ===
  const changedDetails: StatusModalDetail[] = [
    draftUsername !== username ? { label: 'Username', value: `${username || '-'} -> ${draftUsername || '-'}`, fullWidth: true } : null,
    draftCarType !== carType ? { label: 'Car Type', value: `${carType || '-'} -> ${draftCarType || '-'}`, fullWidth: true } : null,
    draftCarModel !== carModel ? { label: 'Car Model', value: `${carModel || '-'} -> ${draftCarModel || '-'}`, fullWidth: true } : null,
    draftChargingType !== chargingType ? { label: 'Charge Type', value: `${chargingType || '-'} -> ${draftChargingType || '-'}`, fullWidth: true } : null,
  ].filter((d): d is StatusModalDetail => d !== null);

  const hasUnsavedChanges = changedDetails.length > 0;

  const handleSaveProfileChanges = async () => {
    if (!hasUnsavedChanges) return;

    const payload: {
      username?: string;
      carType?: CarType;
      carModel?: string;
      chargingType?: ChargingType;
    } = {};

    if (draftUsername !== username) payload.username = draftUsername;
    if (draftCarType !== carType && draftCarType) payload.carType = draftCarType as CarType;
    if (draftCarModel !== carModel && draftCarModel) payload.carModel = draftCarModel;
    if (draftChargingType !== chargingType && draftChargingType) payload.chargingType = draftChargingType as ChargingType;

    setSavingProfile(true);
    try {
      const updated = await updateProfile(payload);
      const updatedUsername = updated.username ?? '';
      const updatedCarType = updated.carType ?? '';
      const updatedCarModel = updated.carModel ?? '';
      const updatedChargingType = updated.chargingType ?? 'NOT_APPLICABLE';

      setUsername(updatedUsername);
      setCarType(updatedCarType);
      setCarModel(updatedCarModel);
      setChargingType(updatedChargingType);

      setDraftUsername(updatedUsername);
      setDraftCarType(updatedCarType);
      setDraftCarModel(updatedCarModel);
      setDraftChargingType(updatedChargingType);

      setShowSaveConfirmModal(false);
      setSaveResultVariant('success');
      setSaveResultTitle('Profile Updated');
      setSaveResultMessage('Your profile changes have been saved.');
      setShowSaveResultModal(true);
    } catch {
      setSaveResultVariant('error');
      setSaveResultTitle('Error');
      setSaveResultMessage('Failed to save profile changes');
      setShowSaveResultModal(true);
    } finally {
      setSavingProfile(false);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>

        {/* Header */}
        <View style={styles.header}>
          <TouchableOpacity style={styles.backBtn} onPress={() => router.back()}>
            <Ionicons name="arrow-back-outline" size={20} color={PRIMARY} />
          </TouchableOpacity>
          <Text style={styles.headerTitle}>Profile</Text>
          <View style={{ width: 36 }} />
        </View>

        {/* Avatar + Username */}
        <View style={styles.card}>
          <View style={styles.avatarRow}>
            <View style={styles.avatar}>
              <Ionicons name="person-circle-outline" size={56} color={PRIMARY} />
            </View>
            <View style={styles.fieldBlock}>
              <Text style={styles.fieldLabel}>Username</Text>
              <View style={styles.inputRow}>
                {editingUsername ? (
                  <TextInput
                    style={styles.inlineInput}
                    value={draftUsername}
                    onChangeText={setDraftUsername}
                    autoFocus
                    onBlur={() => {
                      setEditingUsername(false)
                    }}
                    onSubmitEditing={() => {
                      setEditingUsername(false)
                    }}
                  />
                ) : (
                  <Text style={styles.fieldValue}>{draftUsername || 'Set username'}</Text>
                )}
                <TouchableOpacity onPress={() => setEditingUsername(!editingUsername)}>
                  <Ionicons
                    name={editingUsername ? 'checkmark-outline' : 'create-outline'}
                    size={18} color={PRIMARY} style={styles.editIcon}
                  />
                </TouchableOpacity>
              </View>
            </View>
          </View>
        </View>

        {/* Wallet + Saved Cards + Car Info */}
        <View style={styles.card}>

          {/* Wallet Balance */}
          <View style={styles.walletRow}>
            <Text style={styles.walletLabel}>Wallet</Text>
            <View style={styles.walletBadge}>
              <Text style={styles.walletAmount}>${balance.toFixed(2)}</Text>
            </View>
          </View>

          <View style={styles.divider} />

          {/* Saved Cards */}
          {savedCards.length > 0 && (
            <View style={{ marginBottom: 8 }}>
              <Text style={[styles.sectionLabel, { marginTop: 12, marginBottom: 8 }]}>Saved Cards</Text>
              {savedCards.map((c: SavedCard) => (
                <View key={c.id} style={styles.savedCardRow}>
                  <Ionicons name="card-outline" size={20} color={PRIMARY} style={{ marginRight: 10 }} />
                  <View style={{ flex: 1 }}>
                    <Text style={styles.savedCardNumber}>•••• •••• •••• {c.cardNumber}</Text>
                    <Text style={styles.savedCardSub}>Expires {c.expiry}  {c.country}</Text>
                  </View>
                  <TouchableOpacity onPress={() => handleDeleteCard(c.id)}>
                    <Ionicons name="trash-outline" size={18} color="#E74C3C" />
                  </TouchableOpacity>
                </View>
              ))}
              <View style={styles.divider} />
            </View>
          )}

          {/* Car Icon */}
          <Ionicons name="car-outline" size={28} color={TEXT} style={{ marginBottom: 12, marginTop: 8 }} />

          {/* Car Type */}
          <View style={styles.detailRow}>
            <Text style={styles.detailLabel}>Car Type:</Text>
            <View style={{ flex: 1 }}>
              <DropdownField
                label="Car Type"
                value={draftCarType}
                onSelect={(val) => {
                  setDraftCarType(val as CarType);
                  setDraftCarModel('');
                  const newChargingType = val === 'ELECTRIC' ? '' : 'NOT_APPLICABLE';
                  setDraftChargingType(newChargingType);
                }}
                options={Object.keys(CAR_MODELS)}
              />
            </View>
          </View>

          <View style={styles.divider} />

          {/* Car Model */}
          <View style={styles.detailRow}>
            <Text style={styles.detailLabel}>Car Model:</Text>
            <View style={{ flex: 1 }}>
              <DropdownField
                label="Car Model"
                value={draftCarModel}
                onSelect={(val) => {
                  setDraftCarModel(val);
                }}
                options={draftCarType ? (CAR_MODELS[draftCarType as CarType] ?? []) : []}
                disabled={!draftCarType}
              />
            </View>
          </View>

          <View style={styles.divider} />

          {/* Charging Type */}
          <View style={styles.detailRow}>
            <Text style={styles.detailLabel}>Charge Type:</Text>
            <View style={{ flex: 1 }}>
              <DropdownField
                label="Charging Type"
                value={draftChargingType}
                onSelect={(val) => {
                  setDraftChargingType(val as ChargingType);
                }}
                options={CHARGING_TYPES}
                disabled={draftCarType !== 'ELECTRIC'}
              />
            </View>
          </View>
        </View>

        {/* === PROFILE SAVE STAGING (LOCAL DRAFT) === */}
        <TouchableOpacity
          style={[styles.saveChangesBtn, (!hasUnsavedChanges || savingProfile) && styles.saveChangesBtnDisabled]}
          onPress={() => setShowSaveConfirmModal(true)}
          disabled={!hasUnsavedChanges || savingProfile}
        >
          <Text style={styles.saveChangesText}>
            {savingProfile ? 'Saving...' : 'Save Changes'}
          </Text>
        </TouchableOpacity>

        {/* Contact Us */}
        <View style={styles.contactCard}>
          <Text style={styles.contactTitle}>Contact Us</Text>
          <Text style={styles.contactText}>CarOneStop@gmail.com</Text>

          <View style={styles.socialRow}>
            <TouchableOpacity style={styles.socialBtn} onPress={() => Linking.openURL('https://facebook.com')}>
              <Ionicons name="logo-facebook" size={20} color="#fff" />
            </TouchableOpacity>
            <TouchableOpacity style={styles.socialBtn} onPress={() => Linking.openURL('https://twitter.com')}>
              <Ionicons name="logo-twitter" size={20} color="#fff" />
            </TouchableOpacity>
            <TouchableOpacity style={styles.socialBtn} onPress={() => Linking.openURL('https://t.me')}>
              <Ionicons name="paper-plane-outline" size={20} color="#fff" />
            </TouchableOpacity>
            <TouchableOpacity style={styles.socialBtn} onPress={() => Linking.openURL('https:github.com')}>
              <Ionicons name="logo-github" size={20} color="#fff" />
            </TouchableOpacity>
            <TouchableOpacity style={styles.socialBtn} onPress={() => Linking.openURL('https://github.com/softwarelab3/SC2006-TCE2-G27')}>
              <Ionicons name="logo-github" size={20} color="#fff" />
            </TouchableOpacity>
          </View>
        </View>

        {/* Log Out */}
        <TouchableOpacity style={styles.logoutBtn} onPress={handleLogOut}>
          <Text style={styles.logoutText}>Log Out</Text>
        </TouchableOpacity>

        {/* === PROFILE SAVE CONFIRMATION (StatusModal) === */}
        <StatusModal
          visible={showSaveConfirmModal}
          variant="confirm"
          title="Confirm Profile Changes"
          message="Save these changes to your profile?"
          details={changedDetails}
          primaryLabel={savingProfile ? 'Saving...' : 'Confirm Save'}
          onPrimary={handleSaveProfileChanges}
          primaryDisabled={savingProfile}
          secondaryLabel="Cancel"
          onSecondary={() => setShowSaveConfirmModal(false)}
          onRequestClose={() => setShowSaveConfirmModal(false)}
        />

        <StatusModal
          visible={showSaveResultModal}
          variant={saveResultVariant}
          title={saveResultTitle}
          message={saveResultMessage}
          primaryLabel="OK"
          onPrimary={() => setShowSaveResultModal(false)}
          onRequestClose={() => setShowSaveResultModal(false)}
        />

        {/* === POPUP STANDARDIZATION (StatusModal) === */}
        <StatusModal
          visible={pendingRemoveCardId !== null}
          variant="warning"
          title="Remove Card"
          message="Remove this saved card?"
          primaryLabel="Remove"
          onPrimary={() => {
            const id = pendingRemoveCardId;
            setPendingRemoveCardId(null);
            if (!id) return;
            setSavedCards(savedCards.filter((c) => c.id !== id));
          }}
          secondaryLabel="Cancel"
          onSecondary={() => setPendingRemoveCardId(null)}
          onRequestClose={() => setPendingRemoveCardId(null)}
        />

        <StatusModal
          visible={showLogoutConfirmModal}
          variant="confirm"
          title="Log Out"
          message="Are you sure you want to log out?"
          primaryLabel="Log Out"
          onPrimary={async () => {
            setShowLogoutConfirmModal(false);
            setUsername('');
            setCarType('');
            setCarModel('');
            setChargingType('NOT_APPLICABLE');
            await logoutUser();
            router.replace('/login');
          }}
          secondaryLabel="Cancel"
          onSecondary={() => setShowLogoutConfirmModal(false)}
          onRequestClose={() => setShowLogoutConfirmModal(false)}
        />

      </ScrollView>
    </SafeAreaView>
  );
}

// ─── Styles ───────────────────────────────────────────────
const styles = StyleSheet.create({
  container: {
    flex: 1, backgroundColor: BG,
    paddingTop: Platform.OS === 'android' ? (StatusBar.currentHeight ?? 24) + 10 : 50,
  },
  scroll: { padding: 20, paddingBottom: 40 },

  // Header
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 24 },
  backBtn: {
    width: 36, height: 36, borderRadius: 18,
    backgroundColor: INPUT_BG, borderWidth: 1, borderColor: PRIMARY,
    alignItems: 'center', justifyContent: 'center',
  },
  headerTitle: { fontSize: 18, fontWeight: '700', color: TEXT },

  // Cards
  card: { backgroundColor: CARD_BG, borderRadius: 16, padding: 16, marginBottom: 16 },

  // Avatar + Username
  avatarRow: { flexDirection: 'row', alignItems: 'center' },
  avatar: { marginRight: 16 },
  fieldBlock: { flex: 1 },
  fieldLabel: { fontSize: 13, fontWeight: '700', color: TEXT, marginBottom: 6 },
  fieldValue: { fontSize: 15, color: TEXT, flex: 1 },
  inputRow: { flexDirection: 'row', alignItems: 'center', flex: 1 },
  inlineInput: {
    flex: 1, fontSize: 15, color: TEXT,
    borderBottomWidth: 1, borderBottomColor: PRIMARY, paddingVertical: 2,
  },
  editIcon: { marginLeft: 8 },

  // Wallet
  walletRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 },
  walletLabel: { fontSize: 16, fontWeight: '700', color: TEXT },
  walletBadge: { backgroundColor: PRIMARY, borderRadius: 20, paddingHorizontal: 16, paddingVertical: 6 },
  walletAmount: { color: '#fff', fontWeight: '700', fontSize: 15 },
  divider: { height: 1, backgroundColor: BORDER, marginVertical: 8 },
  sectionLabel: { fontSize: 14, fontWeight: '700', color: TEXT },

  // Saved Cards
  savedCardRow: {
    flexDirection: 'row', alignItems: 'center',
    borderWidth: 1, borderColor: '#E0E0E0', borderRadius: 12,
    padding: 12, marginBottom: 8, backgroundColor: BG,
  },
  savedCardNumber: { fontSize: 14, fontWeight: '600', color: TEXT },
  savedCardSub: { fontSize: 12, color: SUBTEXT, marginTop: 2 },

  // Car Details
  detailRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: 4 },
  detailLabel: { fontSize: 14, color: SUBTEXT, width: 100 },
  dropdown: { flexDirection: 'row', alignItems: 'center', paddingVertical: 10, paddingHorizontal: 4 },
  modalOverlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.4)', justifyContent: 'center', paddingHorizontal: 28 },
  modalBox: { backgroundColor: BG, borderRadius: 16, paddingVertical: 8, maxHeight: 320 },
  modalTitle: { fontSize: 15, fontWeight: '700', color: TEXT, paddingHorizontal: 20, paddingVertical: 14, borderBottomWidth: 1, borderBottomColor: '#eee' },
  modalOption: { paddingHorizontal: 20, paddingVertical: 14, borderBottomWidth: 1, borderBottomColor: '#F0F0F0' },
  modalOptionActive: { backgroundColor: '#EAF5F7' },
  modalOptionText: { fontSize: 14, color: TEXT },

  // Contact
  contactCard: { backgroundColor: CARD_BG, borderRadius: 16, padding: 20, marginBottom: 16, alignItems: 'center' },
  contactTitle: { fontSize: 17, fontWeight: '700', color: TEXT, marginBottom: 8 },
  contactText: { fontSize: 13, color: SUBTEXT, marginBottom: 4, textAlign: 'center' },
  socialRow: { flexDirection: 'row', gap: 12, marginTop: 16 },
  socialBtn: { width: 40, height: 40, borderRadius: 20, backgroundColor: PRIMARY, alignItems: 'center', justifyContent: 'center' },

  // Profile Save
  saveChangesBtn: { backgroundColor: PRIMARY, padding: 14, borderRadius: 24, alignItems: 'center', marginBottom: 16 },
  saveChangesBtnDisabled: { opacity: 0.45 },
  saveChangesText: { color: '#fff', fontWeight: '700', fontSize: 15 },

  // Log Out
  logoutBtn: { backgroundColor: PRIMARY, padding: 16, borderRadius: 30, alignItems: 'center', marginTop: 8 },
  logoutText: { color: '#fff', fontWeight: '700', fontSize: 16 },
});
