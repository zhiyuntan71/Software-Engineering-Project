import React, { useCallback, useState } from 'react';
import {
  View, Text, StyleSheet, SafeAreaView, ScrollView,
  TouchableOpacity, Platform, StatusBar, TextInput, Image,
} from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import { router } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { PRIMARY, BG, TEXT, SUBTEXT, INPUT_BG, BORDER } from '@/constants/theme';
import StatusModal from '@/components/ui/StatusModal';
import { useUser, SavedCard, TopUp } from '@/context/UserContext';
import { getWalletBalance, getWalletTransactions, topUpWallet, WalletTransactionResponse } from '@/services/walletService';

const TOP_UP_AMOUNTS = [2.00, 5.00, 10.00, 20.00, 50.00, 100.00];

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

// ─── Select Amount Screen ─────────────────────────────────
function SelectAmountScreen({
  balance, selected, setSelected, onTopUp,
}: {
  balance: number;
  selected: number;
  setSelected: (val: number) => void;
  onTopUp: () => void;
}) {
  return (
    <>
      <View style={styles.card}>
        <Text style={styles.sectionHint}>Choose an amount to add</Text>
        <View style={styles.balanceRow}>
          <Text style={styles.balanceLabel}>Balance:</Text>
          <View style={styles.balanceBadge}>
            <Text style={styles.balanceAmount}>${balance.toFixed(2)}</Text>
          </View>
        </View>
        <View style={styles.divider} />
        <View style={styles.grid}>
          {TOP_UP_AMOUNTS.map((amount) => (
            <TouchableOpacity
              key={amount}
              style={[styles.amountBtn, selected === amount && styles.amountBtnActive]}
              onPress={() => setSelected(amount)}
            >
              {selected === amount && (
                <Ionicons
                  name="checkmark-circle"
                  size={16}
                  color="#fff"
                  style={styles.amountSelectedIcon}
                />
              )}
              <Text style={[styles.amountText, selected === amount && styles.amountTextActive]}>
                S$ {amount.toFixed(2)}
              </Text>
            </TouchableOpacity>
          ))}
        </View>
      </View>

      <TouchableOpacity style={styles.topUpBtn} onPress={onTopUp}>
        <Text style={styles.topUpText}>Top Up S$ {selected.toFixed(2)}</Text>
      </TouchableOpacity>
      <Text style={styles.terms}>
        By continuing, you agree to our{' '}
        <Text style={styles.termsLink}>Top Up Service Agreement</Text>
      </Text>
    </>
  );
}

