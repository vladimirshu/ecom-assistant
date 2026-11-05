export function SkeletonCard() {
  return (
    <div className="flex w-full flex-col" aria-hidden="true">
      <div className="aspect-[4/5] w-full animate-pulse rounded-sm bg-wash" />
      <div className="mt-2 h-4 w-3/4 animate-pulse rounded-sm bg-wash" />
      <div className="mt-1 h-4 w-1/3 animate-pulse rounded-sm bg-wash" />
    </div>
  )
}
