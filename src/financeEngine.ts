export type MovementType =
  | 'INCOME'
  | 'EXPENSE'
  | 'SAVINGS_CONTRIBUTION'
  | 'SAVINGS_WITHDRAWAL'
  | 'DEBT_PAYMENT'
  | 'BALANCE_ADJUSTMENT';

export type PeriodType = 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY' | 'CUSTOM';
export type RecurrenceFrequency = 'NONE' | 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY';
export type PaymentMethod = 'CASH' | 'DEBIT' | 'CREDIT' | 'TRANSFER' | 'OTHER';
export type PaymentStatus = 'PENDING' | 'PAID' | 'OVERDUE';

export const INCOME_CATEGORIES = [
  'Sueldo',
  'Ingresos adicionales',
  'Comisiones',
  'Ventas',
  'Apoyos',
  'Otros ingresos',
] as const;

export const EXPENSE_CATEGORIES = [
  'Alimentación',
  'Transporte',
  'Vivienda',
  'Servicios',
  'Salud',
  'Educación',
  'Entretenimiento',
  'Compras',
  'Deudas',
  'Otros gastos',
] as const;

export const SCHEDULED_PAYMENT_CATEGORIES = [
  'Renta',
  'Servicios',
  'Tarjetas de crédito',
  'Préstamos',
  'Suscripciones',
  'Colegiaturas',
  'Otros compromisos',
] as const;

export const MOVEMENT_LABELS: Record<MovementType, string> = {
  INCOME: 'Ingreso',
  EXPENSE: 'Gasto',
  SAVINGS_CONTRIBUTION: 'Aportación a ahorro',
  SAVINGS_WITHDRAWAL: 'Retiro de ahorro',
  DEBT_PAYMENT: 'Abono a deuda',
  BALANCE_ADJUSTMENT: 'Ajuste de saldo',
};

export const PERIOD_LABELS: Record<PeriodType, string> = {
  WEEKLY: 'Semanal',
  BIWEEKLY: 'Quincenal',
  MONTHLY: 'Mensual',
  CUSTOM: 'Personalizado',
};

export const RECURRENCE_LABELS: Record<RecurrenceFrequency, string> = {
  NONE: 'Único',
  WEEKLY: 'Semanal',
  BIWEEKLY: 'Quincenal',
  MONTHLY: 'Mensual',
};

export const PAYMENT_METHOD_LABELS: Record<PaymentMethod, string> = {
  CASH: 'Efectivo',
  DEBIT: 'Tarjeta de débito',
  CREDIT: 'Tarjeta de crédito',
  TRANSFER: 'Transferencia SPEI',
  OTHER: 'Otro',
};

export interface FinancialTransaction {
  id: string;
  type: MovementType;
  amountCentavos: number;
  category: string;
  date: string; // YYYY-MM-DD
  description: string;
  recurrence: RecurrenceFrequency;
  paymentMethod: PaymentMethod;
  isAdjustmentPositive: boolean;
}

export interface Budget {
  id: string;
  category: string;
  periodType: PeriodType;
  limitCentavos: number;
  thresholds: number[];
}

export interface ScheduledPayment {
  id: string;
  description: string;
  category: string;
  amountCentavos: number;
  dueDate: string; // YYYY-MM-DD
  status: PaymentStatus;
  recurrence: RecurrenceFrequency;
}

export interface SavingsGoal {
  id: string;
  name: string;
  targetCentavos: number;
  savedCentavos: number;
  targetDate: string;
  description: string;
}

export interface Debt {
  id: string;
  creditorName: string;
  initialBalanceCentavos: number;
  remainingBalanceCentavos: number;
  dueDate: string;
  minimumPaymentCentavos: number;
  annualInterestRatePercent: number;
  payments: Array<{ id: string; amountCentavos: number; date: string; note: string }>;
}

export interface FinanceState {
  initialBalanceCentavos: number;
  selectedPeriod: PeriodType;
  customStart: string;
  customEnd: string;
  globalThresholds: number[];
  transactions: FinancialTransaction[];
  budgets: Budget[];
  scheduledPayments: ScheduledPayment[];
  savingsGoals: SavingsGoal[];
  debts: Debt[];
  demoLoaded: boolean;
}