// ─── Payment Screen ───────────────────────────────────────
function PaymentScreen({
  selected, balance, onPay,
}: {
  selected: number;
  balance: number;
  onPay: (method: 'CARD' | 'PAYNOW', saveCard: boolean, cardData?: Omit<SavedCard, 'id'>) => Promise<void>;
}) {
  const { savedCards, setSavedCards } = useUser();

  const [payTab, setPayTab] = useState<'card' | 'qr'>('card');
  const [selectedSavedCard, setSelectedSavedCard] = useState<string | null>(
    savedCards.length > 0 ? savedCards[0].id : null
  );
  const [useNewCard, setUseNewCard] = useState(savedCards.length === 0);

  const [cardNumber, setCardNumber] = useState('');
  const [expiry, setExpiry] = useState('');
  const [cvc, setCvc] = useState('');
  const [country, setCountry] = useState('Singapore');
  const [postal, setPostal] = useState('');
  const [saveCard, setSaveCard] = useState(false);
  // === POPUP STANDARDIZATION (StatusModal) ===
  const [pendingRemoveCardId, setPendingRemoveCardId] = useState<string | null>(null);
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

  const formatCardNumber = (text: string) => {
    const cleaned = text.replace(/\D/g, '').slice(0, 16);
    return cleaned.replace(/(.{4})/g, '$1 ').trim();
  };

  const formatExpiry = (text: string) => {
    const cleaned = text.replace(/\D/g, '').slice(0, 4);
    if (cleaned.length >= 3) return `${cleaned.slice(0, 2)}/${cleaned.slice(2)}`;
    return cleaned;
  };

  const handleDeleteCard = (id: string) => {
    setPendingRemoveCardId(id);
  };

  const handlePay = async () => {
    if (payTab === 'card' && useNewCard) {
      const cleanedCardNumber = cardNumber.replace(/\s/g, '');
      const isValidExpiry = /^(0[1-9]|1[0-2])\/\d{2}$/.test(expiry);
      const isValidCvc = /^\d{3,4}$/.test(cvc);

      if (cleanedCardNumber.length !== 16 || !isValidExpiry || !isValidCvc) {
        openFeedbackModal('warning', 'Invalid card details', 'Please enter valid card number, expiry, and CVC.');
        return;
      }

      await onPay('CARD', saveCard, {
        cardNumber: cardNumber.replace(/\s/g, '').slice(-4),
        expiry, country, postal,
      });
    } else if (payTab === 'card' && !useNewCard) {
      await onPay('CARD', false);
    } else {
      await onPay('PAYNOW', false);
    }
  };

  return (
    <>
      <View style={styles.card}>
        {/* Summary */}
        <View style={styles.summaryRow}>
          <Text style={styles.summaryLabel}>Top Up:</Text>
          <View style={styles.balanceBadge}>
            <Text style={styles.balanceAmount}>${selected.toFixed(2)}</Text>
          </View>
        </View>
        <View style={[styles.summaryRow, { marginTop: 12 }]}>
          <Text style={styles.summaryLabel}>New Balance:</Text>
          <View style={styles.balanceBadge}>
            <Text style={styles.balanceAmount}>${(balance + selected).toFixed(2)}</Text>
          </View>
        </View>

        <View style={[styles.divider, { marginTop: 16 }]} />

        {/* Card / QR Tabs */}
        <View style={styles.payTabBar}>
          <TouchableOpacity
            style={[styles.payTab, payTab === 'card' && styles.payTabActive]}
            onPress={() => setPayTab('card')}
          >
            <Ionicons name="card-outline" size={16} color={payTab === 'card' ? PRIMARY : SUBTEXT} />
            <Text style={[styles.payTabText, payTab === 'card' && styles.payTabTextActive]}>Card</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.payTab, payTab === 'qr' && styles.payTabActive]}
            onPress={() => setPayTab('qr')}
          >
            <Ionicons name="qr-code-outline" size={16} color={payTab === 'qr' ? PRIMARY : SUBTEXT} />
            <Text style={[styles.payTabText, payTab === 'qr' && styles.payTabTextActive]}>QR Code</Text>
          </TouchableOpacity>
        </View>

        {/* Card Tab */}
        {payTab === 'card' && (
          <View style={{ marginTop: 12 }}>

            {/* Saved Cards */}
            {savedCards.length > 0 && (
              <View>
                <Text style={styles.fieldLabel}>Saved Cards</Text>
                {savedCards.map((c) => (
                  <TouchableOpacity
                    key={c.id}
                    style={[styles.savedCardRow, !useNewCard && selectedSavedCard === c.id && styles.savedCardActive]}
                    onPress={() => { setSelectedSavedCard(c.id); setUseNewCard(false); }}
                  >
                    <Ionicons name="card-outline" size={20} color={PRIMARY} style={{ marginRight: 10 }} />
                    <View style={{ flex: 1 }}>
                      <Text style={styles.savedCardNumber}>•••• •••• •••• {c.cardNumber}</Text>
                      <Text style={styles.savedCardSub}>Expires {c.expiry}  {c.country}</Text>
                    </View>
                    {!useNewCard && selectedSavedCard === c.id && (
                      <Ionicons name="checkmark-circle" size={20} color={PRIMARY} style={{ marginRight: 8 }} />
                    )}
                    <TouchableOpacity onPress={() => handleDeleteCard(c.id)}>
                      <Ionicons name="trash-outline" size={18} color="#E74C3C" />
                    </TouchableOpacity>
                  </TouchableOpacity>
                ))}

                {/* Use New Card Toggle */}
                <TouchableOpacity
                  style={[styles.savedCardRow, useNewCard && styles.savedCardActive]}
                  onPress={() => { setUseNewCard(true); setSelectedSavedCard(null); }}
                >
                  <Ionicons name="add-circle-outline" size={20} color={PRIMARY} style={{ marginRight: 10 }} />
                  <Text style={{ fontSize: 14, color: PRIMARY, fontWeight: '600' }}>Use a new card</Text>
                  {useNewCard && (
                    <Ionicons name="checkmark-circle" size={20} color={PRIMARY} style={{ marginLeft: 'auto' }} />
                  )}
                </TouchableOpacity>
              </View>
            )}

            {/* New Card Form */}
            {useNewCard && (
              <View>
                <Text style={styles.fieldLabel}>Card number</Text>
                <View style={styles.cardInputRow}>
                  <Ionicons name="card-outline" size={18} color={SUBTEXT} style={{ marginRight: 8 }} />
                  <TextInput
                    style={styles.cardInput}
                    placeholder="1234 1234 1234 1234"
                    placeholderTextColor={SUBTEXT}
                    value={cardNumber}
                    onChangeText={(t) => setCardNumber(formatCardNumber(t))}
                    keyboardType="numeric"
                    maxLength={19}
                  />
                </View>

                <View style={styles.rowInputs}>
                  <View style={{ flex: 1, marginRight: 8 }}>
                    <Text style={styles.fieldLabel}>Expiry</Text>
                    <TextInput
                      style={styles.fieldInput}
                      placeholder="MM / YY"
                      placeholderTextColor={SUBTEXT}
                      value={expiry}
                      onChangeText={(t) => setExpiry(formatExpiry(t))}
                      keyboardType="numeric"
                      maxLength={5}
                    />
                  </View>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.fieldLabel}>CVC</Text>
                    <TextInput
                      style={styles.fieldInput}
                      placeholder="CVC"
                      placeholderTextColor={SUBTEXT}
                      value={cvc}
                      onChangeText={(t) => setCvc(t.replace(/\D/g, '').slice(0, 4))}
                      keyboardType="numeric"
                      maxLength={4}
                      secureTextEntry
                    />
                  </View>
                </View>

                <View style={styles.rowInputs}>
                  <View style={{ flex: 1, marginRight: 8 }}>
                    <Text style={styles.fieldLabel}>Country</Text>
                    <TextInput
                      style={styles.fieldInput}
                      placeholder="Singapore"
                      placeholderTextColor={SUBTEXT}
                      value={country}
                      onChangeText={setCountry}
                    />
                  </View>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.fieldLabel}>Postal code</Text>
                    <TextInput
                      style={styles.fieldInput}
                      placeholder="123456"
                      placeholderTextColor={SUBTEXT}
                      value={postal}
                      onChangeText={(t) => setPostal(t.replace(/\D/g, '').slice(0, 6))}
                      keyboardType="numeric"
                      maxLength={6}
                    />
                  </View>
                </View>

                {/* Save Card Checkbox */}
                <TouchableOpacity
                  style={styles.saveCardRow}
                  onPress={() => setSaveCard(!saveCard)}
                >
                  <View style={[styles.checkbox, saveCard && styles.checkboxActive]}>
                    {saveCard && <Ionicons name="checkmark" size={14} color="#fff" />}
                  </View>
                  <Text style={styles.saveCardText}>Save card for future payments</Text>
                </TouchableOpacity>
              </View>
            )}
          </View>
        )}

        {/* PayNow QR */}
        {payTab === 'qr' && (
          <View style={styles.qrContainer}>
            <Image
              source={{ uri: `https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=PAYNOW-CAR1STOP-${selected}SGD` }}
              style={styles.qrImage}
              resizeMode="contain"
            />
            <Text style={styles.qrLabel}>Scan with PayNow</Text>
          </View>
        )}
      </View>

      <TouchableOpacity style={styles.topUpBtn} onPress={handlePay}>
        <Text style={styles.topUpText}>Pay</Text>
      </TouchableOpacity>
      <Text style={styles.terms}>
        By continuing, you agree to our{' '}
        <Text style={styles.termsLink}>Top Up Service Agreement</Text>
      </Text>

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
          const updated = savedCards.filter((c) => c.id !== id);
          setSavedCards(updated);
          if (selectedSavedCard === id) {
            setSelectedSavedCard(updated.length > 0 ? updated[0].id : null);
            setUseNewCard(updated.length === 0);
          }
        }}
        secondaryLabel="Cancel"
        onSecondary={() => setPendingRemoveCardId(null)}
        onRequestClose={() => setPendingRemoveCardId(null)}
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
    </>
  );
}

