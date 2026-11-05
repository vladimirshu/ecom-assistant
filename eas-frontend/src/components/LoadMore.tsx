export function LoadMore({ onClick, loading = false }: { onClick: () => void; loading?: boolean }) {
  return (
    <button
      type="button"
      onClick={loading ? undefined : onClick}
      aria-disabled={loading}
      className="rounded-sm border border-action px-4 py-2 text-sm text-action hover:bg-wash/50 aria-disabled:cursor-not-allowed aria-disabled:opacity-50 focus:outline focus:outline-2 focus:outline-offset-2 focus:outline-action"
    >
      {loading ? 'Loading…' : 'Load more'}
    </button>
  )
}
