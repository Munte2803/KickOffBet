/** Rejection of a failed API call: the user-facing message plus the backend error code, when there is one. */
export class ApiError extends Error {
  readonly code?: string

  constructor(message: string, code?: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}
