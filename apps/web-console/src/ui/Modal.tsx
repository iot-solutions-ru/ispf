import { Modal as AntdModal, type ModalProps as AntdModalProps } from "antd";
import type { ReactNode } from "react";

export interface ModalProps {
  open: boolean;
  title: string;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
  wide?: boolean;
  className?: string;
  /** Raised above elevated modals (e.g. expression editor backdrop at 20010). */
  stackLevel?: number;
  width?: AntdModalProps["width"];
}

export default function Modal({
  open,
  title,
  onClose,
  children,
  footer,
  wide = false,
  className = "",
  stackLevel = 0,
  width,
}: ModalProps) {
  const zIndex = stackLevel > 0 ? 20010 + stackLevel * 10 : undefined;

  return (
    <AntdModal
      title={title}
      open={open}
      onCancel={onClose}
      footer={footer ?? null}
      destroyOnHidden
      width={width ?? (wide ? 960 : undefined)}
      className={className}
      zIndex={zIndex}
    >
      {children}
    </AntdModal>
  );
}