// ─── Main Wallet Screen ───────────────────────────────────
export default function Wallet() {
  const { balance, setBalance, setTopups, savedCards, setSavedCards } = useUser();
  const [selected, setSelected] = useState<number>(5);
  const [screen, setScreen] = useState<'select' | 'payment'>('select');
  const [topUpSuccess, setTopUpSuccess] = useState(false);
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

  const refreshWalletData = useCallback(async () => {
    const [balanceResp, transactions] = await Promise.all([
      getWalletBalance(),
      getWalletTransactions(),
    ]);
    setBalance(Number(balanceResp.balance ?? 0));
    setTopups(mapTopUpTransactions(transactions));
  }, [setBalance, setTopups]);

  useFocusEffect(
    useCallback(() => {
      refreshWalletData().catch(() => {
        openFeedbackModal('error', 'Unable to load wallet', 'Please try again.');
      });
    }, [refreshWalletData])
  );

  const handlePay = async (method: 'CARD' | 'PAYNOW', saveCard: boolean, cardData?: Omit<SavedCard, 'id'>) => {
    try {
      await topUpWallet({
        amount: selected,
        paymentMethod: method,
      });

      if (saveCard && cardData) {
        setSavedCards([...savedCards, { id: Date.now().toString(), ...cardData }]);
      }

      await refreshWalletData();
      setTopUpSuccess(true);
      openFeedbackModal('success', 'Top Up Successful', `$${selected.toFixed(2)} has been added to your wallet.`);
    } catch {
      openFeedbackModal('error', 'Top up failed', 'Please try again.');
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>

        {/* Header */}
        <View style={styles.header}>
          <TouchableOpacity
            style={styles.backBtn}
            onPress={() => screen === 'payment' ? setScreen('select') : router.back()}
          >
            <Ionicons name="arrow-back-outline" size={20} color={PRIMARY} />
          </TouchableOpacity>
          <Text style={styles.headerTitle}>
            {screen === 'payment' ? 'Payment' : 'Top Up'}
          </Text>
          <TouchableOpacity style={styles.iconBtn} onPress={() => router.push('/profile')}>
            <Ionicons name="person-outline" size={20} color={PRIMARY} />
          </TouchableOpacity>
        </View>

        {screen === 'select' ? (
          <SelectAmountScreen
            balance={balance}
            selected={selected}
            setSelected={setSelected}
            onTopUp={() => setScreen('payment')}
          />
        ) : (
          <PaymentScreen
            selected={selected}
            balance={balance}
            onPay={handlePay}
          />
        )}

      </ScrollView>

      {/* === POPUP STANDARDIZATION (StatusModal) === */}
      <StatusModal
        visible={showFeedbackModal}
        variant={feedbackVariant}
        title={feedbackTitle}
        message={feedbackMessage}
        primaryLabel="OK"
        onPrimary={() => {
          setShowFeedbackModal(false);
          if (topUpSuccess) { setTopUpSuccess(false); router.back(); }
        }}
        onRequestClose={() => {
          setShowFeedbackModal(false);
          if (topUpSuccess) { setTopUpSuccess(false); router.back(); }
        }}
      />
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
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 32 },
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
  card: {
    backgroundColor: '#F5F9FA',
    borderRadius: 20,
    padding: 20,
    marginBottom: 24,
    borderWidth: 1,
    borderColor: '#E3EEF4',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.06,
    shadowRadius: 10,
    elevation: 2,
  },
  sectionHint: {
    fontSize: 12,
    color: SUBTEXT,
    fontWeight: '600',
    marginBottom: 10,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  balanceRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16 },
  balanceLabel: { fontSize: 20, fontWeight: '700', color: TEXT },
  balanceBadge: { backgroundColor: PRIMARY, borderRadius: 20, paddingHorizontal: 18, paddingVertical: 8 },
  balanceAmount: { color: '#fff', fontWeight: '700', fontSize: 16 },
  divider: { height: 1, backgroundColor: '#E0E0E0', marginBottom: 16 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: 12, justifyContent: 'space-between' },
  amountBtn: {
    width: '30%',
    paddingVertical: 14,
    borderRadius: 12,
    backgroundColor: '#C7F9FF',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#B6E5EE',
    position: 'relative',
  },
  amountBtnActive: {
    backgroundColor: PRIMARY,
    borderWidth: 1.5,
    borderColor: '#4C8AA4',
    shadowColor: '#4C8AA4',
    shadowOffset: { width: 0, height: 3 },
    shadowOpacity: 0.22,
    shadowRadius: 8,
    elevation: 2,
  },
  amountSelectedIcon: {
    position: 'absolute',
    top: 6,
    right: 6,
  },
  amountText: { fontSize: 14, fontWeight: '700', color: TEXT },
  amountTextActive: { color: '#fff' },
  summaryRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  summaryLabel: { fontSize: 20, fontWeight: '700', color: TEXT },
  payTabBar: { flexDirection: 'row', gap: 12, marginTop: 4 },
  payTab: {
    flexDirection: 'row', alignItems: 'center', gap: 6,
    flex: 1, paddingVertical: 10, borderRadius: 10,
    borderWidth: 1, borderColor: '#E0E0E0',
    justifyContent: 'center', backgroundColor: BG,
  },
  payTabActive: { borderColor: PRIMARY, backgroundColor: '#EAF5F7' },
  payTabText: { fontSize: 13, color: SUBTEXT, fontWeight: '600' },
  payTabTextActive: { color: PRIMARY },
  fieldLabel: { fontSize: 12, color: SUBTEXT, marginBottom: 6, marginTop: 10 },
  cardInputRow: {
    flexDirection: 'row', alignItems: 'center',
    borderWidth: 1, borderColor: BORDER, borderRadius: 10,
    backgroundColor: BG, paddingHorizontal: 12, paddingVertical: 12,
  },
  cardInput: { flex: 1, fontSize: 14, color: TEXT },
  rowInputs: { flexDirection: 'row', marginTop: 4 },
  fieldInput: {
    borderWidth: 1, borderColor: BORDER, borderRadius: 10,
    backgroundColor: BG, padding: 12, fontSize: 14, color: TEXT,
  },
  savedCardRow: {
    flexDirection: 'row', alignItems: 'center',
    borderWidth: 1, borderColor: '#E0E0E0', borderRadius: 12,
    padding: 12, marginBottom: 8, backgroundColor: BG,
  },
  savedCardActive: { borderColor: PRIMARY, backgroundColor: '#EAF5F7' },
  savedCardNumber: { fontSize: 14, fontWeight: '600', color: TEXT },
  savedCardSub: { fontSize: 12, color: SUBTEXT, marginTop: 2 },
  saveCardRow: { flexDirection: 'row', alignItems: 'center', marginTop: 14 },
  checkbox: {
    width: 20, height: 20, borderRadius: 4,
    borderWidth: 2, borderColor: BORDER,
    alignItems: 'center', justifyContent: 'center', marginRight: 10,
  },
  checkboxActive: { backgroundColor: PRIMARY, borderColor: PRIMARY },
  saveCardText: { fontSize: 13, color: TEXT, fontWeight: '500' },
  qrContainer: { alignItems: 'center', paddingVertical: 16 },
  qrImage: { width: 160, height: 160, marginBottom: 12 },
  qrLabel: { fontSize: 13, color: SUBTEXT, fontWeight: '600' },
  topUpBtn: {
    backgroundColor: PRIMARY,
    borderRadius: 30,
    padding: 16,
    alignItems: 'center',
    marginBottom: 16,
    shadowColor: '#4C8AA4',
    shadowOffset: { width: 0, height: 6 },
    shadowOpacity: 0.18,
    shadowRadius: 10,
    elevation: 3,
  },
  topUpText: { color: '#fff', fontSize: 16, fontWeight: '700' },
  terms: { fontSize: 12, color: SUBTEXT, textAlign: 'center', lineHeight: 18 },
  termsLink: { color: PRIMARY, fontWeight: '600' },
});
