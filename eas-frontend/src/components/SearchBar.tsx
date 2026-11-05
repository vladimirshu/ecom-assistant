import { useRef } from 'react'
import { SEARCH_PLACEHOLDER } from '../lib/brand'

export function SearchBar({
  value,
  onChange,
  onSubmit,
  error,
  onFocus,
  onBlur,
}: {
  value: string
  onChange: (value: string) => void
  onSubmit: (value: string) => void
  error?: string | null
  onFocus?: () => void
  onBlur?: () => void
}) {
  const inputRef = useRef<HTMLInputElement>(null)

  return (
    <form
      className="w-full"
      onSubmit={(e) => {
        e.preventDefault()
        onSubmit(value)
      }}
      noValidate
    >
      <div className="flex h-[52px] w-full overflow-hidden rounded-sm border border-border bg-wash/40 focus-within:border-action">
        <label htmlFor="search-input" className="sr-only">
          Describe the clothing you are looking for
        </label>
        <input
          id="search-input"
          ref={inputRef}
          type="text"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onFocus={onFocus}
          onBlur={onBlur}
          placeholder={SEARCH_PLACEHOLDER}
          autoComplete="off"
          aria-describedby={error ? 'search-input-hint' : undefined}
          aria-invalid={!!error}
          className="min-w-0 flex-1 bg-transparent px-4 text-[16px] text-ink outline-none placeholder:text-muted sm:text-[14px]"
        />
        <button
          type="submit"
          className="shrink-0 bg-action px-4 text-sm text-paper hover:bg-action/90 focus:outline focus:outline-2 focus:outline-offset-[-2px] focus:outline-action"
        >
          Find my match
        </button>
      </div>
      {error && (
        <p id="search-input-hint" className="mt-1 text-sm text-ink" role="status">
          {error}
        </p>
      )}
    </form>
  )
}
