import { useEffect, useRef } from 'react'
import type { Product } from '../lib/api/types'
import { formatPrice } from '../lib/format'
import { SafeImage } from './SafeImage'

function openDialog(dialog: HTMLDialogElement) {
  if (typeof dialog.showModal === 'function') {
    if (!dialog.open) dialog.showModal()
  } else {
    dialog.setAttribute('open', '')
  }
}

function closeDialog(dialog: HTMLDialogElement) {
  if (typeof dialog.close === 'function') {
    if (dialog.open) dialog.close()
  } else {
    dialog.removeAttribute('open')
  }
}

export function ProductDialog({
  product,
  onClose,
  example = false,
}: {
  product: Product | null
  onClose: () => void
  example?: boolean
}) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const triggerRef = useRef<Element | null>(null)

  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) return
    if (product) {
      triggerRef.current = document.activeElement
      openDialog(dialog)
    } else if (dialog.hasAttribute('open') || dialog.open) {
      closeDialog(dialog)
      const trigger = triggerRef.current
      if (trigger instanceof HTMLElement && document.contains(trigger)) trigger.focus()
      triggerRef.current = null
    }
  }, [product])

  return (
    <dialog
      ref={dialogRef}
      aria-label={product ? `${example ? 'Example details' : 'Details'} for ${product.title}` : 'Product details'}
      onCancel={(e) => {
        e.preventDefault()
        onClose()
      }}
      onClick={(e) => {
        if (e.target === dialogRef.current) onClose()
      }}
      className="m-auto w-[min(560px,calc(100vw-2rem))] rounded-sm border border-border bg-paper p-0 text-ink shadow-xl backdrop:bg-ink/40"
    >
      {product && (
        <div className="flex max-h-[calc(100vh-4rem)] flex-col overflow-y-auto sm:flex-row">
          <div className="shrink-0 bg-wash/30 sm:w-1/2">
            <div className="aspect-[4/5] w-full">
              <SafeImage
                src={product.imageUrl}
                alt={product.title}
                className="h-full w-full object-contain"
              />
            </div>
          </div>
          <div className="flex flex-1 flex-col gap-3 p-5 sm:w-1/2">
            {example && <p className="text-sm text-muted">Example product</p>}
            <h2 className="font-serif text-xl text-ink">{product.title}</h2>
            <p className="text-lg text-muted">{formatPrice(product.price)}</p>
            {product.description && (
              <p className="text-sm leading-relaxed text-ink">{product.description}</p>
            )}
            <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
              <dt className="text-muted">Category</dt>
              <dd className="text-ink">{product.category || '—'}</dd>
              <dt className="text-muted">Colors</dt>
              <dd className="text-ink">{product.colors.length ? product.colors.join(', ') : '—'}</dd>
              <dt className="text-muted">Sizes</dt>
              <dd className="text-ink">{product.sizes.length ? product.sizes.join(', ') : '—'}</dd>
              <dt className="text-muted">SKU</dt>
              <dd className="select-all text-ink">{product.sku}</dd>
            </dl>
            <div className="mt-auto pt-2">
              <button
                type="button"
                onClick={onClose}
                className="rounded-sm bg-action px-4 py-2 text-sm text-paper hover:bg-action/90 focus:outline focus:outline-2 focus:outline-offset-2 focus:outline-action"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </dialog>
  )
}
