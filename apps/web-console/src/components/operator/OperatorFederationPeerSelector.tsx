import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { fetchFederationPeers, type FederationPeer } from "../../api/federation";

interface OperatorFederationPeerSelectorProps {
  selectedPeerId: string | null;
  onSelectPeer: (peerId: string | null) => void;
}

function enabledPeers(peers: FederationPeer[]): FederationPeer[] {
  return peers.filter((peer) => peer.enabled);
}

export default function OperatorFederationPeerSelector({
  selectedPeerId,
  onSelectPeer,
}: OperatorFederationPeerSelectorProps) {
  const { t } = useTranslation("operator");
  const peersQuery = useQuery({
    queryKey: ["federation-peers"],
    queryFn: fetchFederationPeers,
    staleTime: 30_000,
  });

  const peers = useMemo(
    () => enabledPeers(peersQuery.data ?? []),
    [peersQuery.data]
  );

  if (peersQuery.isLoading || peers.length === 0) {
    return null;
  }

  return (
    <label className="operator-federation-peer-select" data-testid="operator-federation-peer-select">
      <span className="operator-federation-peer-label">{t("federationPeer.label")}</span>
      <select
        className="operator-federation-peer-input"
        value={selectedPeerId ?? ""}
        onChange={(event) => {
          const value = event.target.value;
          onSelectPeer(value ? value : null);
        }}
        aria-label={t("federationPeer.label")}
      >
        <option value="">{t("federationPeer.allSites")}</option>
        {peers.map((peer) => (
          <option key={peer.id} value={peer.id}>
            {peer.name}
            {peer.healthLevel ? ` (${peer.healthLevel})` : ""}
          </option>
        ))}
      </select>
    </label>
  );
}
