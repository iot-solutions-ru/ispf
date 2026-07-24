import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Button, Input, Space, Typography } from "antd";
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
import { useTheme } from "../../theme";

export default function SecurityMfaPanel() {
  const { t } = useTranslation(["security", "common"]);
  const { resolvedTheme } = useTheme();
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
    const dark = resolvedTheme === "dark";
    QRCode.toCanvas(canvas, activeOtpauthUri, {
      width: 200,
      margin: 1,
      color: dark
        ? { dark: "#e6edf3", light: "#0d1117" }
        : { dark: "#1f2937", light: "#ffffff" },
    }).catch(() => {
      // canvas may be unavailable in tests
    });
  }, [activeOtpauthUri, resolvedTheme]);

  const status = statusQuery.data;
  const showEnrollFlow = Boolean(enrollment) || status?.enrollmentPending;

  return (
    <section className="modal-section security-mfa-panel" data-testid="security-mfa-panel" style={{ marginBottom: 24 }}>
      <Typography.Title level={5}>{t("mfa.title")}</Typography.Title>
      <Typography.Paragraph type="secondary">{t("mfa.subtitle")}</Typography.Paragraph>

      {statusQuery.isLoading && <Typography.Paragraph type="secondary">{t("common:action.loading")}</Typography.Paragraph>}
      {statusQuery.error && (
        <Alert type="error" showIcon message={String(statusQuery.error)} style={{ marginBottom: 12 }} />
      )}
      {feedback && <Alert showIcon message={feedback} style={{ marginBottom: 12 }} />}

      {status && !status.enabled && (
        <Typography.Paragraph type="secondary">{t("mfa.disabledOnServer")}</Typography.Paragraph>
      )}

      {status?.enabled && (
        <Space orientation="vertical" size={4} style={{ marginBottom: 12 }}>
          <Typography.Text>
            <strong>{t("mfa.account")}:</strong> <Typography.Text code>{session?.username ?? "—"}</Typography.Text>
          </Typography.Text>
          <Typography.Text>
            <strong>{t("mfa.statusLabel")}:</strong>{" "}
            {status.enrolled
              ? t("mfa.statusEnrolled")
              : status.enrollmentPending
                ? t("mfa.statusPending")
                : t("mfa.statusNotEnrolled")}
          </Typography.Text>
        </Space>
      )}

      {status?.enabled && !status.enrolled && !showEnrollFlow && (
        <Button type="primary" loading={enrollMutation.isPending} onClick={() => enrollMutation.mutate()}>
          {t("mfa.startEnroll")}
        </Button>
      )}

      {status?.enabled && showEnrollFlow && (
        <div className="security-mfa-enroll" data-testid="security-mfa-enroll">
          <Typography.Paragraph type="secondary">{enrollment?.hint ?? t("mfa.pendingHint")}</Typography.Paragraph>
          <div className="security-mfa-qr-row">
            <canvas ref={qrCanvasRef} className="security-mfa-qr" aria-hidden="true" />
            <div className="security-mfa-secret">
              <Typography.Text type="secondary">{t("mfa.manualSecret")}</Typography.Text>
              <Input
                readOnly
                value={enrollment?.secret ?? ""}
                onFocus={(event) => event.target.select()}
                style={{ fontFamily: "var(--font-mono)" }}
              />
            </div>
          </div>
          <Typography.Text style={{ display: "block", marginTop: 12, marginBottom: 6 }}>
            {t("mfa.verifyCode")}
          </Typography.Text>
          <Space wrap>
            <Input
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
              placeholder="000000"
              value={verifyCode}
              onChange={(event) => setVerifyCode(event.target.value.replace(/\D/g, ""))}
              style={{ width: 120, fontFamily: "var(--font-mono)" }}
            />
            <Button
              type="primary"
              loading={verifyMutation.isPending}
              disabled={verifyCode.length !== 6}
              onClick={() => verifyMutation.mutate()}
            >
              {t("mfa.verify")}
            </Button>
            <Button loading={cancelMutation.isPending} onClick={() => cancelMutation.mutate()}>
              {t("common:action.cancel")}
            </Button>
          </Space>
        </div>
      )}
    </section>
  );
}
