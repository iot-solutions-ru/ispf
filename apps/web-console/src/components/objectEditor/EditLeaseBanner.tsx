import { useEffect, useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Button, Space, Typography } from "antd";
import { acquireEditLease, fetchEditLeases, releaseEditLease } from "../../api";
import { useUserTimeZone } from "../../context/UserTimeZoneContext";

function leaseCoversPath(leasePath: string, objectPath: string): boolean {
  return objectPath === leasePath || objectPath.startsWith(`${leasePath}.`);
}

interface EditLeaseBannerProps {
  path: string;
  canManage: boolean;
  username: string | undefined;
  isEditing: boolean;
}

export default function EditLeaseBanner({
  path,
  canManage,
  username,
  isEditing,
}: EditLeaseBannerProps) {
  const { t } = useTranslation("inspector");
  const { formatDate } = useUserTimeZone();
  const queryClient = useQueryClient();

  const leasesQuery = useQuery({
    queryKey: ["edit-leases"],
    queryFn: fetchEditLeases,
    enabled: canManage,
    refetchInterval: 30_000,
  });

  useEffect(() => {
    if (!canManage || !isEditing) {
      return;
    }
    let cancelled = false;
    acquireEditLease(path)
      .then(() => {
        if (!cancelled) {
          queryClient.invalidateQueries({ queryKey: ["edit-leases"] });
        }
      })
      .catch(() => {});
    return () => {
      cancelled = true;
      releaseEditLease(path).catch(() => {});
    };
  }, [canManage, isEditing, path, queryClient]);

  const releaseMutation = useMutation({
    mutationFn: () => releaseEditLease(path),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["edit-leases"] }),
  });

  const acquireMutation = useMutation({
    mutationFn: () => acquireEditLease(path),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["edit-leases"] }),
  });

  const { blockingLease, ownLease } = useMemo(() => {
    const leases = leasesQuery.data ?? [];
    const blocking = leases.find(
      (lease) =>
        username &&
        leaseCoversPath(lease.pathPrefix, path) &&
        lease.holder.toLowerCase() !== username.toLowerCase()
    );
    const own = leases.find(
      (lease) =>
        username &&
        lease.pathPrefix === path &&
        lease.holder.toLowerCase() === username.toLowerCase()
    );
    return { blockingLease: blocking, ownLease: own };
  }, [leasesQuery.data, path, username]);

  if (!canManage) {
    return null;
  }

  if (blockingLease) {
    return (
      <Alert
        className="edit-lease-banner"
        type="error"
        showIcon
        message={t("objectEditor.leaseBlocked", {
          holder: blockingLease.holder,
          until: formatDate(blockingLease.expiresAt),
          prefix: blockingLease.pathPrefix,
        })}
      />
    );
  }

  if (ownLease) {
    return (
      <Alert
        className="edit-lease-banner"
        type="success"
        showIcon
        message={
          <Space>
            <span>
              {t("objectEditor.leaseHeld", {
                until: formatDate(ownLease.expiresAt),
              })}
            </span>
            <Button size="small" loading={releaseMutation.isPending} onClick={() => releaseMutation.mutate()}>
              {t("objectEditor.leaseRelease")}
            </Button>
          </Space>
        }
      />
    );
  }

  if (!isEditing) {
    return (
      <Alert
        className="edit-lease-banner"
        type={acquireMutation.isError ? "error" : "info"}
        showIcon
        message={
          <Space wrap>
            <span>{t("objectEditor.leaseHint")}</span>
            <Button size="small" loading={acquireMutation.isPending} onClick={() => acquireMutation.mutate()}>
              {acquireMutation.isPending
                ? t("objectEditor.leaseAcquiring")
                : t("objectEditor.leaseAcquire")}
            </Button>
            {acquireMutation.isError && (
              <Typography.Text type="danger">{(acquireMutation.error as Error).message}</Typography.Text>
            )}
          </Space>
        }
      />
    );
  }

  return null;
}
