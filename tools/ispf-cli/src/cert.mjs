import readline from "node:readline/promises";
import { readJsonFile } from "./util.mjs";

export function loadBank(filePath) {
  let bank;
  try {
    bank = readJsonFile(filePath);
  } catch (err) {
    throw new Error(`Cannot read bank ${filePath}: ${err.message}`);
  }
  const errors = checkBank(bank);
  if (errors.length) throw new Error(errors.join("\n"));
  return bank;
}

export function loadAnswers(filePath) {
  let answers;
  try {
    answers = readJsonFile(filePath);
  } catch (err) {
    throw new Error(`Cannot read answers ${filePath}: ${err.message}`);
  }
  if (answers === null || typeof answers !== "object" || Array.isArray(answers)) {
    throw new Error("answers must be a JSON object of question id to index");
  }
  for (const [key, value] of Object.entries(answers)) {
    if (!Number.isInteger(value) || value < 0) {
      throw new Error(`answer for ${key} must be an index starting at 0`);
    }
  }
  return answers;
}

/** Structural check. Does not print which option is correct. */
export function checkBank(bank) {
  const errors = [];
  if (bank === null || typeof bank !== "object" || Array.isArray(bank)) {
    return ["bank must be a JSON object"];
  }
  if (typeof bank.track !== "string" || bank.track.trim() === "") {
    errors.push("track must be a non-empty string");
  }
  const levelOk =
    (typeof bank.level === "string" && bank.level.trim() !== "") ||
    (typeof bank.level === "number" && Number.isFinite(bank.level));
  if (!levelOk) errors.push("level must be a non-empty string or a finite number");
  if (!Array.isArray(bank.questions) || bank.questions.length === 0) {
    errors.push("questions must be a non-empty array");
    return errors;
  }
  const seen = new Set();
  bank.questions.forEach((question, index) => {
    const where = `questions[${index}]`;
    if (question === null || typeof question !== "object" || Array.isArray(question)) {
      errors.push(`${where} must be an object`);
      return;
    }
    const id = question.id;
    if (typeof id !== "string" || id.trim() === "") {
      errors.push(`${where}.id must be a non-empty string`);
    } else if (seen.has(id)) {
      errors.push(`${where}.id is duplicated: ${id}`);
    } else {
      seen.add(id);
    }
    if (question.type !== "multiple-choice") {
      errors.push(`${where}.type must be multiple-choice`);
    }
    if (typeof question.prompt !== "string" || question.prompt.trim() === "") {
      errors.push(`${where}.prompt must be a non-empty string`);
    }
    if (!Array.isArray(question.options) || question.options.length < 2) {
      errors.push(`${where}.options must list at least two strings`);
    } else if (question.options.some((option) => typeof option !== "string" || option.trim() === "")) {
      errors.push(`${where}.options must list at least two strings`);
    }
    const choices = Array.isArray(question.options) ? question.options.length : 0;
    if (!Number.isInteger(question.correctIndex) || question.correctIndex < 0 || question.correctIndex >= choices) {
      errors.push(`${where}.correctIndex must be an index into options`);
    }
    if (typeof question.reference !== "string" || question.reference.trim() === "") {
      errors.push(`${where}.reference must be a non-empty string`);
    }
  });
  return errors;
}

/**
 * `answers` maps question id to the 0-based option index stored in the bank.
 * A missing id is a miss. An id that is not in the bank is an error.
 */
export function gradeBank(bank, answers) {
  const errors = checkBank(bank);
  if (errors.length) throw new Error(errors.join("\n"));
  if (answers === null || typeof answers !== "object" || Array.isArray(answers)) {
    throw new Error("answers must be a JSON object of question id to index");
  }
  const ids = new Set(bank.questions.map((question) => question.id));
  for (const key of Object.keys(answers)) {
    if (!ids.has(key)) throw new Error(`unknown question id: ${key}`);
  }
  const misses = [];
  let correct = 0;
  for (const question of bank.questions) {
    if (answers[question.id] === question.correctIndex) correct += 1;
    else misses.push({ id: question.id, reference: question.reference });
  }
  return { correct, total: bank.questions.length, misses };
}

export function formatGrade(result) {
  const lines = result.misses.map((miss) => `miss ${miss.id} ${miss.reference}`);
  lines.push(`${result.correct}/${result.total}`);
  return lines.join("\n");
}

/** Reads 1-based option numbers, one line per question, and returns 0-based indexes. */
export async function promptAnswers(bank, input, output) {
  const rl = readline.createInterface({ input, output });
  const answers = {};
  try {
    for (const question of bank.questions) {
      output.write(`${question.id}\n${question.prompt}\n`);
      question.options.forEach((option, index) => {
        output.write(`  ${index + 1}. ${option}\n`);
      });
      const line = (await rl.question("> ")).trim();
      const picked = Number(line);
      answers[question.id] = Number.isInteger(picked) ? picked - 1 : Number.NaN;
    }
  } finally {
    rl.close();
  }
  return answers;
}
