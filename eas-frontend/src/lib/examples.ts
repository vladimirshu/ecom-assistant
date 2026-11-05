export interface ExamplePrompt {
  label: string
  message: string
}

/**
 * Concise clickable examples. The first two cover warm layers and a leather
 * jacket; a non-English request is included for multilingual demonstration.
 */
export const EXAMPLE_PROMPTS: ExamplePrompt[] = [
  { label: 'Warm winter jacket', message: 'warm winter jacket' },
  { label: 'A leather jacket', message: 'a leather jacket' },
  { label: 'Куртка до 200 евро', message: 'куртка до 200 евро' },
]
