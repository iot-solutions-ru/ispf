import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { fetchFederationPeers, type FederationPeer, type FederationPeerHealthLevel } from "../../api/federation";
import FederationPeerHealthBadge from "../federation/FederationPeerHealthBadge";

interface OperatorFederationPeerSelectorProps {
  selectedPeerId: string | null;
  onSelectPeer: (peerId: string | null) => void;
}

function enabledPeers(peers: FederationPeer[]): FederationPeer[] {
  return peers.filter((peer) => peer.enabled);
}

function healthIndicator(level: FederationPeerHealthLevel | undefined): string {
  if (level === "GREEN") return "●";
  if (level === "YELLOW") return "◐";
  return "○";
}

export default function OperatorFederationPeerSelector({
  selectedPeerId,
  onSelectPeer,
}: OperatorFederationPeerSelectorProps) {
  const { t } = useTranslation(["operator", "federation"]);
  const peersQuery = useQuery({
    queryKey: ["federation-peers"],
    queryFn: fetchFederationPeers,
    staleTime: 30_000,
    refetchInterval: 120_000,
  });

  const peers = useMemo(
    () => enabledPeers(peersQuery.data ?? []),
    [peersQuery.data]
  );

  const selectedPeer = useMemo(
    () => peers.find((peer) => peer.id === selectedPeerId) ?? null,
    [peers, selectedPeerId]
  );

  if (peersQuery.isLoading || peers.length === 0) {
    return null;
  }

  return (
    <div className="operator-federation-peer-select" data-testid="operator-federation-peer-select">
      <span className="operator-federation-peer-label">{t("operator:federationPeer.label")}</span>
      {selectedPeer ? (
        <FederationPeerHealthBadge
          level={selectedPeer.healthLevel ?? "YELLOW"}
          summary={selectedPeer.healthSummary}
          compact
        />
      ) : null}
      <select
        className="operator-federation-peer-input"
        value={selectedPeerId ?? ""}
        onChange={(event) => {
          const value = event.target.value;
          onSelectPeer(value ? value : null);
        }}
        aria-label={t("operator:federationPeer.label")}
      >
        <option value="">{t("operator:federationPeer.allSites")}</option>
        {peers.map((peer) => (
          <option key={peer.id} value={peer.id}>
            {`${healthIndicator(peer.healthLevel)} ${peer.name}`}
          </option>
        ))}
      </select>
    </div>
  );
}
