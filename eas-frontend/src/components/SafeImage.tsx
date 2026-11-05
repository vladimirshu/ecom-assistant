import { useState } from 'react'

export const DEFAULT_IMAGE = '/images/categories/default.webp'

export function SafeImage({ src, alt, className }: { src: string | null; alt: string; className?: string }) {
  const [failedSrc, setFailedSrc] = useState<string | null>(null)
  const [fallbackFailed, setFallbackFailed] = useState(false)
  const image = src && src !== failedSrc ? src : DEFAULT_IMAGE

  if (image === DEFAULT_IMAGE && fallbackFailed) return null
  return (
    <img
      src={image}
      alt={alt}
      className={className}
      loading="lazy"
      onError={() => image === DEFAULT_IMAGE ? setFallbackFailed(true) : setFailedSrc(image)}
    />
  )
}
