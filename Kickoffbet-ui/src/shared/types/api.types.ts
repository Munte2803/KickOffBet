export interface SortResponse {
  sorted: boolean
  unsorted: boolean
  empty: boolean
}

export interface PageResponse<T> {
  content: T[]
  totalPages: number
  totalElements: number
  number: number
  size: number
  first: boolean
  last: boolean
  sort: SortResponse
}

export interface PageRequest {
  page?: number
  size?: number
  sort?: string  
}