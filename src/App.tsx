import React, { useEffect, useMemo, useState } from 'react';
import {
  CheckCircle2,
  Download,
  FolderCode,
  Moon,
  Pencil,
  Plus,
  Search,
  ShieldCheck,
  Sun,
  Trash2,
} from 'lucide-react';
import {
  addDaysIso,
  Budget,
  calculateAvailableBalanceCentavos,
  Debt,
  EXPENSE_CATEGORIES,
  exportTransactionsCsv,
  FinanceState,
  FinancialTransaction,
  formatMxnFromCentavos,
  getPeriodRange,
  INCOME_CATEGORIES,
  MOVEMENT_LABELS,
  MovementType,
  parseMxnToCentavos,
  PAYMENT_METHOD_LABELS,
  PaymentMethod,
  PERIOD_LABELS,
  PeriodType,
  RECURRENCE_LABELS,
  RecurrenceFrequency,
  resolveEffectivePaymentStatus,
  SavingsGoal,
  SCHEDULED_PAYMENT_CATEGORIES,
  ScheduledPayment,
  simulateDebtPayoff,
  todayIso,
} from './financeEngine';

type NavTab = 'home' | 'movements' | 'budget' | 'savings' | 'profile';

const STORAGE_KEY = 'mi_bolsillo_inteligente_v1';

const EMPTY_STATE: FinanceState = {
  initialBalanceCentavos: 0,
  selectedPeriod: 'BIWEEKLY',
  customStart: todayIso().slice(0, 8) + '01',
  customEnd: todayIso(),
  globalThresholds: [50, 75, 90, 100],
  transactions: [],
  budgets: [],
  scheduledPayments: [],
  savingsGoals: [],
  debts: [],
  demoLoaded: false,
};

const ANDROID_FILES_SUMMARY = [
  { path: 'gradle/libs.versions.toml', role: 'Version Catalog con versiones estables (AGP 8.7.3, Kotlin 2.0.21, Compose BOM, Room 2.6.1, AdMob 23.5.0)' },
  { path: 'app/build.gradle.kts', role: 'Configuración de módulo Android, KSP para Room, firma de producción por variables de entorno y BuildConfig AdMob' },
  { path: '.github/workflows/android.yml', role: 'Workflow CI/CD Ubuntu + Java 17 + Android SDK + pruebas unitarias, lint, APK y AAB' },
  { path: 'app/src/main/java/.../domain/usecase/FinanceCalculator.kt', role: 'Motor de cálculo monetario con BigDecimal, presupuestos, deudas y exportación CSV segura' },
  { path: 'app/src/main/java/.../data/local/AppDatabase.kt', role: 'Base de datos Room con transacciones atómicas (@Transaction) e idempotencia anti-duplicados' },
  { path: 'app/src/main/java/.../security/KeystoreCryptoManager.kt', role: 'Protección de llaves AES-256-GCM en Android Keystore y BiometricLockManager' },
  { path: 'app/src/main/java/.../ads/AdManager.kt', role: 'Banner adaptativo anclado discreto de AdMob con consentimiento UMP y etiqueta Anuncio' },
  { path: 'app/src/test/java/.../FinanceCalculatorTest.kt', role: 'Pruebas unitarias JUnit 4 de saldos, periodos, pagos, deudas, ahorro y límites' },
];

