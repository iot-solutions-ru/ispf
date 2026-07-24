import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Button, Modal, Space, Typography } from "antd";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  applyPlatformUpdate,
  checkPlatformUpdateNow,
  fetchPlatformUpdateStatus,
} from "../../api/platformUpdate";

const DISMISS_KEY = "ispf.ui.platformUpdate.dismissedFingerprint";

function statusFingerprint(status: {
  checkError?: string | null;
  updateAvailable?: boolean;
  latestVersion?: string | null;
  applyState?: string | null;
}): string {
  if (status.updateAvailable) {
    return `update:${status.latestVersion ?? "yes"}`;
  }
  if (status.applyState === "FAILED") {
    return `failed:${status.applyState}`;
  }
  if (status.checkError) {
    return `checkError:${status.checkError}`;
  }
  return "idle";
}

export default function PlatformUpdateBanner() {
  const { t } = useTranslation("shell");
  const queryClient = useQueryClient();
  const [dismissedFingerprint, setDismissedFingerprint] = useState<string | null>(() => {
    try {
      return sessionStorage.getItem(DISMISS_KEY);
    } catch {
      return null;
    }
  });

  const statusQuery = useQuery({
    queryKey: ["platform-update"],
    queryFn: fetchPlatformUpdateStatus,
    refetchInterval: (query) => {
      const state = query.state.data?.applyState;
      if (state === "DOWNLOADING" || state === "RESTARTING") {
        return 3000;
      }
      return 15 * 60_000;
    },
    retry: 1,
  });

  const checkMutation = useMutation({
    mutationFn: checkPlatformUpdateNow,
    onSuccess: (data) => {
      queryClient.setQueryData(["platform-update"], data);
    },
  });

  const applyMutation = useMutation({
    mutationFn: applyPlatformUpdate,
    onSuccess: (data) => {
      queryClient.setQueryData(["platform-update"], data);
    },
  });

  const status = statusQuery.data;

  function dismiss(fingerprint: string | null) {
    setDismissedFingerprint(fingerprint);
    try {
      if (fingerprint) {
        sessionStorage.setItem(DISMISS_KEY, fingerprint);
      } else {
        sessionStorage.removeItem(DISMISS_KEY);
      }
    } catch {
      // ignore storage errors
    }
  }

  useEffect(() => {
    if (!status) {
      return;
    }
    const applying = status.applyState === "DOWNLOADING" || status.applyState === "RESTARTING";
    if (applying) {
      dismiss(null);
    }
  }, [status?.applyState]);

  if (!status) {
    return null;
  }

  const applying = status.applyState === "DOWNLOADING" || status.applyState === "RESTARTING";
  const failed = status.applyState === "FAILED";
  const showUpdate = Boolean(status.updateAvailable) || applying || failed;
  const showCheckError = Boolean(status.checkError) && !showUpdate;
  const fingerprint = statusFingerprint(status);
  const dismissed = !applying && dismissedFingerprint === fingerprint;

  if ((!showUpdate && !showCheckError) || dismissed) {
    return null;
  }

  const handleApply = () => {
    const version = status.latestVersion ?? t("platformUpdate.newVersionFallback");
    Modal.confirm({
      title: t("platformUpdate.apply"),
      content: t("platformUpdate.applyConfirm", { version }),
      onOk: () => applyMutation.mutate(),
    });
  };

  const canDismiss = !applying;
  const bannerType = failed || showCheckError ? "error" : applying ? "info" : "warning";
  const message = applying
    ? t("platformUpdate.updating")
    : failed
      ? t("platformUpdate.failed")
      : status.updateAvailable
        ? t("platformUpdate.available")
        : t("platformUpdate.checkError");
  const description = applying
    ? status.applyMessage ?? t("platformUpdate.restarting")
    : failed
      ? status.applyMessage ?? t("platformUpdate.failedMessage")
      : status.updateAvailable
        ? (
            <>
              {t("platformUpdate.versionLine", {
                current: status.currentVersion,
                latest: status.latestVersion,
              })}
              {status.releaseName ? ` (${status.releaseName})` : ""}
            </>
          )
        : status.checkError;

  return (
    <Alert
      className={`platform-update-banner ${
        failed ? "platform-update-banner-error" : applying ? "platform-update-banner-busy" : ""
      }`}
      type={bannerType}
      showIcon
      message={<Typography.Text strong>{message}</Typography.Text>}
      description={description}
      action={(
        <Space className="platform-update-banner-actions" wrap size="small">
          {status.updateAvailable && status.applyEnabled && !applying && (
            <Button
              type="primary"
              size="small"
              loading={applyMutation.isPending}
              onClick={handleApply}
            >
              {t("platformUpdate.apply")}
            </Button>
          )}
          {status.updateAvailable && !status.applyEnabled && !applying && status.releaseUrl && (
            <Button size="small" href={status.releaseUrl} target="_blank" rel="noreferrer">
              {t("platformUpdate.openRelease")}
            </Button>
          )}
          {!applying && (
            <Button
              size="small"
              loading={checkMutation.isPending || statusQuery.isFetching}
              onClick={() => checkMutation.mutate()}
            >
              {t("platformUpdate.checkAgain")}
            </Button>
          )}
          {canDismiss && (
            <Button
              size="small"
              className="platform-update-banner-dismiss"
              aria-label={t("platformUpdate.dismiss")}
              title={t("platformUpdate.dismiss")}
              onClick={() => dismiss(fingerprint)}
            >
              x
            </Button>
          )}
        </Space>
      )}
    />
  );
}
