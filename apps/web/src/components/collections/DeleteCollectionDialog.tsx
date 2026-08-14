import { Trash2 } from 'lucide-react'
import { ConfirmDialog } from '@/components/ui/confirm-dialog'

type Props = {
  open: boolean
  collectionName: string
  pending?: boolean
  error?: string | null
  onConfirm: () => void
  onCancel: () => void
}

export function DeleteCollectionDialog({
  open,
  collectionName,
  pending,
  error,
  onConfirm,
  onCancel,
}: Props) {
  return (
    <ConfirmDialog
      open={open}
      variant="destructive"
      icon={<Trash2 className="h-5 w-5" />}
      title={`Delete “${collectionName}”?`}
      description="This collection, its documents, and its chats will be permanently removed."
      confirmLabel="Delete collection"
      cancelLabel="Keep collection"
      pending={pending}
      error={error}
      onConfirm={onConfirm}
      onCancel={onCancel}
    />
  )
}