export function formatMxnFromCentavos(centavos: number): string {
  return new Intl.NumberFormat('es-MX', {
    style: 'currency',
    currency: 'MXN',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(centavos / 100);
}

export function parseMxnToCentavos(raw: string, allowZero = false): number | null {
  const cleaned = raw.trim().replace(/[$,\s]/g, '');
  if (!cleaned || !/^\d+(\.\d{1,2})?$/.test(cleaned)) return null;
  const num = Number(cleaned);
  if (!Number.isFinite(num)) return null;
  const centavos = Math.round(num * 100);
  if (!allowZero && centavos <= 0) return null;
  if (centavos < 0 || centavos > 99999999999) return null;
  return centavos;
}

export function todayIso(): string {
  const d = new Date();
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

export function addDaysIso(baseIso: string, deltaDays: number): string {
  const [y, m, d] = baseIso.split('-').map(Number);
  const dt = new Date(y, m - 1, d);
  dt.setDate(dt.getDate() + deltaDays);
  return `${dt.getFullYear()}-${String(dt.getMonth() + 1).padStart(2, '0')}-${String(dt.getDate()).padStart(2, '0')}`;
}

export function getPeriodRange(
  period: PeriodType,
  refIso: string,
  customStart?: string,
  customEnd?: string
): { start: string; end: string } {
  const [y, m, d] = refIso.split('-').map(Number);
  const dt = new Date(y, m - 1, d);

  if (period === 'WEEKLY') {
    const dayOfWeek = dt.getDay(); // 0 Sun .. 6 Sat
    const diffToMonday = dayOfWeek === 0 ? -6 : 1 - dayOfWeek;
    const monday = new Date(dt);
    monday.setDate(dt.getDate() + diffToMonday);
    const sunday = new Date(monday);
    sunday.setDate(monday.getDate() + 6);
    const fmt = (x: Date) =>
      `${x.getFullYear()}-${String(x.getMonth() + 1).padStart(2, '0')}-${String(x.getDate()).padStart(2, '0')}`;
    return { start: fmt(monday), end: fmt(sunday) };
  }

  if (period === 'BIWEEKLY') {
    const lastDay = new Date(y, m, 0).getDate();
    if (d <= 15) {
      return {
        start: `${y}-${String(m).padStart(2, '0')}-01`,
        end: `${y}-${String(m).padStart(2, '0')}-15`,
      };
    }
    return {
      start: `${y}-${String(m).padStart(2, '0')}-16`,
      end: `${y}-${String(m).padStart(2, '0')}-${String(lastDay).padStart(2, '0')}`,
    };
  }

  if (period === 'MONTHLY') {
    const lastDay = new Date(y, m, 0).getDate();
    return {
      start: `${y}-${String(m).padStart(2, '0')}-01`,
      end: `${y}-${String(m).padStart(2, '0')}-${String(lastDay).padStart(2, '0')}`,
    };
  }

  const s = customStart || `${y}-${String(m).padStart(2, '0')}-01`;
  const e = customEnd || refIso;
  return s <= e ? { start: s, end: e } : { start: e, end: s };
}

export function calculateAvailableBalanceCentavos(
  initialBalanceCentavos: number,
  transactions: FinancialTransaction[]
): number {
  return transactions.reduce((acc, tx) => {
    switch (tx.type) {
      case 'INCOME':
      case 'SAVINGS_WITHDRAWAL':
        return acc + tx.amountCentavos;
      case 'EXPENSE':
      case 'DEBT_PAYMENT':
      case 'SAVINGS_CONTRIBUTION':
        return acc - tx.amountCentavos;
      case 'BALANCE_ADJUSTMENT':
        return tx.isAdjustmentPositive ? acc + tx.amountCentavos : acc - tx.amountCentavos;
      default:
        return acc;
    }
  }, initialBalanceCentavos);
}

export function resolveEffectivePaymentStatus(
  payment: ScheduledPayment,
  today: string
): PaymentStatus {
  if (payment.status === 'PAID') return 'PAID';
  if (payment.dueDate < today) return 'OVERDUE';
  return 'PENDING';
}

export function simulateDebtPayoff(
  remainingCentavos: number,
  monthlyPaymentCentavos: number,
  annualRatePercent: number
): {
  possible: boolean;
  months: number;
  totalInterestCentavos: number;
  totalPaidCentavos: number;
} {
  if (remainingCentavos <= 0) {
    return { possible: true, months: 0, totalInterestCentavos: 0, totalPaidCentavos: 0 };
  }
  if (monthlyPaymentCentavos <= 0) {
    return { possible: false, months: 0, totalInterestCentavos: 0, totalPaidCentavos: 0 };
  }
  const monthlyRate = Math.max(0, annualRatePercent) / 100 / 12;
  let balance = remainingCentavos;
  let months = 0;
  let totalInterest = 0;
  let totalPaid = 0;

  while (balance > 0 && months < 600) {
    const interest = Math.round(balance * monthlyRate);
    if (monthlyPaymentCentavos <= interest && monthlyRate > 0) {
      return { possible: false, months: 0, totalInterestCentavos: 0, totalPaidCentavos: 0 };
    }
    const due = balance + interest;
    const pay = Math.min(monthlyPaymentCentavos, due);
    balance = due - pay;
    totalInterest += interest;
    totalPaid += pay;
    months += 1;
  }

  return {
    possible: balance === 0,
    months: balance === 0 ? months : 0,
    totalInterestCentavos: totalInterest,
    totalPaidCentavos: totalPaid,
  };
}

export function exportTransactionsCsv(transactions: FinancialTransaction[]): string {
  const escape = (val: string) => {
    const safe = /^[=+\-@]/.test(val) ? `'${val}` : val;
    return /[,"\n]/.test(safe) ? `"${safe.replace(/"/g, '""')}"` : safe;
  };
  const header = 'Fecha,Tipo,Categoría,Importe (MXN),Método de pago,Frecuencia,Descripción\n';
  const rows = [...transactions]
    .sort((a, b) => b.date.localeCompare(a.date))
    .map((tx) => {
      const positive =
        tx.type === 'INCOME' ||
        tx.type === 'SAVINGS_WITHDRAWAL' ||
        (tx.type === 'BALANCE_ADJUSTMENT' && tx.isAdjustmentPositive);
      const signed = ((positive ? tx.amountCentavos : -tx.amountCentavos) / 100).toFixed(2);
      return [
        escape(tx.date),
        escape(MOVEMENT_LABELS[tx.type]),
        escape(tx.category),
        signed,
        escape(PAYMENT_METHOD_LABELS[tx.paymentMethod]),
        escape(RECURRENCE_LABELS[tx.recurrence]),
        escape(tx.description),
      ].join(',');
    })
    .join('\n');

  return '\uFEFF' + header + rows;
}
