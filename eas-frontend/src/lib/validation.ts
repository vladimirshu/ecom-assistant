export const MIN_MESSAGE_LENGTH = 3
export const MAX_MESSAGE_LENGTH = 500

export type Validation =
  | { ok: true; value: string }
  | { ok: false; error: string }

/** Trim and locally validate a message before sending. The backend remains authoritative. */
export function validateMessage(raw: string): Validation {
  const value = raw.trim()
  if (value.length < MIN_MESSAGE_LENGTH) {
    return { ok: false, error: 'Please describe what you are looking for in a few more words.' }
  }
  if (value.length > MAX_MESSAGE_LENGTH) {
    return { ok: false, error: 'That description is a little long — please shorten it.' }
  }
  if (value.includes('<') || value.includes('>')) {
    return { ok: false, error: 'Please avoid the < and > characters.' }
  }
  return { ok: true, value }
}
