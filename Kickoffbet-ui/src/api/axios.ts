import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080',
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true,
})

api.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('accessToken')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Response interceptor: Handle errors and refresh token
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config

    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true

      try {
        const refreshResponse = await axios.post(
          `${import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'}/api/auth/refresh-token`,
          {},
          { withCredentials: true }
        )

        const newToken = refreshResponse.data.token

        sessionStorage.setItem('accessToken', newToken)
        originalRequest.headers.Authorization = `Bearer ${newToken}`

        return api(originalRequest)
      } catch (refreshError) {
        // Log error details for debugging
        const errorMessage = (refreshError as any)?.response?.data?.error ?? 'Token refresh failed'
        console.error('🔴 Token refresh failed:', errorMessage, refreshError)

        sessionStorage.removeItem('accessToken')
        sessionStorage.removeItem('authUser')
        window.location.href = '/login'
        return Promise.reject(new Error('Session expired. Please login again.'))
      }
    }

    const message = error.response?.data?.error ?? 'Eroare de conexiune'
    return Promise.reject(new Error(message))
  }
)

export default api


