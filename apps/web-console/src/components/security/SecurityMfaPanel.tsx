import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import QRCode from "qrcode";
import {
  cancelMfaEnrollment,
  fetchMfaStatus,
  startMfaEnrollment,
  verifyMfaEnrollment,
  type MfaEnrollmentStart,
} from "../../api/mfa";
import { getStoredSession } from "../../auth/session";

export default function SecurityMfaPanel() {
  const { t } = useTranslation(["security", "common"]);
  const queryClient = useQueryClient();
  const session = getStoredSession();
  const qrCanvasRef = useRef<HTMLCanvasElement>(null);
  const [enrollment, setEnrollment] = useState<MfaEnrollmentStart | null>(null);
  const [verifyCode, setVerifyCode] = useState("");
  const [feedback, setFeedback] = useState<string | null>(null);

  const statusQuery = useQuery({
    queryKey: ["mfa-status"],
    queryFn: fetchMfaStatus,
  });

  const enrollMutation = useMutation({
    mutationFn: startMfaEnrollment,
    onSuccess: (data) => {
      setEnrollment(data);
      setVerifyCode("");
      setFeedback(null);
      queryClient.invalidateQueries({ queryKey: ["mfa-status"] });
    },
    onError: (error: Error) => setFeedback(error.message),
  });

  const verifyMutation = useMutation({
    mutationFn: () => verifyMfaEnrollment(verifyCode.trim()),
    onSuccess: (result) => {
      setEnrollment(null);
      setVerifyCode("");
      setFeedback(result.message);
      queryClient.invalidateQueries({ queryKey: ["mfa-status"] });
    },
    onError: (error: Error) => setFeedback(error.message),
  });

  const cancelMutation = useMutation({
    mutationFn: cancelMfaEnrollment,
    onSuccess: () => {
      setEnrollment(null);
      setVerifyCode("");
      setFeedback(null);
      queryClient.invalidateQueries({ queryKey: ["mfa-status"] });
    },
    onError: (error: Error) => setFeedback(error.message),
  });

  useEffect(() => {
    const status = statusQuery.data;
    if (!status?.enrollmentPending || enrollment != null) {
      return;
    }
    if (status.pendingSecret && status.pendingOtpauthUri) {
      setEnrollment({
        secret: status.pendingSecret,
        otpauthUri: status.pendingOtpauthUri,
        hint: t("mfa.pendingHint"),
      });
    }
  }, [statusQuery.data, enrollment, t]);

  const activeOtpauthUri = enrollment?.otpauthUri;

  useEffect(() => {
    const canvas = qrCanvasRef.current;
    if (!canvas || !activeOtpauthUri) {
      return;
    }
    QRCode.toCanvas(canvas, activeOtpauthUri, {
      width: 200,
      margin: 1,
      color: { dark: "#e6edf3", light: "#0d1117" },
    }).catch(() => {
      // canvas may be unavailable in tests
    });
  }, [activeOtpauthUri]);

  const status = statusQuery.data;
  const showEnrollFlow = Boolean(enrollment) || status?.enrollmentPending;

  return (
    <section className="modal-section security-mfa-panel" data-testid="security-mfa-panel">
      <h4>{t("mfa.title")}</h4>
      <p className="op-muted">{t("mfa.subtitle")}</p>

      {statusQuery.isLoading && <p className="op-muted">{t("common:action.loading")}</p>}
      {statusQuery.error && (
        <div className="op-alert op-alert-error">{String(statusQuery.error)}</div>
      )}
      {feedback && <div className="op-alert">{feedback}</div>}

      {status && !status.enabled && (
        <p className="op-muted">{t("mfa.disabledOnServer")}</p>
      )}

      {status?.enabled && (
        <div className="security-mfa-status">
          <p>
            <strong>{t("mfa.account")}:</strong> <code>{session?.username ?? "—"}</code>
          </p>
          <p>
            <strong>{t("mfa.statusLabel")}:</strong>{" "}
            {status.enrolled
              ? t("mfa.statusEnrolled")
              : status.enrollmentPending
                ? t("mfa.statusPending")
                : t("mfa.statusNotEnrolled")}
          </p>
        </div>
      )}

      {status?.enabled && !status.enrolled && !showEnrollFlow && (
        <button
          type="button"
          className="btn primary"
          disabled={enrollMutation.isPending}
          onClick={() => enrollMutation.mutate()}
        >
          {t("mfa.startEnroll")}
        </button>
      )}

      {status?.enabled && showEnrollFlow && (
        <div className="security-mfa-enroll" data-testid="security-mfa-enroll">
          <p className="op-muted">{enrollment?.hint ?? t("mfa.pendingHint")}</p>
          <div className="security-mfa-qr-row">
            <canvas ref={qrCanvasRef} className="security-mfa-qr" aria-hidden="true" />
            <div className="security-mfa-secret">
              <label className="op-muted" htmlFor="mfa-secret">
                {t("mfa.manualSecret")}
              </label>
              <input
                id="mfa-secret"
                className="system-settings-input mono"
                readOnly
                value={enrollment?.secret ?? ""}
                onFocus={(event) => event.target.select()}
              />
            </div>
          </div>
          <label className="security-mfa-verify-label" htmlFor="mfa-verify-code">
            {t("mfa.verifyCode")}
          </label>
          <div className="security-mfa-verify-row">
            <input
              id="mfa-verify-code"
              className="system-settings-input mono"
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
              placeholder="000000"
              value={verifyCode}
              onChange={(event) => setVerifyCode(event.target.value.replace(/\D/g, ""))}
            />
            <button
              type="button"
              className="btn primary"
              disabled={verifyMutation.isPending || verifyCode.length !== 6}
              onClick={() => verifyMutation.mutate()}
            >
              {t("mfa.verify")}
            </button>
            <button
              type="button"
              className="btn"
              disabled={cancelMutation.isPending}
              onClick={() => cancelMutation.mutate()}
            >
              {t("common:action.cancel")}
            </button>
          </div>
        </div>
      )}
    </section>
  );
}
