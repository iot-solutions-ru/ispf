/** Excel serial date epoch: 1899-12-30 UTC (serial 1 = 1900-01-01). */
const EXCEL_EPOCH_MS = Date.UTC(1899, 11, 30);

function serialFromUtcParts(year: number, month: number, day: number): number {
  return (Date.UTC(year, month, day) - EXCEL_EPOCH_MS) / 86_400_000;
}

export function dateToExcelSerial(date: Date): number {
  return serialFromUtcParts(date.getFullYear(), date.getMonth(), date.getDate());
}

export function excelSerialToUtcDate(serial: number): Date {
  return new Date(EXCEL_EPOCH_MS + Math.trunc(serial) * 86_400_000);
}

export function excelTodaySerial(): number {
  return dateToExcelSerial(new Date());
}

export function excelNowSerial(): number {
  const now = new Date();
  const midnight = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  return (now.getTime() - midnight) / 86_400_000 + dateToExcelSerial(now);
}

export function excelYear(serial: number): number {
  return excelSerialToUtcDate(serial).getUTCFullYear();
}

export function excelMonth(serial: number): number {
  return excelSerialToUtcDate(serial).getUTCMonth() + 1;
}

export function excelDay(serial: number): number {
  return excelSerialToUtcDate(serial).getUTCDate();
}

export function excelDate(year: number, month: number, day: number): number {
  return serialFromUtcParts(Math.trunc(year), Math.trunc(month) - 1, Math.trunc(day));
}

export function excelDays(endSerial: number, startSerial: number): number {
  return Math.trunc(endSerial) - Math.trunc(startSerial);
}

export function excelWeekday(serial: number, returnType = 1): number {
  const day = excelSerialToUtcDate(serial).getUTCDay();
  if (returnType === 2) {
    return day === 0 ? 7 : day;
  }
  if (returnType === 3) {
    return day === 0 ? 6 : day - 1;
  }
  return day + 1;
}

export function excelHour(serial: number): number {
  const fraction = serial - Math.trunc(serial);
  if (fraction <= 0) {
    return 0;
  }
  return Math.floor(fraction * 24);
}

export function excelMinute(serial: number): number {
  const fraction = serial - Math.trunc(serial);
  if (fraction <= 0) {
    return 0;
  }
  return Math.floor(fraction * 24 * 60) % 60;
}

export function excelSecond(serial: number): number {
  const fraction = serial - Math.trunc(serial);
  if (fraction <= 0) {
    return 0;
  }
  return Math.floor(fraction * 86400) % 60;
}

export function excelTime(hour: number, minute: number, second: number): number {
  const totalSeconds = Math.trunc(hour) * 3600 + Math.trunc(minute) * 60 + Math.trunc(second);
  return totalSeconds / 86_400;
}

export function excelEdate(serial: number, months: number): number {
  const date = excelSerialToUtcDate(serial);
  return serialFromUtcParts(
    date.getUTCFullYear(),
    date.getUTCMonth() + Math.trunc(months),
    date.getUTCDate()
  );
}

export function excelEomonth(serial: number, months: number): number {
  const date = excelSerialToUtcDate(serial);
  const y = date.getUTCFullYear();
  const m = date.getUTCMonth() + Math.trunc(months) + 1;
  return serialFromUtcParts(y, m, 0);
}

export function excelDatedif(startSerial: number, endSerial: number, unit: string): number {
  const unitUpper = unit.toUpperCase();
  const start = excelSerialToUtcDate(startSerial);
  const end = excelSerialToUtcDate(endSerial);
  if (unitUpper === "D") {
    return Math.trunc(endSerial) - Math.trunc(startSerial);
  }
  if (unitUpper === "M") {
    return (end.getUTCFullYear() - start.getUTCFullYear()) * 12 + (end.getUTCMonth() - start.getUTCMonth());
  }
  if (unitUpper === "Y") {
    let years = end.getUTCFullYear() - start.getUTCFullYear();
    const monthDelta = end.getUTCMonth() - start.getUTCMonth();
    const dayDelta = end.getUTCDate() - start.getUTCDate();
    if (monthDelta < 0 || (monthDelta === 0 && dayDelta < 0)) {
      years -= 1;
    }
    return years;
  }
  return Math.trunc(endSerial) - Math.trunc(startSerial);
}

export function excelRand(): number {
  return Math.random();
}

export function excelRandBetween(bottom: number, top: number): number {
  const low = Math.trunc(bottom);
  const high = Math.trunc(top);
  if (high < low) {
    return Number.NaN;
  }
  return Math.floor(Math.random() * (high - low + 1)) + low;
}

function isWeekend(serial: number): boolean {
  const dow = excelSerialToUtcDate(serial).getUTCDay();
  return dow === 0 || dow === 6;
}

export function excelNetworkdays(
  startSerial: number,
  endSerial: number,
  holidays: number[] = []
): number {
  let start = Math.trunc(startSerial);
  let end = Math.trunc(endSerial);
  if (start > end) {
    [start, end] = [end, start];
  }
  const holidaySet = new Set(holidays.map((h) => Math.trunc(h)));
  let count = 0;
  for (let day = start; day <= end; day++) {
    if (isWeekend(day) || holidaySet.has(day)) {
      continue;
    }
    count++;
  }
  return count;
}

export function excelWorkday(startSerial: number, days: number, holidays: number[] = []): number {
  let current = Math.trunc(startSerial);
  let remaining = Math.trunc(days);
  const holidaySet = new Set(holidays.map((h) => Math.trunc(h)));
  const step = remaining >= 0 ? 1 : -1;
  remaining = Math.abs(remaining);
  while (remaining > 0) {
    current += step;
    if (isWeekend(current) || holidaySet.has(current)) {
      continue;
    }
    remaining--;
  }
  return current;
}
