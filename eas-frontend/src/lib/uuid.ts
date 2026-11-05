/** A new UUID for the in-memory conversation id. */
export function newConversationId(): string {
  return crypto.randomUUID()
}
