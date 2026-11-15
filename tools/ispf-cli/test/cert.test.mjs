import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { Readable, Writable } from "node:stream";
import { spawnSync } from "node:child_process";
import test from "node:test";
import { fileURLToPath } from "node:url";
import { checkBank, formatGrade, gradeBank, loadAnswers, loadBank, promptAnswers } from "../src/cert.mjs";

const here = path.dirname(fileURLToPath(import.meta.url));
const bankDir = path.resolve(here, "../../../examples/certification");
const bin = path.resolve(here, "../bin/ispf.mjs");
const banks = [
  "solution-developer-l1.json",
  "solution-developer-l2.json",
  "platform-admin-core.json",
];

function answerKey(bank) {
  return Object.fromEntries(bank.questions.map((question) => [question.id, question.correctIndex]));
}

function runCli(args) {
  return spawnSync(process.execPath, [bin, ...args], { encoding: "utf8" });
}

test("shipped banks pass check and grade from their own key", () => {
  for (const name of banks) {
    const bank = loadBank(path.join(bankDir, name));
    assert.deepEqual(checkBank(bank), []);
    const graded = gradeBank(bank, answerKey(bank));
    assert.equal(graded.correct, graded.total);
    assert.deepEqual(graded.misses, []);
  }
});

test("a wrong index is a miss and the report omits the correct option", () => {
  const bank = loadBank(path.join(bankDir, "solution-developer-l1.json"));
  const answers = answerKey(bank);
  const missed = bank.questions[1];
  answers[missed.id] = (missed.correctIndex + 1) % missed.options.length;
  const graded = gradeBank(bank, answers);
  assert.equal(graded.correct, bank.questions.length - 1);
  assert.deepEqual(graded.misses, [{ id: missed.id, reference: missed.reference }]);
  const text = formatGrade(graded);
  assert.match(text, new RegExp(`miss ${missed.id} ${missed.reference}`));
  assert.equal(text.includes(missed.options[missed.correctIndex]), false);
  assert.match(text, new RegExp(`${graded.correct}/${graded.total}$`));
});

test("an unknown answer id is rejected and a missing id is a miss", () => {
  const bank = loadBank(path.join(bankDir, "platform-admin-core.json"));
  assert.throws(() => gradeBank(bank, { ...answerKey(bank), "no-such": 0 }), /unknown question id: no-such/);
  const answers = answerKey(bank);
  const dropped = bank.questions[0].id;
  delete answers[dropped];
  const graded = gradeBank(bank, answers);
  assert.equal(graded.misses.some((miss) => miss.id === dropped), true);
  assert.equal(graded.correct, bank.questions.length - 1);
});

test("answers file rejects a string index and an empty level fails the check", () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "ispf-cert-"));
  const answersPath = path.join(dir, "answers.json");
  fs.writeFileSync(answersPath, JSON.stringify({ q1: "0" }));
  assert.throws(() => loadAnswers(answersPath), /answer for q1/);
  const bank = loadBank(path.join(bankDir, "solution-developer-l1.json"));
  const broken = structuredClone(bank);
  broken.level = "  ";
  assert.ok(checkBank(broken).some((line) => line.includes("level")));
  fs.rmSync(dir, { recursive: true, force: true });
});

test("a duplicated id and a bad index fail the check", () => {
  const bank = loadBank(path.join(bankDir, "solution-developer-l2.json"));
  const broken = structuredClone(bank);
  broken.questions.push({ ...broken.questions[0] });
  assert.ok(checkBank(broken).some((line) => line.includes("duplicated")));
  broken.questions.pop();
  broken.questions[0].correctIndex = 99;
  assert.ok(checkBank(broken).some((line) => line.includes("correctIndex")));
});

test("the prompt stores a 1-based choice as a 0-based index", async () => {
  const bank = {
    track: "solution-developer",
    level: 1,
    questions: [
      {
        id: "q1",
        type: "multiple-choice",
        prompt: "Pick",
        options: ["no", "yes"],
        correctIndex: 1,
        reference: "docs/en/learn.md",
      },
    ],
  };
  const input = Readable.from(["2\n"]);
  const output = new Writable({
    write(_chunk, _enc, callback) {
      callback();
    },
  });
  const answers = await promptAnswers(bank, input, output);
  assert.deepEqual(answers, { q1: 1 });
  assert.equal(gradeBank(bank, answers).correct, 1);
});

test("cert check and cert run exit from the shipped bank", () => {
  const bankPath = path.join(bankDir, "solution-developer-l1.json");
  const checked = runCli(["cert", "check", bankPath]);
  assert.equal(checked.status, 0, checked.stderr);
  assert.match(checked.stdout, /^ok 8\n$/);

  const bank = loadBank(bankPath);
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "ispf-cert-"));
  const answersPath = path.join(dir, "answers.json");
  fs.writeFileSync(answersPath, JSON.stringify(answerKey(bank)));
  const passed = runCli(["cert", "run", bankPath, "--answers", answersPath]);
  assert.equal(passed.status, 0, passed.stderr);
  assert.match(passed.stdout, /8\/8\n$/);

  const answers = answerKey(bank);
  const first = bank.questions[0];
  answers[first.id] = (first.correctIndex + 1) % first.options.length;
  fs.writeFileSync(answersPath, JSON.stringify(answers));
  const failed = runCli(["cert", "run", bankPath, "--answers", answersPath]);
  assert.equal(failed.status, 1);
  assert.match(failed.stdout, new RegExp(`miss ${bank.questions[0].id} `));
  assert.equal(failed.stdout.includes(bank.questions[0].options[bank.questions[0].correctIndex]), false);

  const blocked = runCli(["cert", "run", bankPath]);
  assert.equal(blocked.status, 1);
  assert.match(blocked.stderr, /--answers/);
  fs.rmSync(dir, { recursive: true, force: true });
});
