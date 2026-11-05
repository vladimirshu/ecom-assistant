import { createContext, useContext } from 'react'
import type { AvailabilityGate } from './useAvailabilityGate'

export const AvailabilityGateContext = createContext<AvailabilityGate | null>(null)

export function useGate(): AvailabilityGate {
  const gate = useContext(AvailabilityGateContext)
  if (!gate) {
    throw new Error('useGate must be used within AvailabilityGateContext.Provider')
  }
  return gate
}