export default function App() {
  const [state, setState] = useState<FinanceState>(() => {
    try {
      const saved = localStorage.getItem(STORAGE_KEY);
      if (saved) return { ...EMPTY_STATE, ...JSON.parse(saved) };
    } catch {
      // Ignorar si almacenamiento no disponible
    }
    return EMPTY_STATE;
  });

  const [darkMode, setDarkMode] = useState<boolean>(() => {
    try {
      return localStorage.getItem('mbi_theme') === 'dark';
    } catch {
      return false;
    }
  });

  const [activeTab, setActiveTab] = useState<NavTab>('home');
  const [movementModalOpen, setMovementModalOpen] = useState(false);
  const [movementModalType, setMovementModalType] = useState<'INCOME' | 'EXPENSE'>('EXPENSE');
  const [editingTx, setEditingTx] = useState<FinancialTransaction | null>(null);
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  // Filtros en Movimientos
  const [searchQuery, setSearchQuery] = useState('');
  const [typeFilter, setTypeFilter] = useState<'ALL' | 'INCOME' | 'EXPENSE'>('ALL');
  const [categoryFilter, setCategoryFilter] = useState<string>('ALL');

  // Modales auxiliares
  const [budgetModalOpen, setBudgetModalOpen] = useState(false);
  const [paymentModalOpen, setPaymentModalOpen] = useState(false);
  const [goalModalOpen, setGoalModalOpen] = useState(false);
  const [goalMoveModal, setGoalMoveModal] = useState<{ goal: SavingsGoal; isContribution: boolean } | null>(null);
  const [debtModalOpen, setDebtModalOpen] = useState(false);
  const [debtPayModal, setDebtPayModal] = useState<Debt | null>(null);
  const [debtSimModal, setDebtSimModal] = useState<Debt | null>(null);
  const [adjustBalanceModalOpen, setAdjustBalanceModalOpen] = useState(false);
  const [confirmWipeOpen, setConfirmWipeOpen] = useState(false);
  const [showAndroidModal, setShowAndroidModal] = useState(false);
  const [showStartupSplash, setShowStartupSplash] = useState(true);
  const [splashFading, setSplashFading] = useState(false);

  useEffect(() => {
    const fadeTimer = setTimeout(() => setSplashFading(true), 950);
    const hideTimer = setTimeout(() => setShowStartupSplash(false), 1300);
    return () => {
      clearTimeout(fadeTimer);
      clearTimeout(hideTimer);
    };
  }, []);

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    } catch {
      // Silencioso
    }
  }, [state]);

  useEffect(() => {
    document.documentElement.classList.toggle('dark', darkMode);
    try {
      localStorage.setItem('mbi_theme', darkMode ? 'dark' : 'light');
    } catch {
      // Silencioso
    }
  }, [darkMode]);

  useEffect(() => {
    if (!toastMessage) return;
    const t = setTimeout(() => setToastMessage(null), 3500);
    return () => clearTimeout(t);
  }, [toastMessage]);

  const today = todayIso();
  const currentRange = useMemo(
    () => getPeriodRange(state.selectedPeriod, today, state.customStart, state.customEnd),
    [state.selectedPeriod, today, state.customStart, state.customEnd]
  );

  const availableBalance = useMemo(
    () => calculateAvailableBalanceCentavos(state.initialBalanceCentavos, state.transactions),
    [state.initialBalanceCentavos, state.transactions]
  );

  const periodIncome = useMemo(
    () =>
      state.transactions
        .filter((tx) => tx.type === 'INCOME' && tx.date >= currentRange.start && tx.date <= currentRange.end)
        .reduce((acc, tx) => acc + tx.amountCentavos, 0),
    [state.transactions, currentRange]
  );

  const periodExpenses = useMemo(
    () =>
      state.transactions
        .filter(
          (tx) =>
            (tx.type === 'EXPENSE' || tx.type === 'DEBT_PAYMENT') &&
            tx.date >= currentRange.start &&
            tx.date <= currentRange.end
        )
        .reduce((acc, tx) => acc + tx.amountCentavos, 0),
    [state.transactions, currentRange]
  );

  const committedUpcoming = useMemo(
    () =>
      state.scheduledPayments
        .filter((p) => resolveEffectivePaymentStatus(p, today) !== 'PAID')
        .reduce((acc, p) => acc + p.amountCentavos, 0),
    [state.scheduledPayments, today]
  );

  const budgetProgressList = useMemo(() => {
    return state.budgets.map((b) => {
      const range = getPeriodRange(b.periodType, today, state.customStart, state.customEnd);
      const spent = state.transactions
        .filter(
          (tx) =>
            (tx.type === 'EXPENSE' || tx.type === 'DEBT_PAYMENT') &&
            tx.date >= range.start &&
            tx.date <= range.end &&
            (b.category === 'General' || tx.category === b.category)
        )
        .reduce((acc, tx) => acc + tx.amountCentavos, 0);
      const remaining = b.limitCentavos - spent;
      const pct = b.limitCentavos > 0 ? Math.round((spent / b.limitCentavos) * 100) : 0;
      const triggered = [...b.thresholds].sort((a, z) => a - z).filter((t) => pct >= t).pop() ?? null;
      return { budget: b, spent, remaining, pct, triggered };
    });
  }, [state.budgets, state.transactions, today, state.customStart, state.customEnd]);

  const totalBudgetRemaining = useMemo(
    () => budgetProgressList.reduce((acc, b) => acc + b.remaining, 0),
    [budgetProgressList]
  );

  const savingsTotals = useMemo(() => {
    const saved = state.savingsGoals.reduce((acc, g) => acc + g.savedCentavos, 0);
    const target = state.savingsGoals.reduce((acc, g) => acc + g.targetCentavos, 0);
    const pct = target > 0 ? Math.min(100, Math.round((saved / target) * 100)) : 0;
    return { saved, target, pct };
  }, [state.savingsGoals]);

  const expenseBreakdown = useMemo(() => {
    const map = new Map<string, number>();
    let total = 0;
    for (const tx of state.transactions) {
      if (
        (tx.type === 'EXPENSE' || tx.type === 'DEBT_PAYMENT') &&
        tx.date >= currentRange.start &&
        tx.date <= currentRange.end
      ) {
        map.set(tx.category, (map.get(tx.category) ?? 0) + tx.amountCentavos);
        total += tx.amountCentavos;
      }
    }
    return Array.from(map.entries())
      .map(([category, amountCentavos]) => ({
        category,
        amountCentavos,
        pct: total > 0 ? Math.round((amountCentavos / total) * 100) : 0,
      }))
      .sort((a, b) => b.amountCentavos - a.amountCentavos);
  }, [state.transactions, currentRange]);

  const filteredTransactions = useMemo(() => {
    return [...state.transactions]
      .filter((tx) => {
        const matchesQ =
          !searchQuery.trim() ||
          tx.description.toLowerCase().includes(searchQuery.toLowerCase()) ||
          tx.category.toLowerCase().includes(searchQuery.toLowerCase());
        const matchesT = typeFilter === 'ALL' || tx.type === typeFilter;
        const matchesC = categoryFilter === 'ALL' || tx.category === categoryFilter;
        return matchesQ && matchesT && matchesC;
      })
      .sort((a, b) => b.date.localeCompare(a.date));
  }, [state.transactions, searchQuery, typeFilter, categoryFilter]);

  function handleSaveMovement(payload: {
    type: 'INCOME' | 'EXPENSE';
    amountCentavos: number;
    category: string;
    date: string;
    description: string;
    recurrence: RecurrenceFrequency;
    paymentMethod: PaymentMethod;
  }) {
    if (editingTx) {
      setState((prev) => ({
        ...prev,
        transactions: prev.transactions.map((t) =>
          t.id === editingTx.id
            ? {
                ...t,
                ...payload,
                isAdjustmentPositive: payload.type === 'INCOME',
              }
            : t
        ),
      }));
      setToastMessage('Movimiento actualizado.');
    } else {
      const newTx: FinancialTransaction = {
        id: crypto.randomUUID(),
        ...payload,
        isAdjustmentPositive: payload.type === 'INCOME',
      };
      setState((prev) => ({
        ...prev,
        transactions: [newTx, ...prev.transactions],
      }));
      setToastMessage(`${MOVEMENT_LABELS[payload.type]} guardado correctamente.`);
    }
    setEditingTx(null);
    setMovementModalOpen(false);
  }

  function handleDeleteTx(id: string) {
    setState((prev) => ({
      ...prev,
      transactions: prev.transactions.filter((t) => t.id !== id),
    }));
    setToastMessage('Movimiento eliminado.');
  }

  function handleMarkPaymentPaid(payment: ScheduledPayment) {
    if (payment.status === 'PAID') return;
    const category =
      payment.category === 'Renta'
        ? 'Vivienda'
        : payment.category === 'Tarjetas de crédito' || payment.category === 'Préstamos'
        ? 'Deudas'
        : payment.category === 'Colegiaturas'
        ? 'Educación'
        : payment.category === 'Servicios'
        ? 'Servicios'
        : 'Otros gastos';

    const newTx: FinancialTransaction = {
      id: `sched_${payment.id}`,
      type: 'EXPENSE',
      amountCentavos: payment.amountCentavos,
      category,
      date: today,
      description: `Pago programado: ${payment.description}`,
      recurrence: payment.recurrence,
      paymentMethod: 'TRANSFER',
      isAdjustmentPositive: false,
    };

    setState((prev) => {
      if (prev.transactions.some((t) => t.id === newTx.id)) return prev;
      return {
        ...prev,
        scheduledPayments: prev.scheduledPayments.map((p) =>
          p.id === payment.id ? { ...p, status: 'PAID' } : p
        ),
        transactions: [newTx, ...prev.transactions],
      };
    });
    setToastMessage('Pago registrado en gastos sin duplicados.');
  }

  function handleExportCsv() {
    const csv = exportTransactionsCsv(state.transactions);
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `mi_bolsillo_movimientos_${today}.csv`;
    a.click();
    URL.revokeObjectURL(url);
    setToastMessage('Archivo CSV descargado.');
  }

  function handleLoadDemo() {
    setState({
      initialBalanceCentavos: 350000,
      selectedPeriod: 'BIWEEKLY',
      customStart: todayIso().slice(0, 8) + '01',
      customEnd: todayIso(),
      globalThresholds: [50, 75, 90, 100],
      demoLoaded: true,
      transactions: [
        {
          id: 'demo_1',
          type: 'INCOME',
          amountCentavos: 950000,
          category: 'Sueldo',
          date: addDaysIso(today, -4),
          description: 'Quincena laboral (Demo)',
          recurrence: 'BIWEEKLY',
          paymentMethod: 'TRANSFER',
          isAdjustmentPositive: true,
        },
        {
          id: 'demo_2',
          type: 'EXPENSE',
          amountCentavos: 142050,
          category: 'Alimentación',
          date: addDaysIso(today, -2),
          description: 'Despensa semanal (Demo)',
          recurrence: 'NONE',
          paymentMethod: 'DEBIT',
          isAdjustmentPositive: false,
        },
        {
          id: 'demo_3',
          type: 'EXPENSE',
          amountCentavos: 48000,
          category: 'Transporte',
          date: addDaysIso(today, -1),
          description: 'Traslados y recarga (Demo)',
          recurrence: 'NONE',
          paymentMethod: 'CASH',
          isAdjustmentPositive: false,
        },
      ],
      budgets: [
        {
          id: 'demo_b1',
          category: 'Alimentación',
          periodType: 'BIWEEKLY',
          limitCentavos: 250000,
          thresholds: [50, 75, 90, 100],
        },
      ],
      scheduledPayments: [
        {
          id: 'demo_sp1',
          description: 'Internet y telefonía (Demo)',
          category: 'Servicios',
          amountCentavos: 59900,
          dueDate: addDaysIso(today, 4),
          status: 'PENDING',
          recurrence: 'MONTHLY',
        },
      ],
      savingsGoals: [
        {
          id: 'demo_sg1',
          name: 'Fondo de emergencia (Demo)',
          targetCentavos: 1500000,
          savedCentavos: 420000,
          targetDate: addDaysIso(today, 180),
          description: 'Respaldo para imprevistos',
        },
      ],
      debts: [
        {
          id: 'demo_d1',
          creditorName: 'Tarjeta bancaria (Demo)',
          initialBalanceCentavos: 600000,
          remainingBalanceCentavos: 420000,
          dueDate: addDaysIso(today, 10),
          minimumPaymentCentavos: 70000,
          annualInterestRatePercent: 36,
          payments: [
            { id: 'demo_dp1', amountCentavos: 180000, date: addDaysIso(today, -15), note: 'Abono inicial' },
          ],
        },
      ],
    });
    setToastMessage('Datos de demostración cargados.');
  }

  function handleWipeAll() {
    setState(EMPTY_STATE);
    setConfirmWipeOpen(false);
    setToastMessage('Todos los datos locales fueron eliminados.');
  }

  return (
    <div className="min-h-screen bg-[#F7F9F7] dark:bg-[#0E1510] text-[#171D18] dark:text-[#E3EAE3] transition-colors">
      {/* Pantalla de inicio breve al abrir la aplicación */}
      {showStartupSplash && (
        <div
          role="dialog"
          aria-label="Inicio de Calculadora de Gastos"
          onClick={() => setShowStartupSplash(false)}
          className={`fixed inset-0 z-50 flex flex-col items-center justify-center bg-[#F7F9F7] dark:bg-[#0E1510] px-6 transition-opacity duration-300 ${
            splashFading ? 'opacity-0 pointer-events-none' : 'opacity-100'
          }`}
        >
          <div className="flex flex-col items-center text-center max-w-xs space-y-4">
            <img
              src="/assets/aistudio/a_clean_high_resolution_cute_3d_cartoon_illustra_dd88b30b.png"
              alt="Calculadora de Gastos"
              referrerPolicy="no-referrer"
              className="w-40 h-40 rounded-3xl object-contain bg-white border border-[#D5DDD6] dark:border-[#2D3A2F] p-2 shadow-sm"
            />
            <div className="space-y-1">
              <h1 className="text-xl font-bold text-[#1B5E20] dark:text-[#66BB6A]">
                Calculadora de Gastos
              </h1>
              <p className="text-xs text-[#5E6B5F] dark:text-[#96A598]">
                Control financiero personal en MXN
              </p>
            </div>
            <button
              type="button"
              onClick={() => setShowStartupSplash(false)}
              className="px-4 py-1.5 text-xs font-medium rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] bg-white dark:bg-[#151E17] hover:bg-[#F0F4F0] dark:hover:bg-[#1D281F] cursor-pointer"
            >
              Continuar
            </button>
          </div>
        </div>
      )}

      {/* Top Bar Contract: exactamente 3 zonas separadas por gap-8 */}
      <header className="sticky top-0 z-30 border-b border-[#D5DDD6] dark:border-[#253328] bg-white/95 dark:bg-[#131C15]/95 backdrop-blur-sm">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between gap-8">
          <button
            type="button"
            onClick={() => setActiveTab('home')}
            className="text-lg font-bold tracking-tight text-[#1B5E20] dark:text-[#66BB6A] whitespace-nowrap shrink-0 cursor-pointer"
          >
            Calculadora de Gastos
          </button>

          <nav className="hidden md:flex items-center gap-6 text-sm font-medium text-[#424D43] dark:text-[#B8C4B9]">
            {(
              [
                ['home', 'Inicio'],
                ['movements', 'Movimientos'],
                ['budget', 'Presupuesto'],
                ['savings', 'Ahorro'],
                ['profile', 'Perfil'],
              ] as Array<[NavTab, string]>
            ).map(([key, label]) => (
              <button
                key={key}
                type="button"
                onClick={() => setActiveTab(key)}
                className={`whitespace-nowrap shrink-0 pb-0.5 transition-colors cursor-pointer ${
                  activeTab === key
                    ? 'text-[#1B5E20] dark:text-[#66BB6A] font-semibold border-b-2 border-[#1B5E20] dark:border-[#66BB6A]'
                    : 'hover:text-[#171D18] dark:hover:text-white'
                }`}
              >
                {label}
              </button>
            ))}
          </nav>

          <div className="shrink-0">
            <button
              type="button"
              onClick={() => {
                setEditingTx(null);
                setMovementModalType('EXPENSE');
                setMovementModalOpen(true);
              }}
              className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-lg bg-[#1B5E20] hover:bg-[#144A18] text-white transition-colors whitespace-nowrap shrink-0 cursor-pointer"
            >
              <Plus className="w-4 h-4" />
              <span>Registrar movimiento</span>
            </button>
          </div>
        </div>
      </header>

      {/* Navegación móvil inferior */}
      <nav className="md:hidden fixed bottom-0 inset-x-0 z-30 border-t border-[#D5DDD6] dark:border-[#253328] bg-white dark:bg-[#131C15] flex justify-around py-2">
        {(
          [
            ['home', 'Inicio'],
            ['movements', 'Movimientos'],
            ['budget', 'Presupuesto'],
            ['savings', 'Ahorro'],
            ['profile', 'Perfil'],
          ] as Array<[NavTab, string]>
        ).map(([key, label]) => (
          <button
            key={key}
            type="button"
            onClick={() => setActiveTab(key)}
            className={`px-2 py-1 text-xs font-medium whitespace-nowrap ${
              activeTab === key
                ? 'text-[#1B5E20] dark:text-[#66BB6A] font-semibold'
                : 'text-[#424D43] dark:text-[#B8C4B9]'
            }`}
          >
            {label}
          </button>
        ))}
      </nav>

      {/* Contenido principal */}
      <main className="max-w-6xl mx-auto px-4 sm:px-6 py-6 pb-24 md:pb-10 space-y-6">
        {toastMessage && (
          <div className="rounded-xl border border-[#B8D8BC] dark:border-[#2B4B30] bg-[#E8F4EA] dark:bg-[#18301D] px-4 py-3 text-sm font-medium text-[#1B5E20] dark:text-[#A5D6A7]">
            {toastMessage}
          </div>
        )}

        {/* Barra de utilidades del espacio de trabajo */}
        <div className="flex flex-wrap items-center justify-between gap-4 border-b border-[#E3E9E4] dark:border-[#212D23] pb-4">
          <div className="text-sm text-[#424D43] dark:text-[#B8C4B9]">
            <span>Moneda local: Peso Mexicano (MXN)</span>
            <span className="mx-2" aria-hidden="true">·</span>
            <span>Periodo: {currentRange.start} al {currentRange.end}</span>
          </div>
          <div className="flex items-center gap-2.5">
            <button
              type="button"
              onClick={() => setShowAndroidModal(true)}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] bg-white dark:bg-[#151E17] hover:bg-[#F0F4F0] dark:hover:bg-[#1D281F] whitespace-nowrap cursor-pointer"
            >
              <FolderCode className="w-3.5 h-3.5" />
              <span>Proyecto Android y CI/CD</span>
            </button>
            <button
              type="button"
              onClick={() => setDarkMode((d) => !d)}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] bg-white dark:bg-[#151E17] hover:bg-[#F0F4F0] dark:hover:bg-[#1D281F] whitespace-nowrap cursor-pointer"
              aria-label="Cambiar tema claro u oscuro"
            >
              {darkMode ? <Sun className="w-3.5 h-3.5" /> : <Moon className="w-3.5 h-3.5" />}
              <span>{darkMode ? 'Tema claro' : 'Tema oscuro'}</span>
            </button>
            <img
              src="/assets/aistudio/a_clean_high_resolution_cute_3d_cartoon_illustra_dd88b30b.png"
              alt="Emblema Calculadora de Gastos"
              referrerPolicy="no-referrer"
              className="w-9 h-9 rounded-xl object-contain bg-white border border-[#D5DDD6] dark:border-[#2D3A2F] p-0.5 shrink-0 select-none pointer-events-none"
            />
          </div>
        </div>

        {/* Vista: INICIO */}
        {activeTab === 'home' && (
          <div className="space-y-6">
            {/* Selector de periodo */}
            <div className="flex flex-wrap items-center justify-between gap-4">
              <div className="inline-flex items-center gap-1 p-1 rounded-lg bg-[#E9EFEA] dark:bg-[#1A241C]">
                {(['WEEKLY', 'BIWEEKLY', 'MONTHLY', 'CUSTOM'] as PeriodType[]).map((period) => (
                  <button
                    key={period}
                    type="button"
                    onClick={() => setState((prev) => ({ ...prev, selectedPeriod: period }))}
                    className={`px-3 py-1.5 text-xs font-medium rounded-md whitespace-nowrap transition-colors cursor-pointer ${
                      state.selectedPeriod === period
                        ? 'bg-white dark:bg-[#253328] text-[#171D18] dark:text-white shadow-xs'
                        : 'text-[#424D43] dark:text-[#B8C4B9] hover:text-[#171D18]'
                    }`}
                  >
                    {PERIOD_LABELS[period]}
                  </button>
                ))}
              </div>

              {state.selectedPeriod === 'CUSTOM' && (
                <div className="flex items-center gap-2 text-xs">
                  <input
                    type="date"
                    value={state.customStart}
                    onChange={(e) => setState((prev) => ({ ...prev, customStart: e.target.value }))}
                    className="px-2.5 py-1.5 rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] bg-white dark:bg-[#151E17]"
                  />
                  <span>a</span>
                  <input
                    type="date"
                    value={state.customEnd}
                    onChange={(e) => setState((prev) => ({ ...prev, customEnd: e.target.value }))}
                    className="px-2.5 py-1.5 rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] bg-white dark:bg-[#151E17]"
                  />
                </div>
              )}
            </div>

            {/* Tarjeta focal de saldo disponible */}
            <section className="rounded-2xl border border-[#C3DEC6] dark:border-[#27462C] bg-[#EAF4EC] dark:bg-[#152819] p-6">
              <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-6">
                <div>
                  <p className="text-sm font-medium text-[#1B5E20] dark:text-[#81C784]">
                    Saldo disponible
                  </p>
                  <p
                    className={`font-money text-3xl sm:text-4xl font-bold mt-1 ${
                      availableBalance < 0
                        ? 'text-[#C62828] dark:text-[#FF8A80]'
                        : 'text-[#0E2911] dark:text-white'
                    }`}
                  >
                    {formatMxnFromCentavos(availableBalance)}
                  </p>
                  <p className="text-xs text-[#38523B] dark:text-[#A5B8A8] mt-2">
                    Saldo inicial + ingresos registrados - gastos registrados - aportaciones apartadas en metas
                  </p>
                </div>

                <div className="flex flex-wrap items-center gap-3">
                  <button
                    type="button"
                    onClick={() => {
                      setEditingTx(null);
                      setMovementModalType('EXPENSE');
                      setMovementModalOpen(true);
                    }}
                    className="px-4 py-2.5 rounded-lg bg-[#1B5E20] hover:bg-[#144A18] text-white text-sm font-medium whitespace-nowrap cursor-pointer"
                  >
                    Gasto rápido
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setEditingTx(null);
                      setMovementModalType('INCOME');
                      setMovementModalOpen(true);
                    }}
                    className="px-4 py-2.5 rounded-lg border border-[#1B5E20] dark:border-[#66BB6A] text-[#1B5E20] dark:text-[#81C784] text-sm font-medium hover:bg-white/60 dark:hover:bg-white/5 whitespace-nowrap cursor-pointer"
                  >
                    Registrar ingreso
                  </button>
                  {state.transactions.length === 0 && (
                    <button
                      type="button"
                      onClick={handleLoadDemo}
                      className="px-4 py-2.5 rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] bg-white dark:bg-[#151E17] text-sm font-medium hover:bg-[#F5F8F5] dark:hover:bg-[#1D281F] whitespace-nowrap cursor-pointer"
                    >
                      Cargar datos demo
                    </button>
                  )}
                </div>
              </div>
            </section>

            {/* 4 KPIs en fila */}
            <section className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              <div className="rounded-xl border border-[#D5DDD6] dark:border-[#253328] bg-white dark:bg-[#151E17] p-4">
                <p className="text-xs text-[#424D43] dark:text-[#B8C4B9]">Ingresos del periodo</p>
                <p className="font-money text-xl font-bold mt-1 text-[#1B5E20] dark:text-[#66BB6A]">
                  {formatMxnFromCentavos(periodIncome)}
                </p>
              </div>
              <div className="rounded-xl border border-[#D5DDD6] dark:border-[#253328] bg-white dark:bg-[#151E17] p-4">
                <p className="text-xs text-[#424D43] dark:text-[#B8C4B9]">Gastos del periodo</p>
                <p className="font-money text-xl font-bold mt-1">
                  {formatMxnFromCentavos(periodExpenses)}
                </p>
              </div>
              <div className="rounded-xl border border-[#D5DDD6] dark:border-[#253328] bg-white dark:bg-[#151E17] p-4">
                <p className="text-xs text-[#424D43] dark:text-[#B8C4B9]">Comprometido próximo</p>
                <p className="font-money text-xl font-bold mt-1">
                  {formatMxnFromCentavos(committedUpcoming)}
                </p>
                <p className="text-xs text-[#5E6B5F] dark:text-[#96A598] mt-1">
                  Separado del saldo disponible
                </p>
              </div>
              <div className="rounded-xl border border-[#D5DDD6] dark:border-[#253328] bg-white dark:bg-[#151E17] p-4">
                <p className="text-xs text-[#424D43] dark:text-[#B8C4B9]">Presupuesto restante</p>
                <p
                  className={`font-money text-xl font-bold mt-1 ${
                    totalBudgetRemaining < 0 ? 'text-[#C62828] dark:text-[#FF8A80]' : ''
                  }`}
                >
                  {budgetProgressList.length === 0
                    ? 'Sin definir'
                    : formatMxnFromCentavos(totalBudgetRemaining)}
                </p>
              </div>
            </section>

            {/* Progreso de ahorro y próximos vencimientos */}
            <section className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              <div className="rounded-xl border border-[#D5DDD6] dark:border-[#253328] bg-white dark:bg-[#151E17] p-5 space-y-3">
                <div className="flex items-center justify-between">
                  <h2 className="text-base font-semibold">Progreso de ahorro</h2>
                  <button
                    type="button"
                    onClick={() => setActiveTab('savings')}
                    className="text-xs font-medium text-[#1B5E20] dark:text-[#66BB6A] hover:underline cursor-pointer"
                  >
                    Ver metas
                  </button>
                </div>
                {state.savingsGoals.length === 0 ? (
                  <p className="text-sm text-[#424D43] dark:text-[#B8C4B9]">
                    Aún no has creado metas de ahorro. Inicia tu fondo de emergencia o meta personal desde la pestaña Ahorro.
                  </p>
                ) : (
                  <div>
                    <div className="flex justify-between text-sm mb-2">
                      <span>{savingsTotals.pct}% acumulado</span>
                      <span className="font-money font-medium">
                        {formatMxnFromCentavos(savingsTotals.saved)} / {formatMxnFromCentavos(savingsTotals.target)}
                      </span>
                    </div>
                    <div className="w-full h-2.5 rounded-full bg-[#E6ECE7] dark:bg-[#233025] overflow-hidden">
                      <div
                        className="h-full bg-[#4CAF50] transition-all"
                        style={{ width: `${savingsTotals.pct}%` }}
                      />
                    </div>
                  </div>
                )}
              </div>

              <div className="rounded-xl border border-[#D5DDD6] dark:border-[#253328] bg-white dark:bg-[#151E17] p-5 space-y-3">
                <div className="flex items-center justify-between">
                  <h2 className="text-base font-semibold">Próximos vencimientos</h2>
                  <button
                    type="button"
                    onClick={() => setActiveTab('budget')}
                    className="text-xs font-medium text-[#1B5E20] dark:text-[#66BB6A] hover:underline cursor-pointer"
                  >
                    Ir al calendario
                  </button>
                </div>
                {state.scheduledPayments.filter((p) => resolveEffectivePaymentStatus(p, today) !== 'PAID')
                  .length === 0 ? (
                  <p className="text-sm text-[#424D43] dark:text-[#B8C4B9]">
                    No tienes pagos pendientes registrados.
                  </p>
                ) : (
                  <div className="divide-y divide-[#EEF2EE] dark:divide-[#222E24]">
                    {state.scheduledPayments
                      .filter((p) => resolveEffectivePaymentStatus(p, today) !== 'PAID')
                      .sort((a, b) => a.dueDate.localeCompare(b.dueDate))
                      .slice(0, 4)
                      .map((p) => {
                        const status = resolveEffectivePaymentStatus(p, today);
                        return (
                          <div key={p.id} className="py-2.5 flex items-center justify-between gap-4">
                            <div>
                              <p className="text-sm font-medium">{p.description}</p>
                              <p
                                className={`text-xs ${
                                  status === 'OVERDUE'
                                    ? 'text-[#C62828] dark:text-[#FF8A80] font-medium'
                                    : 'text-[#5E6B5F] dark:text-[#96A598]'
                                }`}
                              >
                                {p.category} · Vence {p.dueDate} · {status === 'OVERDUE' ? 'Vencido' : 'Pendiente'}
                              </p>
                            </div>
                            <span className="font-money text-sm font-semibold">
                              {formatMxnFromCentavos(p.amountCentavos)}
                            </span>
                          </div>
                        );
                      })}
                  </div>
                )}
              </div>
            </section>
          </div>
        )}

        {/* Vista: MOVIMIENTOS */}
        {activeTab === 'movements' && (
          <div className="space-y-6">
            <div className="flex flex-wrap items-center justify-between gap-4">
              <h1 className="text-xl font-bold">Movimientos</h1>
              <button
                type="button"
                onClick={handleExportCsv}
                className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-medium rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] bg-white dark:bg-[#151E17] hover:bg-[#F0F4F0] dark:hover:bg-[#1D281F] cursor-pointer"
              >
                <Download className="w-4 h-4" />
                <span>Exportar CSV</span>
              </button>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
              <div className="relative">
                <Search className="w-4 h-4 absolute left-3 top-3 text-[#5E6B5F]" />
                <input
                  type="text"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="Buscar por descripción o categoría"
                  className="w-full pl-9 pr-3 py-2 text-sm rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] bg-white dark:bg-[#151E17]"
                />
              </div>
              <div className="inline-flex items-center gap-1 p-1 rounded-lg bg-[#E9EFEA] dark:bg-[#1A241C]">
                {(['ALL', 'INCOME', 'EXPENSE'] as const).map((t) => (
                  <button
                    key={t}
                    type="button"
                    onClick={() => setTypeFilter(t)}
                    className={`flex-1 px-3 py-1.5 text-xs font-medium rounded-md cursor-pointer ${
                      typeFilter === t
                        ? 'bg-white dark:bg-[#253328] text-[#171D18] dark:text-white'
                        : 'text-[#424D43] dark:text-[#B8C4B9]'
                    }`}
                  >
                    {t === 'ALL' ? 'Todos' : t === 'INCOME' ? 'Ingresos' : 'Gastos'}
                  </button>
                ))}
              </div>
              <select
                value={categoryFilter}
                onChange={(e) => setCategoryFilter(e.target.value)}
                className="px-3 py-2 text-sm rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] bg-white dark:bg-[#151E17]"
              >
                <option value="ALL">Todas las categorías</option>
                {Array.from(new Set(state.transactions.map((t) => t.category))).map((cat) => (
                  <option key={cat} value={cat}>
                    {cat}
                  </option>
                ))}
              </select>
            </div>

            {expenseBreakdown.length > 0 && (
              <div className="rounded-xl border border-[#D5DDD6] dark:border-[#253328] bg-white dark:bg-[#151E17] p-5 space-y-3">
                <h2 className="text-sm font-semibold">
                  Resumen de gastos por categoría ({PERIOD_LABELS[state.selectedPeriod]})
                </h2>
                <div className="space-y-2.5">
                  {expenseBreakdown.map((item) => (
                    <div key={item.category}>
                      <div className="flex justify-between text-xs mb-1">
                        <span>
                          {item.category} · {item.pct}%
                        </span>
                        <span className="font-money font-medium">
                          {formatMxnFromCentavos(item.amountCentavos)}
                        </span>
                      </div>
                      <div className="w-full h-2 rounded-full bg-[#E6ECE7] dark:bg-[#233025]">
                        <div
                          className="h-full rounded-full bg-[#1B5E20] dark:bg-[#66BB6A]"
                          style={{ width: `${item.pct}%` }}
                        />
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            <div className="rounded-xl border border-[#D5DDD6] dark:border-[#253328] bg-white dark:bg-[#151E17] overflow-hidden">
              {filteredTransactions.length === 0 ? (
                <div className="p-8 text-center space-y-3">
                  <p className="text-sm text-[#424D43] dark:text-[#B8C4B9]">
                    No hay movimientos registrados para mostrar.
                  </p>
                  <button
                    type="button"
                    onClick={() => {
                      setEditingTx(null);
                      setMovementModalType('EXPENSE');
                      setMovementModalOpen(true);
                    }}
                    className="px-4 py-2 rounded-lg bg-[#1B5E20] text-white text-sm font-medium cursor-pointer"
                  >
                    Registrar primer movimiento
                  </button>
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead className="bg-[#F2F6F3] dark:bg-[#1A241C] text-xs text-[#424D43] dark:text-[#B8C4B9] text-left">
                      <tr>
                        <th className="px-4 py-3 font-medium">Fecha</th>
                        <th className="px-4 py-3 font-medium">Descripción y categoría</th>
                        <th className="px-4 py-3 font-medium">Medio</th>
                        <th className="px-4 py-3 font-medium text-right">Importe</th>
                        <th className="px-4 py-3 font-medium text-right">Acciones</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-[#EEF2EE] dark:divide-[#222E24]">
                      {filteredTransactions.map((tx) => {
                        const positive =
                          tx.type === 'INCOME' ||
                          tx.type === 'SAVINGS_WITHDRAWAL' ||
                          (tx.type === 'BALANCE_ADJUSTMENT' && tx.isAdjustmentPositive);
                        const editable = tx.type === 'INCOME' || tx.type === 'EXPENSE';
                        return (
                          <tr key={tx.id} className="hover:bg-[#F8FAF8] dark:hover:bg-[#19231B]">
                            <td className="px-4 py-3 font-money text-xs whitespace-nowrap">{tx.date}</td>
                            <td className="px-4 py-3">
                              <p className="font-medium">{tx.description || tx.category}</p>
                              <p className="text-xs text-[#5E6B5F] dark:text-[#96A598]">
                                {MOVEMENT_LABELS[tx.type]} · {tx.category}
                              </p>
                            </td>
                            <td className="px-4 py-3 text-xs text-[#5E6B5F] dark:text-[#96A598]">
                              {PAYMENT_METHOD_LABELS[tx.paymentMethod]}
                            </td>
                            <td
                              className={`px-4 py-3 text-right font-money font-semibold whitespace-nowrap ${
                                positive ? 'text-[#1B5E20] dark:text-[#66BB6A]' : ''
                              }`}
                            >
                              {positive ? '+' : '-'}
                              {formatMxnFromCentavos(tx.amountCentavos)}
                            </td>
                            <td className="px-4 py-3 text-right whitespace-nowrap">
                              {editable && (
                                <button
                                  type="button"
                                  onClick={() => {
                                    setEditingTx(tx);
                                    setMovementModalType(tx.type === 'INCOME' ? 'INCOME' : 'EXPENSE');
                                    setMovementModalOpen(true);
                                  }}
                                  className="p-1.5 text-[#424D43] hover:text-[#1B5E20] dark:text-[#B8C4B9] cursor-pointer"
                                  title="Editar"
                                >
                                  <Pencil className="w-4 h-4" />
                                </button>
                              )}
                              <button
                                type="button"
                                onClick={() => handleDeleteTx(tx.id)}
                                className="p-1.5 text-[#424D43] hover:text-[#C62828] dark:text-[#B8C4B9] cursor-pointer"
                                title="Eliminar"
                              >
                                <Trash2 className="w-4 h-4" />
                              </button>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>
        )}

        {/* Vista: PRESUPUESTO Y CALENDARIO */}
        {activeTab === 'budget' && (
          <div className="space-y-8">
            <section className="space-y-4">
              <div className="flex items-center justify-between">
                <h1 className="text-xl font-bold">Presupuestos</h1>
                <button
                  type="button"
                  onClick={() => setBudgetModalOpen(true)}
                  className="px-3.5 py-2 text-xs font-medium rounded-lg bg-[#1B5E20] text-white cursor-pointer"
                >
                  Nuevo presupuesto
                </button>
              </div>

              {budgetProgressList.length === 0 ? (
                <div className="rounded-xl border border-[#D5DDD6] dark:border-[#253328] bg-white dark:bg-[#151E17] p-6 text-sm text-[#424D43] dark:text-[#B8C4B9]">
                  Crea presupuestos semanales, quincenales o mensuales con alertas configurables al 50%, 75%, 90% y 100%.
                </div>
              ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {budgetProgressList.map(({ budget, spent, remaining, pct, triggered }) => (
                    <div
                      key={budget.id}
                      className="rounded-xl border border-[#D5DDD6] dark:border-[#253328] bg-white dark:bg-[#151E17] p-5 space-y-3"
                    >
                      <div className="flex items-start justify-between">
                        <div>
                          <h3 className="font-semibold">
                            {budget.category} · {PERIOD_LABELS[budget.periodType]}
                          </h3>
                          <p className="text-xs text-[#5E6B5F] dark:text-[#96A598]">
                            Alertas: {budget.thresholds.join('%, ')}%
                          </p>
                        </div>
                        <button
                          type="button"
                          onClick={() =>
                            setState((prev) => ({
                              ...prev,
                              budgets: prev.budgets.filter((b) => b.id !== budget.id),
                            }))
                          }
                          className="p-1 text-[#5E6B5F] hover:text-[#C62828] cursor-pointer"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                      <div className="flex justify-between text-xs">
                        <span>{pct}% consumido</span>
                        <span className="font-money font-medium">
                          {formatMxnFromCentavos(spent)} / {formatMxnFromCentavos(budget.limitCentavos)}
                        </span>
                      </div>
                      <div className="w-full h-2.5 rounded-full bg-[#E6ECE7] dark:bg-[#233025] overflow-hidden">
                        <div
                          className={`h-full ${pct >= 90 ? 'bg-[#C62828]' : 'bg-[#4CAF50]'}`}
                          style={{ width: `${Math.min(100, pct)}%` }}
                        />
                      </div>
                      <p className="text-xs text-[#424D43] dark:text-[#B8C4B9]">
                        {remaining >= 0
                          ? `Restante: ${formatMxnFromCentavos(remaining)}`
                          : `Excedido por ${formatMxnFromCentavos(Math.abs(remaining))}`}
                      </p>
                      {triggered !== null && (
                        <p
                          className={`text-xs font-semibold ${
                            triggered >= 90 ? 'text-[#C62828] dark:text-[#FF8A80]' : 'text-[#1B5E20] dark:text-[#66BB6A]'
                          }`}
                        >
                          Alerta activa: alcanzaste el {triggered}% del presupuesto.
                        </p>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </section>

            <section className="space-y-4">
              <div className="flex items-center justify-between">
                <h2 className="text-xl font-bold">Calendario de pagos</h2>
                <button
                  type="button"
                  onClick={() => setPaymentModalOpen(true)}
                  className="px-3.5 py-2 text-xs font-medium rounded-lg border border-[#1B5E20] text-[#1B5E20] dark:border-[#66BB6A] dark:text-[#66BB6A] cursor-pointer"
                >
                  Agendar pago
                </button>
              </div>

              {state.scheduledPayments.length === 0 ? (
                <div className="rounded-xl border border-[#D5DDD6] dark:border-[#253328] bg-white dark:bg-[#151E17] p-6 text-sm text-[#424D43] dark:text-[#B8C4B9]">
                  Registra renta, servicios, tarjetas o colegiaturas. Ningún pago se descuenta automáticamente por llegar su fecha; tú decides cuándo marcarlo como pagado.
                </div>
              ) : (
                <div className="space-y-3">
                  {state.scheduledPayments.map((p) => {
                    const status = resolveEffectivePaymentStatus(p, today);
                    return (
                      <div
                        key={p.id}
                        className="rounded-xl border border-[#D5DDD6] dark:border-[#253328] bg-white dark:bg-[#151E17] p-4 flex flex-wrap items-center justify-between gap-4"
                      >
                        <div>
                          <p className="font-semibold">{p.description}</p>
                          <p
                            className={`text-xs ${
                              status === 'OVERDUE'
                                ? 'text-[#C62828] dark:text-[#FF8A80] font-medium'
                                : status === 'PAID'
                                ? 'text-[#1B5E20] dark:text-[#66BB6A] font-medium'
                                : 'text-[#5E6B5F] dark:text-[#96A598]'
                            }`}
                          >
                            {p.category} · Vence {p.dueDate} ·{' '}
                            {status === 'PAID' ? 'Pagado' : status === 'OVERDUE' ? 'Vencido' : 'Pendiente'}
                          </p>
                        </div>
                        <div className="flex items-center gap-3">
                          <span className="font-money font-bold">
                            {formatMxnFromCentavos(p.amountCentavos)}
                          </span>
                          {status !== 'PAID' && (
                            <button
                              type="button"
                              onClick={() => handleMarkPaymentPaid(p)}
                              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg bg-[#1B5E20] text-white cursor-pointer"
                            >
                              <CheckCircle2 className="w-3.5 h-3.5" />
                              <span>Marcar pagado</span>
                            </button>
                          )}
                          <button
                            type="button"
                            onClick={() =>
                              setState((prev) => ({
                                ...prev,
                                scheduledPayments: prev.scheduledPayments.filter((x) => x.id !== p.id),
                              }))
                            }
                            className="p-1.5 text-[#5E6B5F] hover:text-[#C62828] cursor-pointer"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </section>
          </div>
        )}

        {/* Vista: AHORRO Y DEUDAS */}
        {activeTab === 'savings' && (
          <div className="space-y-8">
            <section className="space-y-4">
              <div className="flex items-center justify-between">
                <h1 className="text-xl font-bold">Metas de ahorro</h1>
                <button
                  type="button"
                  onClick={() => setGoalModalOpen(true)}
                  className="px-3.5 py-2 text-xs font-medium rounded-lg bg-[#1B5E20] text-white cursor-pointer"
                >
                  Nueva meta
                </button>
              </div>
              {state.savingsGoals.length === 0 ? (
                <div className="rounded-xl border border-[#D5DDD6] dark:border-[#253328] bg-white dark:bg-[#151E17] p-6 text-sm text-[#424D43] dark:text-[#B8C4B9]">
                  Crea metas para apartar recursos de tu saldo disponible sin duplicar gastos.
                </div>
              ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {state.savingsGoals.map((g) => {
                    const pct =
                      g.targetCentavos > 0
                        ? Math.min(100, Math.round((g.savedCentavos / g.targetCentavos) * 100))
                        : 0;
                    const rem = Math.max(0, g.targetCentavos - g.savedCentavos);
                    return (
                      <div
                        key={g.id}
                        className="rounded-xl border border-[#D5DDD6] dark:border-[#253328] bg-white dark:bg-[#151E17] p-5 space-y-3"
                      >
                        <div className="flex justify-between">
                          <div>
                            <h3 className="font-semibold">{g.name}</h3>
                            <p className="text-xs text-[#5E6B5F] dark:text-[#96A598]">
                              {g.description || 'Sin descripción'}
                              {g.targetDate ? ` · Meta: ${g.targetDate}` : ''}
                            </p>
                          </div>
                          <button
                            type="button"
                            onClick={() =>
                              setState((prev) => ({
                                ...prev,
                                savingsGoals: prev.savingsGoals.filter((x) => x.id !== g.id),
                              }))
                            }
                            className="p-1 text-[#5E6B5F] hover:text-[#C62828] cursor-pointer"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                        <div className="flex justify-between text-xs">
                          <span>{pct}% completado</span>
                          <span className="font-money font-medium">
                            {formatMxnFromCentavos(g.savedCentavos)} / {formatMxnFromCentavos(g.targetCentavos)}
                          </span>
                        </div>
                        <div className="w-full h-2.5 rounded-full bg-[#E6ECE7] dark:bg-[#233025] overflow-hidden">
                          <div className="h-full bg-[#4CAF50]" style={{ width: `${pct}%` }} />
                        </div>
                        <p className="text-xs text-[#424D43] dark:text-[#B8C4B9]">
                          Faltante: {formatMxnFromCentavos(rem)}
                        </p>
                        <div className="flex gap-2">
                          <button
                            type="button"
                            onClick={() => setGoalMoveModal({ goal: g, isContribution: true })}
                            className="flex-1 py-2 text-xs font-medium rounded-lg bg-[#1B5E20] text-white cursor-pointer"
                          >
                            Aportar
                          </button>
                          <button
                            type="button"
                            disabled={g.savedCentavos <= 0}
                            onClick={() => setGoalMoveModal({ goal: g, isContribution: false })}
                            className="flex-1 py-2 text-xs font-medium rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] disabled:opacity-40 cursor-pointer"
                          >
                            Retirar
                          </button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </section>

            <section className="space-y-4">
              <div className="flex items-center justify-between">
                <h2 className="text-xl font-bold">Deudas y préstamos</h2>
                <button
                  type="button"
                  onClick={() => setDebtModalOpen(true)}
                  className="px-3.5 py-2 text-xs font-medium rounded-lg border border-[#1B5E20] text-[#1B5E20] dark:border-[#66BB6A] dark:text-[#66BB6A] cursor-pointer"
                >
                  Nueva deuda
                </button>
              </div>
              {state.debts.length === 0 ? (
                <div className="rounded-xl border border-[#D5DDD6] dark:border-[#253328] bg-white dark:bg-[#151E17] p-6 text-sm text-[#424D43] dark:text-[#B8C4B9]">
                  Registra tus créditos o préstamos para llevar control de abonos y estimar en cuántos meses puedes liquidarlos.
                </div>
              ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {state.debts.map((d) => (
                    <div
                      key={d.id}
                      className="rounded-xl border border-[#D5DDD6] dark:border-[#253328] bg-white dark:bg-[#151E17] p-5 space-y-3"
                    >
                      <div className="flex justify-between">
                        <div>
                          <h3 className="font-semibold">{d.creditorName}</h3>
                          <p className="text-xs text-[#5E6B5F] dark:text-[#96A598]">
                            Pendiente: <span className="font-money font-semibold">{formatMxnFromCentavos(d.remainingBalanceCentavos)}</span> · Tasa anual {d.annualInterestRatePercent}%
                          </p>
                        </div>
                        <button
                          type="button"
                          onClick={() =>
                            setState((prev) => ({
                              ...prev,
                              debts: prev.debts.filter((x) => x.id !== d.id),
                            }))
                          }
                          className="p-1 text-[#5E6B5F] hover:text-[#C62828] cursor-pointer"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                      <p className="text-xs text-[#424D43] dark:text-[#B8C4B9]">
                        Pago mínimo: {formatMxnFromCentavos(d.minimumPaymentCentavos)} · Abonos registrados: {d.payments.length}
                      </p>
                      <div className="flex gap-2">
                        <button
                          type="button"
                          disabled={d.remainingBalanceCentavos <= 0}
                          onClick={() => setDebtPayModal(d)}
                          className="flex-1 py-2 text-xs font-medium rounded-lg bg-[#1B5E20] text-white disabled:opacity-40 cursor-pointer"
                        >
                          Registrar abono
                        </button>
                        <button
                          type="button"
                          onClick={() => setDebtSimModal(d)}
                          className="flex-1 py-2 text-xs font-medium rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] cursor-pointer"
                        >
                          Simular liquidación
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </section>
          </div>
        )}

        {/* Vista: PERFIL Y CONFIGURACIÓN */}
        {activeTab === 'profile' && (
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <div className="rounded-xl border border-[#D5DDD6] dark:border-[#253328] bg-white dark:bg-[#151E17] p-6 space-y-4">
              <h1 className="text-lg font-bold">Control y seguridad financiera</h1>
              <div className="flex items-center justify-between py-2 border-b border-[#EEF2EE] dark:border-[#222E24]">
                <div>
                  <p className="text-sm font-medium">Saldo disponible actual</p>
                  <p className="font-money text-base font-semibold">
                    {formatMxnFromCentavos(availableBalance)}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => setAdjustBalanceModalOpen(true)}
                  className="px-3.5 py-2 text-xs font-medium rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] cursor-pointer"
                >
                  Ajustar con registro
                </button>
              </div>

              <div className="space-y-2 text-sm text-[#424D43] dark:text-[#B8C4B9]">
                <div className="flex items-center gap-2 text-[#1B5E20] dark:text-[#66BB6A] font-medium">
                  <ShieldCheck className="w-4 h-4" />
                  <span>Privacidad local verificable</span>
                </div>
                <p>
                  En esta vista web los datos se guardan exclusivamente en el almacenamiento local de tu navegador (`localStorage`). En la aplicación nativa Android se almacenan localmente en Room SQLite con `allowBackup=false`, bloqueo biométrico opcional y llaves auxiliares en Android Keystore.
                </p>
              </div>

              <div className="flex flex-wrap gap-3 pt-2">
                <button
                  type="button"
                  onClick={handleLoadDemo}
                  className="px-4 py-2 text-xs font-medium rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] cursor-pointer"
                >
                  Cargar datos demo
                </button>
                <button
                  type="button"
                  onClick={() => setConfirmWipeOpen(true)}
                  className="px-4 py-2 text-xs font-medium rounded-lg bg-[#C62828] text-white cursor-pointer"
                >
                  Borrar todos los datos
                </button>
              </div>
            </div>

            <div className="rounded-xl border border-[#D5DDD6] dark:border-[#253328] bg-white dark:bg-[#151E17] p-6 space-y-4">
              <div className="flex items-center gap-4">
                <img
                  src="/assets/aistudio/a_clean_high_resolution_cute_3d_cartoon_illustra_dd88b30b.png"
                  alt="Icono de Calculadora de Gastos"
                  referrerPolicy="no-referrer"
                  className="w-14 h-14 rounded-2xl object-contain bg-white border border-[#D5DDD6] dark:border-[#2D3A2F] p-1 shrink-0"
                />
                <div>
                  <h2 className="text-lg font-bold">Preferencias y respaldo</h2>
                  <p className="text-xs text-[#5E6B5F] dark:text-[#96A598]">
                    Calculadora de Gastos · Moneda MXN
                  </p>
                </div>
              </div>

              <div className="flex items-center justify-between py-2 border-b border-[#EEF2EE] dark:border-[#222E24]">
                <div>
                  <p className="text-sm font-medium">Apariencia</p>
                  <p className="text-xs text-[#5E6B5F] dark:text-[#96A598]">
                    {darkMode ? 'Tema oscuro activo' : 'Tema claro activo'}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => setDarkMode((d) => !d)}
                  className="px-3.5 py-2 text-xs font-medium rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] cursor-pointer"
                >
                  {darkMode ? 'Cambiar a claro' : 'Cambiar a oscuro'}
                </button>
              </div>

              <div className="flex items-center justify-between py-2 border-b border-[#EEF2EE] dark:border-[#222E24]">
                <div>
                  <p className="text-sm font-medium">Respaldo de movimientos</p>
                  <p className="text-xs text-[#5E6B5F] dark:text-[#96A598]">
                    Descarga tus registros en formato CSV compatible con hojas de cálculo
                  </p>
                </div>
                <button
                  type="button"
                  onClick={handleExportCsv}
                  className="px-3.5 py-2 text-xs font-medium rounded-lg bg-[#1B5E20] text-white cursor-pointer whitespace-nowrap"
                >
                  Exportar CSV
                </button>
              </div>

              <p className="text-xs text-[#5E6B5F] dark:text-[#96A598]">
                Tus registros funcionan sin conexión y no requieren cuenta bancaria ni correo electrónico.
              </p>
            </div>
          </div>
        )}
      </main>

      {/* Modal: Registrar / Editar movimiento */}
      {movementModalOpen && (
        <MovementModal
          initialType={movementModalType}
          editing={editingTx}
          onSave={handleSaveMovement}
          onClose={() => {
            setMovementModalOpen(false);
            setEditingTx(null);
          }}
        />
      )}

      {/* Modal: Nuevo presupuesto */}
      {budgetModalOpen && (
        <BudgetModal
          onSave={(b) => {
            setState((prev) => ({
              ...prev,
              budgets: [...prev.budgets.filter((x) => !(x.category === b.category && x.periodType === b.periodType)), b],
            }));
            setBudgetModalOpen(false);
            setToastMessage('Presupuesto guardado.');
          }}
          onClose={() => setBudgetModalOpen(false)}
        />
      )}

      {/* Modal: Agendar pago */}
      {paymentModalOpen && (
        <ScheduledPaymentModal
          onSave={(p) => {
            setState((prev) => ({ ...prev, scheduledPayments: [...prev.scheduledPayments, p] }));
            setPaymentModalOpen(false);
            setToastMessage('Pago programado guardado.');
          }}
          onClose={() => setPaymentModalOpen(false)}
        />
      )}

      {/* Modal: Nueva meta de ahorro */}
      {goalModalOpen && (
        <GoalModal
          onSave={(g) => {
            setState((prev) => ({ ...prev, savingsGoals: [...prev.savingsGoals, g] }));
            setGoalModalOpen(false);
            setToastMessage('Meta de ahorro creada.');
          }}
          onClose={() => setGoalModalOpen(false)}
        />
      )}

      {/* Modal: Aportar / Retirar meta */}
      {goalMoveModal && (
        <SimpleAmountModal
          title={
            goalMoveModal.isContribution
              ? `Aportar a ${goalMoveModal.goal.name}`
              : `Retirar de ${goalMoveModal.goal.name}`
          }
          onConfirm={(amountCentavos, note) => {
            if (!goalMoveModal.isContribution && amountCentavos > goalMoveModal.goal.savedCentavos) {
              return 'El retiro excede el ahorro acumulado en esta meta.';
            }
            const tx: FinancialTransaction = {
              id: crypto.randomUUID(),
              type: goalMoveModal.isContribution ? 'SAVINGS_CONTRIBUTION' : 'SAVINGS_WITHDRAWAL',
              amountCentavos,
              category: 'Ahorro',
              date: today,
              description: note ? `${goalMoveModal.goal.name}: ${note}` : goalMoveModal.goal.name,
              recurrence: 'NONE',
              paymentMethod: 'TRANSFER',
              isAdjustmentPositive: !goalMoveModal.isContribution,
            };
            setState((prev) => ({
              ...prev,
              savingsGoals: prev.savingsGoals.map((g) =>
                g.id === goalMoveModal.goal.id
                  ? {
                      ...g,
                      savedCentavos: goalMoveModal.isContribution
                        ? g.savedCentavos + amountCentavos
                        : g.savedCentavos - amountCentavos,
                    }
                  : g
              ),
              transactions: [tx, ...prev.transactions],
            }));
            setGoalMoveModal(null);
            setToastMessage(goalMoveModal.isContribution ? 'Aportación registrada.' : 'Retiro registrado.');
            return null;
          }}
          onClose={() => setGoalMoveModal(null)}
        />
      )}

      {/* Modal: Nueva deuda */}
      {debtModalOpen && (
        <DebtModal
          onSave={(d) => {
            setState((prev) => ({ ...prev, debts: [...prev.debts, d] }));
            setDebtModalOpen(false);
            setToastMessage('Deuda registrada.');
          }}
          onClose={() => setDebtModalOpen(false)}
        />
      )}

      {/* Modal: Abono a deuda */}
      {debtPayModal && (
        <SimpleAmountModal
          title={`Abonar a ${debtPayModal.creditorName}`}
          onConfirm={(amountCentavos, note) => {
            const applied = Math.min(amountCentavos, debtPayModal.remainingBalanceCentavos);
            const tx: FinancialTransaction = {
              id: crypto.randomUUID(),
              type: 'DEBT_PAYMENT',
              amountCentavos: applied,
              category: 'Deudas',
              date: today,
              description: note ? `Abono ${debtPayModal.creditorName}: ${note}` : `Abono a ${debtPayModal.creditorName}`,
              recurrence: 'NONE',
              paymentMethod: 'TRANSFER',
              isAdjustmentPositive: false,
            };
            setState((prev) => ({
              ...prev,
              debts: prev.debts.map((d) =>
                d.id === debtPayModal.id
                  ? {
                      ...d,
                      remainingBalanceCentavos: d.remainingBalanceCentavos - applied,
                      payments: [{ id: crypto.randomUUID(), amountCentavos: applied, date: today, note }, ...d.payments],
                    }
                  : d
              ),
              transactions: [tx, ...prev.transactions],
            }));
            setDebtPayModal(null);
            setToastMessage('Abono aplicado.');
            return null;
          }}
          onClose={() => setDebtPayModal(null)}
        />
      )}

      {/* Modal: Simulador de deuda */}
      {debtSimModal && (
        <DebtSimulatorModal debt={debtSimModal} onClose={() => setDebtSimModal(null)} />
      )}

      {/* Modal: Ajuste auditado de saldo */}
      {adjustBalanceModalOpen && (
        <SimpleAmountModal
          title="Ajustar saldo disponible (con registro)"
          allowZero
          onConfirm={(targetCentavos, note) => {
            const diff = targetCentavos - availableBalance;
            if (diff === 0) return 'El saldo ingresado ya coincide con tu saldo actual.';
            const tx: FinancialTransaction = {
              id: crypto.randomUUID(),
              type: 'BALANCE_ADJUSTMENT',
              amountCentavos: Math.abs(diff),
              category: 'Ajuste de saldo',
              date: today,
              description: note || 'Ajuste manual auditado de saldo',
              recurrence: 'NONE',
              paymentMethod: 'OTHER',
              isAdjustmentPositive: diff > 0,
            };
            setState((prev) => ({ ...prev, transactions: [tx, ...prev.transactions] }));
            setAdjustBalanceModalOpen(false);
            setToastMessage('Ajuste registrado en movimientos.');
            return null;
          }}
          onClose={() => setAdjustBalanceModalOpen(false)}
        />
      )}

      {/* Modal: Confirmar borrado total */}
      {confirmWipeOpen && (
        <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4">
          <div className="w-full max-w-md rounded-2xl bg-white dark:bg-[#151E17] border border-[#D5DDD6] dark:border-[#2D3A2F] p-6 space-y-4">
            <h3 className="text-lg font-bold">Eliminar todos los datos locales</h3>
            <p className="text-sm text-[#424D43] dark:text-[#B8C4B9]">
              Esta acción eliminará todos tus ingresos, gastos, presupuestos, pagos, metas y deudas guardados localmente.
            </p>
            <div className="flex justify-end gap-3">
              <button
                type="button"
                onClick={() => setConfirmWipeOpen(false)}
                className="px-4 py-2 text-sm rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] cursor-pointer"
              >
                Cancelar
              </button>
              <button
                type="button"
                onClick={handleWipeAll}
                className="px-4 py-2 text-sm font-medium rounded-lg bg-[#C62828] text-white cursor-pointer"
              >
                Borrar todo
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Modal: Arquitectura Android y estado real de compilación */}
      {showAndroidModal && (
        <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4">
          <div className="w-full max-w-2xl max-h-[85vh] overflow-y-auto rounded-2xl bg-white dark:bg-[#151E17] border border-[#D5DDD6] dark:border-[#2D3A2F] p-6 space-y-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <img
                  src="/assets/aistudio/a_clean_high_resolution_cute_3d_cartoon_illustra_dd88b30b.png"
                  alt="Icono de Calculadora de Gastos"
                  referrerPolicy="no-referrer"
                  className="w-11 h-11 rounded-xl object-contain bg-white border border-[#D5DDD6] dark:border-[#2D3A2F] p-0.5 shrink-0"
                />
                <h3 className="text-lg font-bold">Proyecto Android Nativo: Calculadora de Gastos</h3>
              </div>
              <button
                type="button"
                onClick={() => setShowAndroidModal(false)}
                className="text-sm font-medium text-[#424D43] dark:text-[#B8C4B9] cursor-pointer"
              >
                Cerrar
              </button>
            </div>
            <p className="text-sm text-[#424D43] dark:text-[#B8C4B9]">
              El proyecto nativo completo está generado en la raíz del repositorio (`app/`, `gradle/`, `.github/workflows/android.yml`). Nota transparente: este entorno de vista previa web solo cuenta con Node.js/Vite y no dispone de JDK ni Android SDK instalados, por lo que la compilación Gradle (`./gradlew testDebugUnitTest`, `lintDebug`, `assembleDebug`, `bundleRelease`) debe ejecutarse en Android Studio o mediante el workflow de GitHub Actions incluido.
            </p>
            <div className="divide-y divide-[#EEF2EE] dark:divide-[#222E24] border border-[#D5DDD6] dark:border-[#2D3A2F] rounded-xl">
              {ANDROID_FILES_SUMMARY.map((f) => (
                <div key={f.path} className="p-3">
                  <p className="font-money text-xs font-semibold text-[#1B5E20] dark:text-[#66BB6A]">
                    {f.path}
                  </p>
                  <p className="text-xs text-[#424D43] dark:text-[#B8C4B9] mt-0.5">{f.role}</p>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function MovementModal({
  initialType,
  editing,
  onSave,
  onClose,
}: {
  initialType: 'INCOME' | 'EXPENSE';
  editing: FinancialTransaction | null;
  onSave: (p: {
    type: 'INCOME' | 'EXPENSE';
    amountCentavos: number;
    category: string;
    date: string;
    description: string;
    recurrence: RecurrenceFrequency;
    paymentMethod: PaymentMethod;
  }) => void;
  onClose: () => void;
}) {
  const [type, setType] = useState<'INCOME' | 'EXPENSE'>(
    editing?.type === 'INCOME' ? 'INCOME' : editing?.type === 'EXPENSE' ? 'EXPENSE' : initialType
  );
  const categories = type === 'INCOME' ? INCOME_CATEGORIES : EXPENSE_CATEGORIES;
  const [amount, setAmount] = useState(editing ? (editing.amountCentavos / 100).toFixed(2) : '');
  const [category, setCategory] = useState<string>(
    editing && (categories as readonly string[]).includes(editing.category) ? editing.category : categories[0]
  );
  const [date, setDate] = useState(editing?.date || todayIso());
  const [description, setDescription] = useState(editing?.description || '');
  const [recurrence, setRecurrence] = useState<RecurrenceFrequency>(editing?.recurrence || 'NONE');
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>(editing?.paymentMethod || 'CASH');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!(categories as readonly string[]).includes(category)) {
      setCategory(categories[0]);
    }
  }, [type, categories, category]);

  return (
    <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4">
      <div className="w-full max-w-md rounded-2xl bg-white dark:bg-[#151E17] border border-[#D5DDD6] dark:border-[#2D3A2F] p-6 space-y-4">
        <h3 className="text-lg font-bold">{editing ? 'Editar movimiento' : 'Registrar movimiento'}</h3>
        <div className="grid grid-cols-2 gap-2 p-1 rounded-lg bg-[#E9EFEA] dark:bg-[#1A241C]">
          {(['EXPENSE', 'INCOME'] as const).map((t) => (
            <button
              key={t}
              type="button"
              onClick={() => setType(t)}
              className={`py-2 text-xs font-medium rounded-md cursor-pointer ${
                type === t ? 'bg-white dark:bg-[#253328] font-semibold' : ''
              }`}
            >
              {t === 'EXPENSE' ? 'Gasto' : 'Ingreso'}
            </button>
          ))}
        </div>

        <div className="space-y-3">
          <div>
            <label className="block text-xs font-medium mb-1">Importe en MXN</label>
            <input
              type="text"
              inputMode="decimal"
              value={amount}
              onChange={(e) => {
                setAmount(e.target.value);
                setError(null);
              }}
              placeholder="0.00"
              className="w-full px-3 py-2 text-sm rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] bg-white dark:bg-[#0E1510] font-money"
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium mb-1">Categoría</label>
              <select
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                className="w-full px-3 py-2 text-sm rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] bg-white dark:bg-[#0E1510]"
              >
                {categories.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-xs font-medium mb-1">Fecha</label>
              <input
                type="date"
                value={date}
                onChange={(e) => setDate(e.target.value)}
                className="w-full px-3 py-2 text-sm rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] bg-white dark:bg-[#0E1510]"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-medium mb-1">Descripción (opcional)</label>
            <input
              type="text"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Ej. Supermercado, quincena, transporte"
              className="w-full px-3 py-2 text-sm rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] bg-white dark:bg-[#0E1510]"
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium mb-1">Frecuencia</label>
              <select
                value={recurrence}
                onChange={(e) => setRecurrence(e.target.value as RecurrenceFrequency)}
                className="w-full px-3 py-2 text-sm rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] bg-white dark:bg-[#0E1510]"
              >
                {(Object.keys(RECURRENCE_LABELS) as RecurrenceFrequency[]).map((k) => (
                  <option key={k} value={k}>
                    {RECURRENCE_LABELS[k]}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-xs font-medium mb-1">Medio de pago</label>
              <select
                value={paymentMethod}
                onChange={(e) => setPaymentMethod(e.target.value as PaymentMethod)}
                className="w-full px-3 py-2 text-sm rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] bg-white dark:bg-[#0E1510]"
              >
                {(Object.keys(PAYMENT_METHOD_LABELS) as PaymentMethod[]).map((k) => (
                  <option key={k} value={k}>
                    {PAYMENT_METHOD_LABELS[k]}
                  </option>
                ))}
              </select>
            </div>
          </div>
        </div>

        {error && <p className="text-xs text-[#C62828] dark:text-[#FF8A80]">{error}</p>}

        <div className="flex justify-end gap-3 pt-2">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 text-sm rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] cursor-pointer"
          >
            Cancelar
          </button>
          <button
            type="button"
            onClick={() => {
              const centavos = parseMxnToCentavos(amount);
              if (centavos === null) {
                setError('Ingresa un importe válido mayor a $0.00 MXN.');
                return;
              }
              if (!date) {
                setError('Selecciona una fecha válida.');
                return;
              }
              onSave({ type, amountCentavos: centavos, category, date, description, recurrence, paymentMethod });
            }}
            className="px-4 py-2 text-sm font-medium rounded-lg bg-[#1B5E20] text-white cursor-pointer"
          >
            Guardar
          </button>
        </div>
      </div>
    </div>
  );
}

function BudgetModal({ onSave, onClose }: { onSave: (b: Budget) => void; onClose: () => void }) {
  const [category, setCategory] = useState<string>('General');
  const [periodType, setPeriodType] = useState<PeriodType>('BIWEEKLY');
  const [limit, setLimit] = useState('');
  const [thresholds, setThresholds] = useState('50,75,90,100');
  const [err, setErr] = useState<string | null>(null);

  return (
    <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4">
      <div className="w-full max-w-md rounded-2xl bg-white dark:bg-[#151E17] border border-[#D5DDD6] dark:border-[#2D3A2F] p-6 space-y-3">
        <h3 className="text-lg font-bold">Nuevo presupuesto</h3>
        <select
          value={category}
          onChange={(e) => setCategory(e.target.value)}
          className="w-full px-3 py-2 text-sm rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] bg-white dark:bg-[#0E1510]"
        >
          <option value="General">General</option>
          {EXPENSE_CATEGORIES.map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </select>
        <select
          value={periodType}
          onChange={(e) => setPeriodType(e.target.value as PeriodType)}
          className="w-full px-3 py-2 text-sm rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] bg-white dark:bg-[#0E1510]"
        >
          <option value="WEEKLY">Semanal</option>
          <option value="BIWEEKLY">Quincenal</option>
          <option value="MONTHLY">Mensual</option>
        </select>
        <input
          type="text"
          value={limit}
          onChange={(e) => setLimit(e.target.value)}
          placeholder="Límite en MXN (ej. 2500.00)"
          className="w-full px-3 py-2 text-sm rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] bg-white dark:bg-[#0E1510] font-money"
        />
        <input
          type="text"
          value={thresholds}
          onChange={(e) => setThresholds(e.target.value)}
          placeholder="Alertas % (50,75,90,100)"
          className="w-full px-3 py-2 text-sm rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] bg-white dark:bg-[#0E1510]"
        />
        {err && <p className="text-xs text-[#C62828]">{err}</p>}
        <div className="flex justify-end gap-2 pt-2">
          <button type="button" onClick={onClose} className="px-4 py-2 text-sm rounded-lg border cursor-pointer">
            Cancelar
          </button>
          <button
            type="button"
            onClick={() => {
              const centavos = parseMxnToCentavos(limit);
              if (centavos === null) {
                setErr('Límite inválido.');
                return;
              }
              const parsedThresholds = thresholds
                .split(',')
                .map((s) => Number(s.trim()))
                .filter((n) => Number.isInteger(n) && n >= 1 && n <= 100);
              onSave({
                id: crypto.randomUUID(),
                category,
                periodType,
                limitCentavos: centavos,
                thresholds: parsedThresholds.length ? parsedThresholds : [50, 75, 90, 100],
              });
            }}
            className="px-4 py-2 text-sm font-medium rounded-lg bg-[#1B5E20] text-white cursor-pointer"
          >
            Guardar
          </button>
        </div>
      </div>
    </div>
  );
}

function ScheduledPaymentModal({
  onSave,
  onClose,
}: {
  onSave: (p: ScheduledPayment) => void;
  onClose: () => void;
}) {
  const [desc, setDesc] = useState('');
  const [category, setCategory] = useState<string>(SCHEDULED_PAYMENT_CATEGORIES[0]);
  const [amount, setAmount] = useState('');
  const [dueDate, setDueDate] = useState(addDaysIso(todayIso(), 7));
  const [err, setErr] = useState<string | null>(null);

  return (
    <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4">
      <div className="w-full max-w-md rounded-2xl bg-white dark:bg-[#151E17] border border-[#D5DDD6] dark:border-[#2D3A2F] p-6 space-y-3">
        <h3 className="text-lg font-bold">Agendar compromiso de pago</h3>
        <input
          type="text"
          value={desc}
          onChange={(e) => setDesc(e.target.value)}
          placeholder="Descripción (ej. Renta, CFE, Telmex)"
          className="w-full px-3 py-2 text-sm rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] bg-white dark:bg-[#0E1510]"
        />
        <select
          value={category}
          onChange={(e) => setCategory(e.target.value)}
          className="w-full px-3 py-2 text-sm rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] bg-white dark:bg-[#0E1510]"
        >
          {SCHEDULED_PAYMENT_CATEGORIES.map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </select>
        <input
          type="text"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          placeholder="Importe MXN"
          className="w-full px-3 py-2 text-sm rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] bg-white dark:bg-[#0E1510] font-money"
        />
        <input
          type="date"
          value={dueDate}
          onChange={(e) => setDueDate(e.target.value)}
          className="w-full px-3 py-2 text-sm rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] bg-white dark:bg-[#0E1510]"
        />
        {err && <p className="text-xs text-[#C62828]">{err}</p>}
        <div className="flex justify-end gap-2 pt-2">
          <button type="button" onClick={onClose} className="px-4 py-2 text-sm rounded-lg border cursor-pointer">
            Cancelar
          </button>
          <button
            type="button"
            onClick={() => {
              const centavos = parseMxnToCentavos(amount);
              if (!desc.trim() || centavos === null || !dueDate) {
                setErr('Completa descripción, importe y fecha válidos.');
                return;
              }
              onSave({
                id: crypto.randomUUID(),
                description: desc.trim(),
                category,
                amountCentavos: centavos,
                dueDate,
                status: 'PENDING',
                recurrence: 'MONTHLY',
              });
            }}
            className="px-4 py-2 text-sm font-medium rounded-lg bg-[#1B5E20] text-white cursor-pointer"
          >
            Guardar
          </button>
        </div>
      </div>
    </div>
  );
}

function GoalModal({ onSave, onClose }: { onSave: (g: SavingsGoal) => void; onClose: () => void }) {
  const [name, setName] = useState('');
  const [target, setTarget] = useState('');
  const [targetDate, setTargetDate] = useState('');
  const [description, setDescription] = useState('');
  const [err, setErr] = useState<string | null>(null);

  return (
    <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4">
      <div className="w-full max-w-md rounded-2xl bg-white dark:bg-[#151E17] border border-[#D5DDD6] dark:border-[#2D3A2F] p-6 space-y-3">
        <h3 className="text-lg font-bold">Nueva meta de ahorro</h3>
        <input
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Nombre de la meta"
          className="w-full px-3 py-2 text-sm rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] bg-white dark:bg-[#0E1510]"
        />
        <input
          type="text"
          value={target}
          onChange={(e) => setTarget(e.target.value)}
          placeholder="Cantidad objetivo MXN"
          className="w-full px-3 py-2 text-sm rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] bg-white dark:bg-[#0E1510] font-money"
        />
        <input
          type="date"
          value={targetDate}
          onChange={(e) => setTargetDate(e.target.value)}
          className="w-full px-3 py-2 text-sm rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] bg-white dark:bg-[#0E1510]"
        />
        <input
          type="text"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="Descripción opcional"
          className="w-full px-3 py-2 text-sm rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] bg-white dark:bg-[#0E1510]"
        />
        {err && <p className="text-xs text-[#C62828]">{err}</p>}
        <div className="flex justify-end gap-2 pt-2">
          <button type="button" onClick={onClose} className="px-4 py-2 text-sm rounded-lg border cursor-pointer">
            Cancelar
          </button>
          <button
            type="button"
            onClick={() => {
              const centavos = parseMxnToCentavos(target);
              if (!name.trim() || centavos === null) {
                setErr('Ingresa nombre y monto objetivo válido.');
                return;
              }
              onSave({
                id: crypto.randomUUID(),
                name: name.trim(),
                targetCentavos: centavos,
                savedCentavos: 0,
                targetDate,
                description: description.trim(),
              });
            }}
            className="px-4 py-2 text-sm font-medium rounded-lg bg-[#1B5E20] text-white cursor-pointer"
          >
            Crear meta
          </button>
        </div>
      </div>
    </div>
  );
}

function DebtModal({ onSave, onClose }: { onSave: (d: Debt) => void; onClose: () => void }) {
  const [creditor, setCreditor] = useState('');
  const [balance, setBalance] = useState('');
  const [minPay, setMinPay] = useState('');
  const [rate, setRate] = useState('0');
  const [err, setErr] = useState<string | null>(null);

  return (
    <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4">
      <div className="w-full max-w-md rounded-2xl bg-white dark:bg-[#151E17] border border-[#D5DDD6] dark:border-[#2D3A2F] p-6 space-y-3">
        <h3 className="text-lg font-bold">Registrar deuda o préstamo</h3>
        <input
          type="text"
          value={creditor}
          onChange={(e) => setCreditor(e.target.value)}
          placeholder="Nombre del acreedor"
          className="w-full px-3 py-2 text-sm rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] bg-white dark:bg-[#0E1510]"
        />
        <input
          type="text"
          value={balance}
          onChange={(e) => setBalance(e.target.value)}
          placeholder="Saldo inicial MXN"
          className="w-full px-3 py-2 text-sm rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] bg-white dark:bg-[#0E1510] font-money"
        />
        <input
          type="text"
          value={minPay}
          onChange={(e) => setMinPay(e.target.value)}
          placeholder="Pago mínimo mensual MXN"
          className="w-full px-3 py-2 text-sm rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] bg-white dark:bg-[#0E1510] font-money"
        />
        <input
          type="text"
          value={rate}
          onChange={(e) => setRate(e.target.value)}
          placeholder="Tasa anual % (opcional)"
          className="w-full px-3 py-2 text-sm rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] bg-white dark:bg-[#0E1510] font-money"
        />
        {err && <p className="text-xs text-[#C62828]">{err}</p>}
        <div className="flex justify-end gap-2 pt-2">
          <button type="button" onClick={onClose} className="px-4 py-2 text-sm rounded-lg border cursor-pointer">
            Cancelar
          </button>
          <button
            type="button"
            onClick={() => {
              const balCentavos = parseMxnToCentavos(balance);
              const minCentavos = parseMxnToCentavos(minPay, true) ?? 0;
              const numRate = Math.max(0, Number(rate) || 0);
              if (!creditor.trim() || balCentavos === null) {
                setErr('Ingresa acreedor y saldo inicial válido.');
                return;
              }
              onSave({
                id: crypto.randomUUID(),
                creditorName: creditor.trim(),
                initialBalanceCentavos: balCentavos,
                remainingBalanceCentavos: balCentavos,
                dueDate: '',
                minimumPaymentCentavos: minCentavos,
                annualInterestRatePercent: numRate,
                payments: [],
              });
            }}
            className="px-4 py-2 text-sm font-medium rounded-lg bg-[#1B5E20] text-white cursor-pointer"
          >
            Guardar
          </button>
        </div>
      </div>
    </div>
  );
}

function SimpleAmountModal({
  title,
  allowZero = false,
  onConfirm,
  onClose,
}: {
  title: string;
  allowZero?: boolean;
  onConfirm: (amountCentavos: number, note: string) => string | null;
  onClose: () => void;
}) {
  const [amount, setAmount] = useState('');
  const [note, setNote] = useState('');
  const [err, setErr] = useState<string | null>(null);

  return (
    <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4">
      <div className="w-full max-w-md rounded-2xl bg-white dark:bg-[#151E17] border border-[#D5DDD6] dark:border-[#2D3A2F] p-6 space-y-3">
        <h3 className="text-lg font-bold">{title}</h3>
        <input
          type="text"
          value={amount}
          onChange={(e) => {
            setAmount(e.target.value);
            setErr(null);
          }}
          placeholder="Importe MXN"
          className="w-full px-3 py-2 text-sm rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] bg-white dark:bg-[#0E1510] font-money"
        />
        <input
          type="text"
          value={note}
          onChange={(e) => setNote(e.target.value)}
          placeholder="Nota o motivo (opcional)"
          className="w-full px-3 py-2 text-sm rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] bg-white dark:bg-[#0E1510]"
        />
        {err && <p className="text-xs text-[#C62828]">{err}</p>}
        <div className="flex justify-end gap-2 pt-2">
          <button type="button" onClick={onClose} className="px-4 py-2 text-sm rounded-lg border cursor-pointer">
            Cancelar
          </button>
          <button
            type="button"
            onClick={() => {
              const centavos = parseMxnToCentavos(amount, allowZero);
              if (centavos === null) {
                setErr('Ingresa un importe válido.');
                return;
              }
              const msg = onConfirm(centavos, note.trim());
              if (msg) setErr(msg);
            }}
            className="px-4 py-2 text-sm font-medium rounded-lg bg-[#1B5E20] text-white cursor-pointer"
          >
            Confirmar
          </button>
        </div>
      </div>
    </div>
  );
}

function DebtSimulatorModal({ debt, onClose }: { debt: Debt; onClose: () => void }) {
  const [monthlyInput, setMonthlyInput] = useState(
    debt.minimumPaymentCentavos > 0 ? (debt.minimumPaymentCentavos / 100).toFixed(2) : '1000.00'
  );
  const monthlyCentavos = parseMxnToCentavos(monthlyInput) ?? 0;
  const sim = simulateDebtPayoff(
    debt.remainingBalanceCentavos,
    monthlyCentavos,
    debt.annualInterestRatePercent
  );

  return (
    <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4">
      <div className="w-full max-w-md rounded-2xl bg-white dark:bg-[#151E17] border border-[#D5DDD6] dark:border-[#2D3A2F] p-6 space-y-3">
        <h3 className="text-lg font-bold">Simulador: {debt.creditorName}</h3>
        <p className="text-xs text-[#5E6B5F] dark:text-[#96A598]">
          Saldo pendiente: {formatMxnFromCentavos(debt.remainingBalanceCentavos)} · Tasa anual {debt.annualInterestRatePercent}%
        </p>
        <input
          type="text"
          value={monthlyInput}
          onChange={(e) => setMonthlyInput(e.target.value)}
          placeholder="Pago mensual estimado MXN"
          className="w-full px-3 py-2 text-sm rounded-lg border border-[#D5DDD6] dark:border-[#2D3A2F] bg-white dark:bg-[#0E1510] font-money"
        />
        {sim.possible ? (
          <div className="rounded-xl bg-[#EAF4EC] dark:bg-[#152819] p-4 space-y-1 text-sm">
            <p className="font-bold">Tiempo estimado: {sim.months} mes(es)</p>
            <p>Intereses estimados: {formatMxnFromCentavos(sim.totalInterestCentavos)}</p>
            <p>Total estimado a pagar: {formatMxnFromCentavos(sim.totalPaidCentavos)}</p>
          </div>
        ) : (
          <p className="text-xs text-[#C62828] dark:text-[#FF8A80]">
            Con ese pago mensual no se alcanza a cubrir el interés mensual o el monto es cero. Aumenta el pago mensual para calcular la liquidación.
          </p>
        )}
        <p className="text-xs text-[#5E6B5F] dark:text-[#96A598]">
          Estimación orientativa basada en los datos ingresados y pagos constantes; no sustituye un estado de cuenta oficial.
        </p>
        <div className="flex justify-end pt-1">
          <button type="button" onClick={onClose} className="px-4 py-2 text-sm font-medium rounded-lg bg-[#1B5E20] text-white cursor-pointer">
            Cerrar
          </button>
        </div>
      </div>
    </div>
  );
}
